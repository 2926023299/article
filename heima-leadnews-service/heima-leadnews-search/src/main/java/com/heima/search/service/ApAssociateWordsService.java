package com.heima.search.service;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.search.dto.UserSearchDto;

public interface ApAssociateWordsService {

    /**
     * 根据关键字查询联想词
     * @param dto
     * @return
     */
    public ResponseResult search(UserSearchDto dto);
}
