package com.heima.wemedia.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.constants.WmNewsMessageConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.AdminNewsPageDto;
import com.heima.model.wemedia.vo.WmAdminNewsVo;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    @Autowired
    private WmNewsMapper wmNewsMapper;

    @Autowired
    private WmNewsTaskService wmNewsTaskService;

    @Autowired
    private WmUserMapper wmUserMapper;

    /**
     * 查询文章
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findAll(WmNewsPageReqDto dto) {

        //1.检查参数
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //分页参数检查
        dto.checkParam();
        //获取当前登录人的信息
        WmUser user = WmThreadLocalUtil.getWmUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        //2.分页条件查询
        IPage page = new Page(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<WmNews> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        //状态精确查询
        if (dto.getStatus() != null) {
            lambdaQueryWrapper.eq(WmNews::getStatus, dto.getStatus());
        }

        //频道精确查询
        if (dto.getChannelId() != null) {
            lambdaQueryWrapper.eq(WmNews::getChannelId, dto.getChannelId());
        }

        //时间范围查询
        if (dto.getBeginPubDate() != null && dto.getEndPubDate() != null) {
            lambdaQueryWrapper.between(WmNews::getPublishTime, dto.getBeginPubDate(), dto.getEndPubDate());
        }

        //关键字模糊查询
        if (StringUtils.isNotBlank(dto.getKeyword())) {
            lambdaQueryWrapper.like(WmNews::getTitle, dto.getKeyword());
        }

        //查询当前登录用户的文章
        lambdaQueryWrapper.eq(WmNews::getUserId, user.getId());

        //发布时间倒序查询
        lambdaQueryWrapper.orderByDesc(WmNews::getCreatedTime);

        page = page(page, lambdaQueryWrapper);

        //3.结果返回
        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        responseResult.setData(page.getRecords());

        return responseResult;
    }

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;

    /**
     * 发布修改文章或保存草稿
     *
     * @param dto
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResponseResult submitNews(WmNewsDto dto) {

        //TODO 1、保存或修改文章
        if (dto == null || dto.getContent() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        WmNews wmNews = new WmNews();
        //属性拷贝 dto->pojo 当属性名和类型相同时，自动拷贝，不相同时，不拷贝
        BeanUtils.copyProperties(dto, wmNews);
        //拷贝图片集合
        if (dto.getImages() != null && dto.getImages().size() > 0) {
            String images = StringUtils.join(dto.getImages(), ",");
            wmNews.setImages(images);
        }

        //如果当前封面类型为自动 -1，则设置封面为第一张图片
        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)) {
            wmNews.setType(null);
        }

        saveOrUpdateWmNews(wmNews);

        //TODO 2、判断是否草稿, 如果是草稿，直接返回
        if (dto.getStatus().equals(WmNews.Status.NORMAL.getCode())) {
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }

        //TODO 保存文章内容图片与素材之间的关系
        List<String> materials = ectractUrlInfo(dto.getContent());
        saveRelativeInfoForContent(materials, wmNews.getId());

        //保存文章封面与素材之间的关系
        saveRelativeInfoForCover(dto, wmNews, materials);

        //审核文章
        wmNewsTaskService.addNewsToTask(wmNews.getId(), wmNews.getPublishTime());
        /*if(wmNews.getId() != null){
            WmNews wmNew = wmNewsMapper.selectById(wmNews.getId());
            wmNewsAutoScanService.autoScanWmNews(wmNew);
        }else{
            throw new RuntimeException("文章id为空");
        }*/

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }


    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 上架下架文章
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult downOrUp(WmNewsDto dto) {
        //检查参数
        if (dto.getId() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //查询文章
        WmNews wmNews = wmNewsMapper.selectById(dto.getId());
        if (wmNews == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST, "文章不存在");
        }

        //判断文章是否发布
        if (!wmNews.getStatus().equals(WmNews.Status.PUBLISHED.getCode())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST, "文章未发布");
        }

        //修改文章enable
        if (dto.getEnable() != null && dto.getEnable() > -1 && dto.getEnable() < 2) {
            update(Wrappers.<WmNews>lambdaUpdate()
                    .set(WmNews::getEnable, dto.getEnable())
                    .eq(WmNews::getId, dto.getId()));

            //发送消息,通知article修改文章的配置
            if (wmNews.getArticleId() != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("articleId", wmNews.getArticleId());
                map.put("enable", dto.getEnable());
                kafkaTemplate.send(WmNewsMessageConstants.WM_NEWS_UP_OR_DOWN_TOPIC, JSON.toJSONString(map));
            }
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 在管理平台上查询文章列表
     *
     * @param dto
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult findListVo(AdminNewsPageDto dto) {
        if (dto == null) {
            return PageResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //查询文章列表
        LambdaQueryWrapper<WmNews> queryWrapper = new LambdaQueryWrapper<>();
        if (dto.getStatus() != null) {
            queryWrapper.eq(WmNews::getStatus, dto.getStatus());
        }
        if (dto.getTitle() != null) {
            queryWrapper.like(WmNews::getTitle, dto.getTitle());
        }
        queryWrapper.orderByDesc(WmNews::getCreatedTime);

        //分页查询
        IPage<WmNews> page = new Page<>(dto.getPage(), dto.getSize());

        IPage<WmNews> wmNewsIPage = wmNewsMapper.selectPage(page, queryWrapper);

        //获取作者
        List<WmNews> records = wmNewsIPage.getRecords();
        List<WmUser> wmUsers = wmUserMapper.selectBatchIds(records.stream().map(WmNews::getUserId).collect(Collectors.toList()));

        Map<Integer, String> wmUserMap = wmUsers.stream().collect(Collectors.toMap(WmUser::getId, WmUser::getNickname));

        //结果转换vo
        List<WmAdminNewsVo> wmAdminNewsVos = new ArrayList<>();

        if (wmNewsIPage.getRecords() != null && wmNewsIPage.getRecords().size() > 0) {
            wmNewsIPage.getRecords().forEach(wmNews -> {
                WmAdminNewsVo wmAdminNewsVo = new WmAdminNewsVo();
                BeanUtils.copyProperties(wmNews, wmAdminNewsVo);
                //设置作者
                if(wmUserMap.size() > 0 && wmUserMap.get(wmNews.getUserId()) != null ){
                    wmAdminNewsVo.setAuthorName(wmUserMap.get(wmNews.getUserId()));
                }

                wmAdminNewsVos.add(wmAdminNewsVo);
            });
        }

        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) wmNewsIPage.getTotal());
        responseResult.setData(wmAdminNewsVos);

        return responseResult;
    }

    @Override
    public ResponseResult findOneVo(Integer id) {

        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //查询文章
        WmNews wmNews = wmNewsMapper.selectById(id);
        if (wmNews == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST, "文章不存在");
        }

        return ResponseResult.okResult(wmNews);
    }

    /**
     * 保存文章封面与素材之间的关系
     *
     * @param dto
     * @param wmNews
     * @param materials
     */
    private void saveRelativeInfoForCover(WmNewsDto dto, WmNews wmNews, List<String> materials) {
        /*第一个功能：如果当前封面类型为自动，则设置封面类型的数据
        匹配规则
            1.如果内容图片大于等于1，小于3 单图 type1
            2.如果内容图片大于等于3， 多图 type3
            3.如果内容图片为0，无图 type0
        */
        List<String> images = dto.getImages();

        if (dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)) {
            if (materials.size() >= 1 && materials.size() < 3) {
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materials.stream().limit(1).collect(Collectors.toList());
            } else if (materials.size() >= 3) {
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            } else {
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }

            //修改文章
            if (images != null && images.size() > 0) {
                String imagesStr = StringUtils.join(images, ",");
                wmNews.setImages(imagesStr);
            }
            updateById(wmNews);

            saveOrUpdateWmNews(wmNews);
        }

        if (images != null && images.size() > 0) {
            //保存封面与素材之间的关系
            saveRelativeInfo(images, wmNews.getId(), WemediaConstants.WM_COVER_REFERENCE);
        }
    }

    /**
     * 处理文章图片与素材之间的关系
     *
     * @param materials 图片地址集合
     * @param newsId    文章id
     */
    private void saveRelativeInfoForContent(List<String> materials, Integer newsId) {
        saveRelativeInfo(materials, newsId, WemediaConstants.WM_CONTENT_REFERENCE);
    }

    //保存文章图片与素材之间的关系
    private void saveRelativeInfo(List<String> materials, Integer newsId, Short type) {

        if (materials == null || materials.isEmpty()) {
            return;
        }

        //通过图片的url查询素材的id
        List<WmMaterial> wmMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));

        //判断素材是否有效
        if (wmMaterials == null || wmMaterials.size() == 0) {
            // 手动抛出异常
            throw new CustomException(AppHttpCodeEnum.MATERIASL_REFERENCE_FAIL);
        }

        if (materials.size() != wmMaterials.size()) {
            // 手动抛出异常
            throw new CustomException(AppHttpCodeEnum.MATERIASL_REFERENCE_FAIL);
        }

        List<Integer> idList = wmMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());

        //批量保存文章与素材之间的关系
        wmNewsMaterialMapper.saveRelations(idList, newsId, type);
    }

    /**
     * 提取文章内容中的图片地址
     *
     * @param content
     * @return
     */
    private List<String> ectractUrlInfo(String content) {
        List<String> list = new ArrayList<>();

        List<Map> maps = JSON.parseArray(content, Map.class);
        maps.forEach(map -> {
            String type = (String) map.get("type");
            if (type.equals("image")) {
                String url = (String) map.get("value");
                list.add(url);
            }
        });

        return list;
    }

    /**
     * 保存或修改文章
     *
     * @param wmNews
     */
    private void saveOrUpdateWmNews(WmNews wmNews) {
        //获取当前登录人的信息
        wmNews.setUserId(WmThreadLocalUtil.getWmUser().getId());
        wmNews.setPublishTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short) 1);

        if (wmNews.getId() == null) {
            wmNews.setCreatedTime(new Date());
            //新增
            save(wmNews);
        } else {
            //修改
            //删除文章图片与素材之间的关系
            wmNewsMaterialMapper.delete(new LambdaQueryWrapper<WmNewsMaterial>().eq(WmNewsMaterial::getNewsId, wmNews.getId()));

            updateById(wmNews);
        }
    }

}