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
     * ?????????????????????
     *
     * @param wmNews
     */
    @Override
    @Async //????????????
    public void autoScanWmNews(WmNews wmNews) {
        //?????????????????????
        wmNews = wmNewsMapper.selectById(wmNews.getId());
        log.info("??????{}", wmNews);
        if (wmNews == null) {
            throw new RuntimeException("mNewsAutoScanServiceImpl-????????????????????????");
        }

        //??????????????????????????????
        if (wmNews.getStatus().equals(WmNews.Status.SUBMIT.getCode())) {
            Map<String, Object> map = handleTextAndImage(wmNews);

            //???????????????????????????
            boolean isSensitive = handleSensitiveScan((String) map.get("text"), wmNews);
            if (!isSensitive) return;

            //??????????????????
            boolean isPassText = handleTextScan((String) map.get("text"), wmNews);
            if (!isPassText) {
                return;
            }

            //??????????????????
            boolean isPassImage = handleImageScan((List<String>) map.get("images"), wmNews);
            if (!isPassImage) {
                return;
            }
        }

        //??????????????????app????????????????????????
        ResponseResult responseResult = saveAppArticle(wmNews);
        if (!responseResult.getCode().equals(200)) {
            throw new RuntimeException("mNewsAutoScanServiceImpl-??????????????????app???????????????");
        }

        //??????id
        wmNews.setArticleId((Long) responseResult.getData());
        updateWmNews(wmNews, (short) 9, "????????????");
    }

    //???????????????????????????
    private boolean handleSensitiveScan(String content, WmNews wmNews) {
        boolean flag = true;

        //????????????????????????
        List<WmSensitive> wmSensitives = wmSensitiveMapper.selectList(Wrappers.<WmSensitive>lambdaQuery().select(WmSensitive::getSensitives));
        List<String> sensitiveList = wmSensitives.stream().map(WmSensitive::getSensitives).collect(Collectors.toList());

        //?????????????????????
        SensitiveWordUtil.initMap(sensitiveList);

        //????????????????????????????????????
        Map<String, Integer> map = SensitiveWordUtil.matchWords(content);
        if (map.size() > 0) {
            updateWmNews(wmNews, (short) 2, "?????????????????????????????????" + map);
            flag = false;
        }

        return flag;
    }

    /**
     * ??????app????????????????????????
     * @param wmNews
     */
    private ResponseResult saveAppArticle(WmNews wmNews) {
        ArticleDto articleDto = new ArticleDto();

        //????????????
        BeanUtils.copyProperties(wmNews, articleDto);
        articleDto.setPublishTime(new Date());
        articleDto.setCreatedTime(new Date());
        articleDto.setLayout(wmNews.getType());

        //????????????
        WmChannel wmChannel = wmChannelMapper.selectById(wmNews.getChannelId());
        if (wmChannel != null) {
            articleDto.setChannelName(wmChannel.getName());
        }

        //????????????
        articleDto.setAuthorId(wmNews.getUserId().longValue());
        WmUser wmUser = wmUserMapper.selectById(wmNews.getUserId());
        if (wmUser != null) {
            articleDto.setAuthorName(wmUser.getName());
        }

        //????????????id
        if (wmNews.getArticleId() != null) {
            articleDto.setId(wmNews.getArticleId());
        }

        return articleClient.saveArticle(articleDto);
    }

    //??????????????????
    private boolean handleImageScan(List<String> images, WmNews wmNews) {

        if (images == null || images.size() == 0) {
            return true;
        }

        images = images.stream().distinct().collect(Collectors.toList());

        //???????????? minIO
        //????????????
        List<byte[]> imagesList = new ArrayList<>();
        try {
            for (String image : images) {
                byte[] bytes = fileStorageService.downLoadFile(image);

                //????????????
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                BufferedImage bufferedImage = ImageIO.read(byteArrayInputStream);
                String result = tess4jClient.doOCR(bufferedImage);
                //???????????????????????????
                boolean flag = handleTextScan(result, wmNews);
                if (!flag) {
                    updateWmNews(wmNews, (short) 2, "???????????????????????????");
                    return false;
                }

                imagesList.add(bytes);
            }
        } catch (TesseractException | IOException e) {
            e.printStackTrace();
        }

        //??????
        try {
            Map map = greenImageScan.imageScan(imagesList);
            if (map == null) {
                log.error("WmNewsAutoScanServiceImpl-????????????????????????");
                return false;
            }
            if (map.get("suggestion").equals("block")) {
                //???????????????
                updateWmNews(wmNews, (short) 2, "???????????????????????????");
                return false;
            }
            if (map.get("suggestion").equals("review")) {
                //???????????????
                updateWmNews(wmNews, (short) 3, "??????????????????");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    //?????????????????????
    private boolean handleTextScan(String text, WmNews wmNews) {
        if (StringUtils.isNotBlank(text) && StringUtils.isNotBlank(wmNews.getTitle())) {
            return true;
        }

        if (StringUtils.isNotBlank(wmNews.getTitle())) {
            text += wmNews.getTitle();
        }

        //????????????????????????
        try {
            Map map = greenTextScan.greeTextScan(text);
            if (map != null) {
                if (map.get("suggestion").equals("block")) {
                    //???????????????
                    updateWmNews(wmNews, (short) 2, "?????????????????????");

                    return false;
                } else if (map.get("suggestion").equals("review")) {
                    //??????????????????
                    updateWmNews(wmNews, (short) 3, "??????????????????");

                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * ????????????
     *
     * @param wmNews
     */
    private void updateWmNews(WmNews wmNews, Short status, String reason) {
        wmNews.setStatus(status);
        wmNews.setReason(reason);
        wmNewsMapper.updateById(wmNews);
    }

    /**
     * ?????????????????????????????????
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
                    //????????????
                    String text = (String) map.get("value");
                    sb.append(text);
                }

                if (map.get("type").equals("image")) {
                    //????????????
                    String url = (String) map.get("value");
                    images.add(url);
                }
            }
        }

        //????????????
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
