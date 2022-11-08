package com.heima.user.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.AuthDto;
import com.heima.user.service.ApUserRealnameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthApUserRealNameController {

    @Autowired
    private ApUserRealnameService apUserRealnameService;

    /**
     * 实名认证失败
     * @param dto
     * @return
     */
    @PostMapping("/authFail")
    public ResponseResult authFail(@RequestBody AuthDto dto){
        return apUserRealnameService.authFail(dto);
    }

    /**
     * 实名认证成功
     */
    @PostMapping("/authPass")
    public ResponseResult authPass(@RequestBody AuthDto dto){
        return apUserRealnameService.authPass(dto);
    }
}
