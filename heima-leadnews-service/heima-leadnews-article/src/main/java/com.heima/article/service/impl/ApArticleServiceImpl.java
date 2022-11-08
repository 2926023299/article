package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.article.service.HotArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.dtos.ArticleInfoDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.article.vo.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.message.ArticleVisitStreamMess;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.nntp.Article;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;

    private final static short MAX_PAGE_SIZE = 50;

    /**
     * 加载文章列表
     *
     * @param dto
     * @param type 1 加载更多   2 加载最新
     * @return
     */
    @Override
    public ResponseResult load(ArticleHomeDto dto, Short type) {
        //1.检验参数
        //分页条数的校验
        Integer size = dto.getSize();
        if (size == null || size == 0) {
            size = 10;
        }
        //分页的值不超过50
        size = Math.min(size, MAX_PAGE_SIZE);


        //校验参数  -->type
        if (!type.equals(ArticleConstants.LOADTYPE_LOAD_MORE) && !type.equals(ArticleConstants.LOADTYPE_LOAD_NEW)) {
            type = ArticleConstants.LOADTYPE_LOAD_MORE;
        }

        //频道参数校验
        if (StringUtils.isBlank(dto.getTag())) {
            dto.setTag(ArticleConstants.DEFAULT_TAG);
        }

        //时间校验
        if (dto.getMaxBehotTime() == null) dto.setMaxBehotTime(new Date());
        if (dto.getMinBehotTime() == null) dto.setMinBehotTime(new Date());

        //2.查询
        List<ApArticle> articleList = apArticleMapper.loadArticleList(dto, type);
        //3.结果返回
        return ResponseResult.okResult(articleList);
    }

    @Override
    public ResponseResult load2(ArticleHomeDto dto, Short type, boolean firstPage) {
        if (firstPage) {
            String str = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + dto.getTag());
            if (StringUtils.isNotBlank(str)) {
                List<HotArticleVo> hotArticleVos = JSON.parseArray(str, HotArticleVo.class);
                hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
                return ResponseResult.okResult(hotArticleVos);
            }
        }

        return load(dto, type);
    }

    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;

    /**
     * 保存app端相关文章
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult saveArticle(ArticleDto dto) {

//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        //1.检查参数
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(dto, apArticle);

        //2.判断是否存在id
        if (dto.getId() == null) {
            //2.1 不存在id  保存  文章  文章配置  文章内容

            //保存文章
            save(apArticle);

            //保存配置
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);

            //保存 文章内容
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.insert(apArticleContent);

        } else {
            //2.2 存在id   修改  文章  文章内容

            //修改  文章
            updateById(apArticle);

            //修改文章内容
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery().eq(ApArticleContent::getArticleId, dto.getId()));
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }

        //异步调用 生成静态文件上传到minio中
        articleFreemarkerService.buildArticleToMinIO(apArticle, dto.getContent());


        //3.结果返回  文章的id
        return ResponseResult.okResult(apArticle.getId());
    }

    @Autowired
    private CacheService cacheService;

    @Override
    public ResponseResult loadArticleBehavior(ArticleInfoDto dto) {

        //0.检查参数
        if (dto == null || dto.getArticleId() == null || dto.getAuthorId() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //{ "isfollow": true, "islike": true,"isunlike": false,"iscollection": true }
        boolean isfollow = false, islike = false, isunlike = false, iscollection = false;

        ApUser user = AppThreadLocalUtil.getApUser();
        if (user != null) {
            //喜欢行为
            String likeBehaviorJson = (String) cacheService.hGet(BehaviorConstants.LIKE_BEHAVIOR + dto.getArticleId().toString(), user.getId().toString());
            if (StringUtils.isNotBlank(likeBehaviorJson)) {
                islike = true;
            }
            //不喜欢的行为
            String unLikeBehaviorJson = (String) cacheService.hGet(BehaviorConstants.UN_LIKE_BEHAVIOR + dto.getArticleId().toString(), user.getId().toString());
            if (StringUtils.isNotBlank(unLikeBehaviorJson)) {
                isunlike = true;
            }
            //是否收藏
            String collctionJson = (String) cacheService.hGet(BehaviorConstants.COLLECTION_BEHAVIOR + user.getId(), dto.getArticleId().toString());
            if (StringUtils.isNotBlank(collctionJson)) {
                iscollection = true;
            }

            //是否关注
            Double score = cacheService.zScore(BehaviorConstants.APUSER_FOLLOW_RELATION + user.getId(), dto.getAuthorId().toString());
            System.out.println(score);
            if (score != null) {
                isfollow = true;
            }

        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("isfollow", isfollow);
        resultMap.put("islike", islike);
        resultMap.put("isunlike", isunlike);
        resultMap.put("iscollection", iscollection);

        return ResponseResult.okResult(resultMap);
    }


    @Autowired
    private HotArticleService hotArticleService;

    /**
     * 更新文章分值，同时更新文章热点数据
     *
     * @param mess
     */
    public void updateScore(ArticleVisitStreamMess mess) {
        //1.更新文章的阅读，评论，点赞，收藏数量
        ApArticle article = updateArticle(mess);

        //2.更新文章的分值
        Integer score = computeScore(article);
        score = score * 3;

        //3.更新文章的热点数据
        replaceDataToRedis(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + article.getChannelId(), article, score);

        //推荐热点更新
        replaceDataToRedis(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG, article, score);

    }

    /**
     * 替换数据到redis中
     * @param key
     * @param article
     * @param score
     */
    private void replaceDataToRedis(String key, ApArticle article, Integer score) {
        String articleListStr = cacheService.get(key);
        if (StringUtils.isNotBlank(articleListStr)) {
            List<HotArticleVo> hotArticleVos = JSON.parseArray(articleListStr, HotArticleVo.class);

            boolean flag = true;
            //如果有缓存，就只更新分值
            for (HotArticleVo hotArticleVo : hotArticleVos) {
                if (hotArticleVo.getId().equals(article.getId())) {
                    hotArticleVo.setScore(score);
                    flag = false;
                    break;
                }
            }

            //缓存不存在， 查询缓存中分值最小的一条数据，进行分值的比较，如果大于缓存中的分值，就替换掉
            if (flag && hotArticleVos.size() >= 30) {
                hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
                HotArticleVo lastVo = hotArticleVos.get(hotArticleVos.size() - 1);
                if (lastVo.getScore() < score) {
                    hotArticleVos.remove(lastVo);

                    HotArticleVo hotArticleVo = new HotArticleVo();
                    BeanUtils.copyProperties(article, hotArticleVo);
                    hotArticleVo.setScore(score);
                    hotArticleVos.add(hotArticleVo);
                }
            } else if (flag) {
                HotArticleVo hotArticleVo = new HotArticleVo();
                BeanUtils.copyProperties(article, hotArticleVo);
                hotArticleVo.setScore(score);
                hotArticleVos.add(hotArticleVo);
            }

            //排序
            hotArticleVos = hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
            //缓存到redis
            cacheService.set(key, JSON.toJSONString(hotArticleVos));
        }
    }

    /**
     * 更新文章的阅读，评论，点赞，收藏数量
     * @param mess
     */
    private ApArticle updateArticle(ArticleVisitStreamMess mess) {
        ApArticle article = getById(mess.getArticleId());

        article.setCollection(article.getCollection() == null ? 0 : article.getCollection() + mess.getCollect());
        article.setComment(article.getComment() == null ? 0 : article.getComment() + mess.getComment());
        article.setViews(article.getViews() == null ? 0 : article.getViews() + mess.getView());
        article.setLikes(article.getLikes() == null ? 0 : article.getLikes() + mess.getLike());

        updateById(article);

        return article;
    }

    /**
     * 计算分值
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
