package com.heima.model.article.vo;

import com.heima.model.article.pojos.ApArticle;
import lombok.Data;

@Data
public class HotArticleVo extends ApArticle {
    /**
     * 文章的热度值
     */
    private Integer score;
}
