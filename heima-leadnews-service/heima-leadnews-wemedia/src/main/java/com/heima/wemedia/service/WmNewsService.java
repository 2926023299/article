package com.heima.wemedia.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.AdminNewsPageDto;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmNews;

public interface WmNewsService extends IService<WmNews> {

    /**
     * 查询文章
     * @param dto
     * @return
     */
    public ResponseResult findAll(WmNewsPageReqDto dto);

    public ResponseResult submitNews(WmNewsDto dto);

    /**
     * 上架下架文章
     * @param dto
     * @return
     */
    ResponseResult downOrUp(WmNewsDto dto);

    /**
     * 管理平台上查询文章列表
     * @param dto
     * @return
     */
    ResponseResult findListVo(AdminNewsPageDto dto);

    /**
     * 根据id查询文章详情
     * @param id
     * @return
     */
    ResponseResult findOneVo(Integer id);
}