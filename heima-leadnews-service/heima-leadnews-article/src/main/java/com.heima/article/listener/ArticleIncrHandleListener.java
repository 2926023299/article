package com.heima.article.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.StringUtils;
import com.heima.article.service.ApArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.constants.HotArticleConstants;
import com.heima.model.message.ArticleVisitStreamMess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ArticleIncrHandleListener {

    @Autowired
    private ApArticleService apArticleService;

    @KafkaListener(topics = HotArticleConstants.HOT_ARTICLE_INCR_HANDLE_TOPIC)
    public void onMessage(String message) {
        //TODO 1.解析消息
        if(StringUtils.isNotBlank(message)){
            log.info("接收到消息：{}", message);
            ArticleVisitStreamMess articleVisitStreamMess = JSON.parseObject(message, ArticleVisitStreamMess.class);
            apArticleService.updateScore(articleVisitStreamMess);
        }
    }
}
