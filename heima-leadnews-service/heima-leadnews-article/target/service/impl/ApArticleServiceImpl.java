package com.heima.article.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;

    private final static short MAX_PAGE_SIZE = 50;

    /**
     * 加载文章列表
     *
     * @param dto  dto
     * @param type 1 加载更多 2 加载最新
     * @return ResponseResult
     */
    @Override
    public ResponseResult load(ArticleHomeDto dto, Short type) {

        //校验参数
        //校验分页条数
        Integer size = dto.getSize();
        if (size == null || size == 0) {
            size = 10;
        }

        //校验分页条数不能超过50
        size = Math.min(size, MAX_PAGE_SIZE);

        //校验参数 -->type
        if (type == null || type < ArticleConstants.LOADTYPE_LOAD_MORE || type > ArticleConstants.LOADTYPE_LOAD_NEW) {
            type = ArticleConstants.LOADTYPE_LOAD_MORE;
        }

        //频道参数校验
        if (StringUtils.isBlank(dto.getTag())) {
            dto.setTag(ArticleConstants.DEFAULT_TAG);
        }

        //时间校验
        if (dto.getMaxBehotTime() == null)
            dto.setMaxBehotTime(new Date());
        if (dto.getMinBehotTime() == null)
            dto.setMinBehotTime(new Date());

        List<ApArticle> list = apArticleMapper.loadArticleList(dto, type);

        return ResponseResult.okResult(list);
    }

    /**
     * 保存app端相关文章
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult saveArticle(ArticleDto dto) {

        //检查参数
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(dto, apArticle);
        //判断是否存在id
        if (dto.getId() == null) {
            //保存文章 保存文章内容 保存文章配置 文章

            save(apArticle); //保存文章

            //保存文章配置
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);

            //保存文章内容
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            if (dto.getContent() != null) {
                apArticleContent.setContent(dto.getContent());
            }
            apArticleContentMapper.insert(apArticleContent);

        } else {
            //存在则修改 文章 文章内容
            updateById(apArticle);

            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery().eq(ApArticleContent::getArticleId, apArticle.getId()));
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }

        //异步调用保存文章到MinIO
        articleFreemarkerService.buildArticleToMinIO(apArticle, dto.getContent());

        return ResponseResult.okResult(apArticle.getId());
    }
}
