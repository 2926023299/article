package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmPageListDto;
import com.heima.model.wemedia.pojos.WmSensitive;

public interface SensitiveService extends IService<WmSensitive> {
    /**
     * 敏感词列表
     * @param wmPageListDto
     * @return
     */
    public ResponseResult list(WmPageListDto wmPageListDto);

    /**
     * 保存敏感词
     * @param wmSensitive
     * @return
     */
    ResponseResult insert(WmSensitive wmSensitive);

    /**
     * 删除敏感词
     * @param id
     * @return
     */
    ResponseResult del(Integer id);

    /**
     * 修改敏感词
     * @param wmSensitive
     * @return
     */
    ResponseResult updateSensitive(WmSensitive wmSensitive);

}
