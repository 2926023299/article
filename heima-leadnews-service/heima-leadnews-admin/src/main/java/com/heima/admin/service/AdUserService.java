package com.heima.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.admin.pojos.AdUser;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.pojos.ApUser;

public interface AdUserService extends IService<AdUser> {
    /**
     * 登录
     * @param user
     * @return
     */
    public ResponseResult login(AdUser user);
}
