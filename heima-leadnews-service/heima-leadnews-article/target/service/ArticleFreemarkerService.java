package com.heima.article.service;

import com.heima.model.article.pojos.ApArticle;

public interface ArticleFreemarkerService {
    /**
     * 生成文章详情页
     * @return
     */
    public void buildArticleToMinIO(ApArticle apArticle, String content);
}
