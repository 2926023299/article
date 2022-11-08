package com.heima.user.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.PageListDto;
import com.heima.model.user.pojos.ApUserRealname;
import com.heima.user.service.ApUserRealnameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class ApUserRealNameController {

    @Autowired
    private ApUserRealnameService apUserRealnameService;

    /**
     * 实名认证
     * @param
     * @return
     */
    @RequestMapping("/list")
    public ResponseResult realname(@RequestBody PageListDto pageListDto){
        return apUserRealnameService.list(pageListDto);
    }
}
