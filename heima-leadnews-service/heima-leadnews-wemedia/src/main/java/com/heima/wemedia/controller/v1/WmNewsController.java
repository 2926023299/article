package com.heima.wemedia.controller.v1;

import com.heima.api.article.IArticleClient;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.AdminNewsPageDto;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.wemedia.service.WmNewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/news")
public class WmNewsController {

    @Qualifier("com.heima.api.article.IArticleClient")
    @Autowired
    private IArticleClient articleClient;

    @Autowired
    private WmNewsService wmNewsService;

    @PostMapping("/list")
    public ResponseResult findAll(@RequestBody WmNewsPageReqDto dto) {
        return wmNewsService.findAll(dto);
    }

    /**
     * 发布修改文章或保存草稿
     * @param dto
     * @return
     */
    @PostMapping("/submit")
    public ResponseResult submitNews(@RequestBody WmNewsDto dto) {

        return wmNewsService.submitNews(dto);
    }

    /**
     * 上架下架文章
     */
    @PostMapping("down_or_up")
    public ResponseResult downOrUp(@RequestBody WmNewsDto dto) {

        return wmNewsService.downOrUp(dto);
    }

    /**
     * 在平台上查询文章列表
     */
    @PostMapping("list_vo")
    public ResponseResult findListVo(@RequestBody AdminNewsPageDto dto) {

        return wmNewsService.findListVo(dto);
    }

    /**
     * 根据id查询文章详情
     */
    @GetMapping("one_vo/{id}")
    public ResponseResult findOneVo(@PathVariable("id") Integer id) {

        return wmNewsService.findOneVo(id);
    }
}