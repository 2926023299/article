package com.heima.article.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.dtos.ArticleInfoDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.message.ArticleVisitStreamMess;

public interface ApArticleService extends IService<ApArticle> {

    /**
     * 加载文章列表
     * @param dto
     * @param type  1 加载更多   2 加载最新
     * @return
     */
    public ResponseResult load(ArticleHomeDto dto,Short type);

    /**
     * 加载文章列表
     * @param dto
     * @param type  1 加载更多   2 加载最新
     * @Param firstPage 是否是第一页
     * @return
     */
    public ResponseResult load2(ArticleHomeDto dto,Short type, boolean firstPage);

    /**
     * 保存app端相关文章
     * @param dto
     * @return
     */
    public ResponseResult saveArticle(ArticleDto dto);

    /**
     * 加载文章详情 数据回显
     * @param dto
     * @return
     */
    public ResponseResult loadArticleBehavior(ArticleInfoDto dto);

    /**
     * 更新文章分值，同时更新文章热点数据
     * @param mess
     */
    public void updateScore(ArticleVisitStreamMess mess);
}
