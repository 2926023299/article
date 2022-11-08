package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.AdChannelDto;
import com.heima.model.user.dtos.PageListDto;
import com.heima.model.wemedia.dtos.WmPageListDto;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.service.WmChannelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@Transactional
@Slf4j
public class WmChannelServiceImpl extends ServiceImpl<WmChannelMapper, WmChannel> implements WmChannelService {


    /**
     * 查询所有频道
     *
     * @return
     */
    @Override
    public ResponseResult findAll() {

        return ResponseResult.okResult(list());
    }

    @Override
    public ResponseResult findAll(WmPageListDto dto) {
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }


        LambdaQueryWrapper<WmChannel> queryWrapper = Wrappers.lambdaQuery();
        if (dto.getName() != null) {
            queryWrapper.like(WmChannel::getName, dto.getName());
        }

        IPage<WmChannel> page = new Page<>(dto.getPage(), dto.getSize());

        queryWrapper.orderByDesc(WmChannel::getCreatedTime);

        IPage result = page(page, queryWrapper);

        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) result.getTotal());
        responseResult.setData(result.getRecords());

        return responseResult;
    }

    @Override
    public ResponseResult insertChannel(AdChannelDto dto) {
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        try {
            WmChannel wmChannel = new WmChannel();
            wmChannel.setName(dto.getName());
            wmChannel.setStatus(dto.getStatus());
            wmChannel.setIsDefault(false);
            wmChannel.setOrd(dto.getOrd());
            wmChannel.setCreatedTime(new Date());
            wmChannel.setDescription(dto.getDescription());
            save(wmChannel);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "频道保存失败");
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult updateChannel(AdChannelDto dto) {
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        try {
            WmChannel wmChannel = new WmChannel();
            wmChannel.setId(dto.getId());
            wmChannel.setName(dto.getName());
            wmChannel.setStatus(dto.getStatus());
            wmChannel.setOrd(dto.getOrd());
            wmChannel.setDescription(dto.getDescription());
            updateById(wmChannel);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "频道更新失败");
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult delChannel(Integer id) {

        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        WmChannel wmChannel = getById(id);
        if(!wmChannel.getStatus()){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"频道已经启用，不能删除");
        }

        try {
            removeById(id);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "频道删除失败");
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
