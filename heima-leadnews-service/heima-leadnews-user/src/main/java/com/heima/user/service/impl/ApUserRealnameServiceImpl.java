package com.heima.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.AuthDto;
import com.heima.model.user.dtos.PageListDto;
import com.heima.model.user.pojos.ApUserRealname;
import com.heima.user.mapper.ApUserRealnameMapper;
import com.heima.user.service.ApUserRealnameService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@Transactional
public class ApUserRealnameServiceImpl extends ServiceImpl<ApUserRealnameMapper, ApUserRealname> implements ApUserRealnameService {

    /**
     * 实名认证列表
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult list(PageListDto dto) {
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        if(dto.getPage() == null){
            dto.setPage(1);
        }

        LambdaQueryWrapper<ApUserRealname> queryWrapper = Wrappers.lambdaQuery();
        if(dto.getStatus() != null){
            queryWrapper.eq(ApUserRealname::getStatus, dto.getStatus());
        }

        IPage<ApUserRealname> page = new Page<>(dto.getPage(), dto.getSize());
        IPage<ApUserRealname> result = page(page, queryWrapper);

        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) result.getTotal());
        responseResult.setData(result.getRecords());

        return responseResult;
    }

    /**
     * 实名认证失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult authFail(AuthDto dto) {
        if(dto == null || dto.getId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        ApUserRealname apUserRealname = getById(dto.getId());
        if(apUserRealname == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }

        apUserRealname.setStatus((short) 2);
        apUserRealname.setReason(dto.getMsg());
        apUserRealname.setUpdatedTime(new Date());
        updateById(apUserRealname);

        return ResponseResult.okResult(200, "驳回成功");
    }

    @Override
    public ResponseResult authPass(AuthDto dto) {
        if(dto == null || dto.getId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        ApUserRealname apUserRealname = getById(dto.getId());
        if(apUserRealname == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }

        apUserRealname.setStatus((short) 9);
        apUserRealname.setReason(dto.getMsg());
        apUserRealname.setUpdatedTime(new Date());
        updateById(apUserRealname);

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
