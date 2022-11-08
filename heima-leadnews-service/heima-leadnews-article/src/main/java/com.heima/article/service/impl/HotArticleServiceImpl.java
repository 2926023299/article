package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.api.wemedia.IWemediaClient;
import com.heima.article.listener.ArticleIsDownListener;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.HotArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.vo.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class HotArticleServiceImpl implements HotArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private IWemediaClient wemediaClient;

    @Autowired
    private CacheService cacheService;

    /**
     * 计算热点文章
     */
    @Override
    public void computeHotArticle() {
        //查询前五天的文章数据
        Date dateTime = DateTime.now().minusYears(2).toDate();
        List<ApArticle> articleList = apArticleMapper.findArticleListByLast5day(dateTime);

        //计算每篇文章的热度值
        List<HotArticleVo> hotArticleVoList = computeHotArticle(articleList);

        //为每个频道缓存30条热度值最高的文章
        cacheTagToRedis(hotArticleVoList);
    }

    /**
     * 为每个频道缓存30条热度值最高的文章
     * @param hotArticleVoList
     */
    private void cacheTagToRedis(List<HotArticleVo> hotArticleVoList) {
        //每个频道缓存30条分值较高的文章
        ResponseResult responseResult = wemediaClient.getChannels();


        if(responseResult.getCode().equals(200)){
            String channelJson = JSON.toJSONString(responseResult.getData());
            List<WmChannel> wmChannels = JSON.parseArray(channelJson, WmChannel.class);
            //检索出每个频道的文章
            if(wmChannels != null && wmChannels.size() > 0){
                for (WmChannel wmChannel : wmChannels) {
                    List<HotArticleVo> hotArticleVos = hotArticleVoList.stream().filter(hotArticleVo -> hotArticleVo.getChannelId().equals(wmChannel.getId())).collect(Collectors.toList());
                    //给文章进行排序，取30条分值较高的文章存入redis  key：频道id   value：30条分值较高的文章
                    sortAndCache(hotArticleVos, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + wmChannel.getId());
                }
            }
        }

        //设置推荐数据
        //给文章进行排序，取30条分值较高的文章存入redis  key：频道id   value：30条分值较高的文章
        sortAndCache(hotArticleVoList, ArticleConstants.HOT_ARTICLE_FIRST_PAGE+ArticleConstants.DEFAULT_TAG);
    }

    //给文章进行排序，
    //取30条分值较高的文章存入redis  key：频道id   value：30条分值较高的文章
    private void sortAndCache(List<HotArticleVo> hotArticleVos, String key) {
        hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
        //取30条分值较高的文章存入redis  key：频道id   value：30条分值较高的文章
        if (hotArticleVos.size() > 30) {
            hotArticleVos = hotArticleVos.subList(0, 30);
        }
        cacheService.set(key, JSON.toJSONString(hotArticleVos));
    }

    /**
     * 计算每篇文章的热度值
     * @param articleList
     * @return
     */
    private List<HotArticleVo> computeHotArticle(List<ApArticle> articleList) {

        if (articleList == null || articleList.size() == 0) {
            return null;
        }

        List<HotArticleVo> list = new ArrayList<>();
        for (ApArticle apArticle : articleList) {
            //计算热度值
            HotArticleVo hotArticleVo = new HotArticleVo();
            BeanUtils.copyProperties(apArticle, hotArticleVo);
            Integer score = computeScore(apArticle);
            hotArticleVo.setScore(score);
            list.add(hotArticleVo);
        }

        return list;
    }

    /**
     * 分值计算
     * @param apArticle
     * @return
     */
    private Integer computeScore(ApArticle apArticle) {
        int score = 0;
        if (apArticle.getLikes() != null) {
            score += apArticle.getLikes() * ArticleConstants.HOT_ARTICLE_LIKE_WEIGHT;
        }
        if (apArticle.getComment() != null) {
            score += apArticle.getComment() * ArticleConstants.HOT_ARTICLE_COMMENT_WEIGHT;
        }
        if (apArticle.getCollection() != null) {
            score += apArticle.getCollection() * ArticleConstants.HOT_ARTICLE_COLLECTION_WEIGHT;
        }
        if (apArticle.getViews() != null) {
            score += apArticle.getViews();
        }

        return score;
    }
}
