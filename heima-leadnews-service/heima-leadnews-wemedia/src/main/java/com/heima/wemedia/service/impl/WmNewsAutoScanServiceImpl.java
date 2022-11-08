package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.api.article.IArticleClient;
import com.heima.common.Tess4j.Tess4jClient;
import com.heima.common.aliyun.GreenImageScan;
import com.heima.common.aliyun.GreenTextScan;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.common.SensitiveWordUtil;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmSensitiveMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsAutoScanServiceImpl implements WmNewsAutoScanService {

    @Autowired
    private WmNewsMapper wmNewsMapper;

    @Autowired
    private GreenTextScan greenTextScan;

    @Autowired
    private GreenImageScan greenImageScan;

    @Autowired
    private FileStorageService fileStorageService;


    @Resource
    private IArticleClient articleClient;

    @Autowired
    private WmChannelMapper wmChannelMapper;

    @Autowired
    private WmUserMapper wmUserMapper;

    @Autowired
    private WmSensitiveMapper wmSensitiveMapper;

    @Autowired
    private Tess4jClient tess4jClient;

    /**
     * 自媒体文章审核
     *
     * @param wmNews
     */
    @Override
    @Async //异步执行
    public void autoScanWmNews(WmNews wmNews) {
        //查询自媒体文章
        wmNews = wmNewsMapper.selectById(wmNews.getId());
        log.info("对象{}", wmNews);
        if (wmNews == null) {
            throw new RuntimeException("mNewsAutoScanServiceImpl-自媒体文章不存在");
        }

        //判断文章是否已经审核
        if (wmNews.getStatus().equals(WmNews.Status.SUBMIT.getCode())) {
            Map<String, Object> map = handleTextAndImage(wmNews);

            //自管理的敏感词过滤
            boolean isSensitive = handleSensitiveScan((String) map.get("text"), wmNews);
            if (!isSensitive) return;

            //审核文本内容
            boolean isPassText = handleTextScan((String) map.get("text"), wmNews);
            if (!isPassText) {
                return;
            }

            //审核图篇内容
            boolean isPassImage = handleImageScan((List<String>) map.get("images"), wmNews);
            if (!isPassImage) {
                return;
            }
        }

        //审核成功保存app端的相关文章数据
        ResponseResult responseResult = saveAppArticle(wmNews);
        if (!responseResult.getCode().equals(200)) {
            throw new RuntimeException("mNewsAutoScanServiceImpl-文章审核保存app端数据失败");
        }

        //回填id
        wmNews.setArticleId((Long) responseResult.getData());
        updateWmNews(wmNews, (short) 9, "审核成功");
    }

    //自管理的敏感词过滤
    private boolean handleSensitiveScan(String content, WmNews wmNews) {
        boolean flag = true;

        //获取所有的敏感词
        List<WmSensitive> wmSensitives = wmSensitiveMapper.selectList(Wrappers.<WmSensitive>lambdaQuery().select(WmSensitive::getSensitives));
        List<String> sensitiveList = wmSensitives.stream().map(WmSensitive::getSensitives).collect(Collectors.toList());

        //初始化敏感词库
        SensitiveWordUtil.initMap(sensitiveList);

        //查看文章中是否包含敏感词
        Map<String, Integer> map = SensitiveWordUtil.matchWords(content);
        if (map.size() > 0) {
            updateWmNews(wmNews, (short) 2, "当前文章中存在违规内容" + map);
            flag = false;
        }

        return flag;
    }

    /**
     * 保存app端的相关文章数据
     * @param wmNews
     */
    private ResponseResult saveAppArticle(WmNews wmNews) {
        ArticleDto articleDto = new ArticleDto();

        //属性赋值
        BeanUtils.copyProperties(wmNews, articleDto);
        articleDto.setPublishTime(new Date());
        articleDto.setCreatedTime(new Date());
        articleDto.setLayout(wmNews.getType());

        //设置频道
        WmChannel wmChannel = wmChannelMapper.selectById(wmNews.getChannelId());
        if (wmChannel != null) {
            articleDto.setChannelName(wmChannel.getName());
        }

        //设置作者
        articleDto.setAuthorId(wmNews.getUserId().longValue());
        WmUser wmUser = wmUserMapper.selectById(wmNews.getUserId());
        if (wmUser != null) {
            articleDto.setAuthorName(wmUser.getName());
        }

        //设置文章id
        if (wmNews.getArticleId() != null) {
            articleDto.setId(wmNews.getArticleId());
        }

        return articleClient.saveArticle(articleDto);
    }

    //审核图篇内容
    private boolean handleImageScan(List<String> images, WmNews wmNews) {

        if (images == null || images.size() == 0) {
            return true;
        }

        images = images.stream().distinct().collect(Collectors.toList());

        //下载图片 minIO
        //图片去重
        List<byte[]> imagesList = new ArrayList<>();
        try {
            for (String image : images) {
                byte[] bytes = fileStorageService.downLoadFile(image);

                //图片识别
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                BufferedImage bufferedImage = ImageIO.read(byteArrayInputStream);
                String result = tess4jClient.doOCR(bufferedImage);
                //判断是否包含敏感词
                boolean flag = handleTextScan(result, wmNews);
                if (!flag) {
                    updateWmNews(wmNews, (short) 2, "图篇内容审核不通过");
                    return false;
                }

                imagesList.add(bytes);
            }
        } catch (TesseractException | IOException e) {
            e.printStackTrace();
        }

        //审核
        try {
            Map map = greenImageScan.imageScan(imagesList);
            if (map == null) {
                log.error("WmNewsAutoScanServiceImpl-图篇内容审核失败");
                return false;
            }
            if (map.get("suggestion").equals("block")) {
                //审核不通过
                updateWmNews(wmNews, (short) 2, "图篇内容审核不通过");
                return false;
            }
            if (map.get("suggestion").equals("review")) {
                //审核不通过
                updateWmNews(wmNews, (short) 3, "需要人工审核");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    //审核纯文本内容
    private boolean handleTextScan(String text, WmNews wmNews) {
        if (StringUtils.isNotBlank(text) && StringUtils.isNotBlank(wmNews.getTitle())) {
            return true;
        }

        if (StringUtils.isNotBlank(wmNews.getTitle())) {
            text += wmNews.getTitle();
        }

        //调用文本审核接口
        try {
            Map map = greenTextScan.greeTextScan(text);
            if (map != null) {
                if (map.get("suggestion").equals("block")) {
                    //审核不通过
                    updateWmNews(wmNews, (short) 2, "文章中存在违规");

                    return false;
                } else if (map.get("suggestion").equals("review")) {
                    //需要人工审核
                    updateWmNews(wmNews, (short) 3, "需要人工审核");

                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * 修改文章
     *
     * @param wmNews
     */
    private void updateWmNews(WmNews wmNews, Short status, String reason) {
        wmNews.setStatus(status);
        wmNews.setReason(reason);
        wmNewsMapper.updateById(wmNews);
    }

    /**
     * 从文章中提取文本和图片
     *
     * @param wmNews
     * @return
     */
    private Map<String, Object> handleTextAndImage(WmNews wmNews) {

        StringBuilder sb = new StringBuilder();
        List<String> images = new ArrayList<>();

        if (StringUtils.isNotBlank(wmNews.getContent())) {
            List<Map> maps = JSON.parseArray(wmNews.getContent(), Map.class);

            for (Map map : maps) {
                if (map.get("type").equals("text")) {
                    //文本内容
                    String text = (String) map.get("value");
                    sb.append(text);
                }

                if (map.get("type").equals("image")) {
                    //图篇内容
                    String url = (String) map.get("value");
                    images.add(url);
                }
            }
        }

        //提取封面
        if (StringUtils.isNotBlank(wmNews.getImages())) {
            String[] split = wmNews.getImages().split(",");
            images.addAll(Arrays.asList(split));
        }

        Map<String, Object> map = new HashMap<>();
        map.put("text", sb.toString());
        map.put("images", images);

        return map;
    }
}
