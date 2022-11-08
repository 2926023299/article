package com.heima.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.AuthDto;
import com.heima.model.user.dtos.PageListDto;
import com.heima.model.user.pojos.ApUserRealname;

public interface ApUserRealnameService extends IService<ApUserRealname> {

    /**
     * 实名认证列表
     * @param pageListDto
     * @return
     */
    ResponseResult list(PageListDto pageListDto);

    /**
     * 实名认证失败
     * @param dto
     * @return
     */
    ResponseResult authFail(AuthDto dto);

    /**
     * 实名认证成功
     * @param dto
     * @return
     */
    ResponseResult authPass(AuthDto dto);
}