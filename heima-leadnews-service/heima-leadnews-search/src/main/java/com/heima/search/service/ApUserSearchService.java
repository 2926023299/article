package com.heima.search.service;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.search.dto.HistorySearchDto;

public interface ApUserSearchService {
    /**
     * 保存用户搜索记录
     * @param
     * @param
     * @return
     */
    public void insert(String keyword, Integer UserId);

    /**
     * 查询用户搜索记录
     * @return
     */
    public ResponseResult findUserSearch();

    /**
     * 删除用户搜索记录
     * @return
     */
    ResponseResult delUserSearch(HistorySearchDto dto);
}
