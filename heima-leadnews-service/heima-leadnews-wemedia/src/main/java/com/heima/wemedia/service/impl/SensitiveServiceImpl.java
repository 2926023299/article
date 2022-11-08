package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmPageListDto;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.wemedia.mapper.WmSensitiveMapper;
import com.heima.wemedia.service.SensitiveService;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class SensitiveServiceImpl extends ServiceImpl<WmSensitiveMapper, WmSensitive> implements SensitiveService {

    /**
     * 敏感词列表
     *
     * @param wmPageListDto
     * @return
     */
    @Override
    public ResponseResult list(WmPageListDto wmPageListDto) {

        if (wmPageListDto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID, "参数错误");
        }

        if (wmPageListDto.getPage() == null) {
            wmPageListDto.setPage(1);
        }
        if (wmPageListDto.getSize() == null) {
            wmPageListDto.setSize(10);
        }

        LambdaQueryWrapper<WmSensitive> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.orderByDesc(WmSensitive::getCreatedTime);

        if (wmPageListDto.getName() != null) {
            lambdaQueryWrapper.like(WmSensitive::getSensitives, wmPageListDto.getName());
        }

        IPage<WmSensitive> page = new Page<>(wmPageListDto.getPage(), wmPageListDto.getSize());

        page = this.page(page, lambdaQueryWrapper);

        ResponseResult responseResult = new PageResponseResult(wmPageListDto.getPage(), wmPageListDto.getSize(), (int) page.getTotal());
        responseResult.setData(page.getRecords());

        return responseResult;
    }

    /**
     * 保存敏感词
     *
     * @param wmSensitive
     * @return
     */
    @Override
    public ResponseResult insert(WmSensitive wmSensitive) {
        if (wmSensitive == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        wmSensitive.setCreatedTime(new Date());

        try {
            this.save(wmSensitive);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "保存失败");
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 删除敏感词
     *
     * @param id
     * @return
     */
    @Override
    public ResponseResult del(Integer id) {

        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        try {
            this.removeById(id);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "删除失败");
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 修改敏感词
     *
     * @param wmSensitive
     * @return
     */
    @Override
    public ResponseResult updateSensitive(WmSensitive wmSensitive) {
        if (wmSensitive == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        try {
            updateById(wmSensitive);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "修改失败");
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
