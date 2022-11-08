package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.AdChannelDto;
import com.heima.model.user.dtos.PageListDto;
import com.heima.model.wemedia.dtos.WmPageListDto;
import com.heima.model.wemedia.pojos.WmChannel;

public interface WmChannelService extends IService<WmChannel> {

    /**
     * 查询所有频道
     * @return
     */
    public ResponseResult findAll();

    /**
     * 查询频道列表
     * @param dto
     * @return
     */
    public ResponseResult findAll(WmPageListDto dto);

    /**
     * 保存频道
     * @param dto
     * @return
     */
    ResponseResult insertChannel(AdChannelDto dto);

    ResponseResult updateChannel(AdChannelDto dto);

    ResponseResult delChannel(Integer id);
}