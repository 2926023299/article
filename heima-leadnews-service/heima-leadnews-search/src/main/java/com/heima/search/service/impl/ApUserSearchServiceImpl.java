package com.heima.search.service.impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dto.HistorySearchDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.pojos.ApUserSearch;
import com.heima.search.service.ApUserSearchService;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApUserSearchServiceImpl implements ApUserSearchService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 保存用户搜索记录
     * @param keyword 搜索关键词
     * @param UserId  用户id
     */
    @Override
    @Async
    public void insert(String keyword, Integer UserId) {
        //1.根据用户id和关键词查询搜索记录
        Query query = Query.query(Criteria.where("userId").is(UserId).and("keyword").is(keyword));
        ApUserSearch userSearch = mongoTemplate.findOne(query, ApUserSearch.class);

        if (userSearch != null) {
            //2.如果存在，更新搜索时间
            userSearch.setCreatedTime(new Date());
            mongoTemplate.save(userSearch);

            return;
        }

        //3.如果没有搜索记录，保存搜索记录
        userSearch = new ApUserSearch();
        userSearch.setUserId(UserId);
        userSearch.setKeyword(keyword);
        userSearch.setCreatedTime(new Date());

        Query query1 = Query.query(Criteria.where("userId").is(UserId));
        query1.with(Sort.by(Sort.Order.desc("createdTime")));
        List<ApUserSearch> apUserSearches = mongoTemplate.find(query1, ApUserSearch.class);

        if (apUserSearches.size() < 10) {
            mongoTemplate.save(userSearch);
        } else {
            ApUserSearch apUserSearch = apUserSearches.get(apUserSearches.size() - 1);
            mongoTemplate.findAndReplace(Query.query(Criteria.where("id").is(apUserSearch.getId())), userSearch);
        }
    }

    /**
     * 查询用户搜索记录
     * @return
     */
    @Override
    public ResponseResult findUserSearch() {

        ApUser apUser = AppThreadLocalUtil.getApUser();
        if (apUser == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        //1.根据用户id查询搜索记录
        Query query = Query.query(Criteria.where("userId").is(apUser.getId()));
        query.with(Sort.by(Sort.Order.desc("createdTime")));
        List<ApUserSearch> apUserSearches = mongoTemplate.find(query, ApUserSearch.class);



        return ResponseResult.okResult(apUserSearches);
    }

    /**
     * 删除用户搜索记录
     * @param dto
     * @return
     */
    @Override
    public ResponseResult delUserSearch(HistorySearchDto dto) {
        if(dto==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        ApUser apUser = AppThreadLocalUtil.getApUser();
        if(apUser==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        mongoTemplate.remove(Query.query(Criteria.where("id").is(dto.getId()).and("userId").is(apUser.getId())), ApUserSearch.class);

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
