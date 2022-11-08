package com.heima.search.service.impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dto.UserSearchDto;
import com.heima.search.pojos.ApAssociateWords;
import com.heima.search.service.ApAssociateWordsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ApAssociateWordsServiceImpl implements ApAssociateWordsService {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 根据用户输入的关键字，查询联想词
     * @param dto
     * @return
     */
    @Override
    public ResponseResult search(UserSearchDto dto) {
        if (dto == null || dto.getSearchWords() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        if(dto.getPageSize() > 20){
            dto.setPageSize(20);
        }

        List<ApAssociateWords> associateWords = mongoTemplate.find(Query.query(Criteria.where("associateWords").regex(".*?\\" + dto.getSearchWords() + ".*"))
                .limit(dto.getPageSize()), ApAssociateWords.class);

        return ResponseResult.okResult(associateWords);
    }
}
