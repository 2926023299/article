package com.heima.wemedia.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmPageListDto;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.wemedia.service.SensitiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sensitive")
public class SensitiveController {

    @Autowired
    private SensitiveService sensitiveService;

    /**
     * 敏感词列表
     * @param wmPageListDto
     * @return
     */
    @PostMapping("/list")
    public ResponseResult list(@RequestBody WmPageListDto wmPageListDto){

        return sensitiveService.list(wmPageListDto);
    }

    /**
     * 保存敏感词
     * @param wmSensitive
     * @return
     */
    @PostMapping("/save")
    public ResponseResult save(@RequestBody WmSensitive wmSensitive){

        return sensitiveService.insert(wmSensitive);
    }

    /**
     * 删除敏感词
     * @param id
     * @return
     */
    @DeleteMapping("/del/{id}")
    public ResponseResult del(@PathVariable("id") Integer id){

        return sensitiveService.del(id);
    }

    @PostMapping("update")
    public ResponseResult update(@RequestBody WmSensitive wmSensitive){

        return sensitiveService.updateSensitive(wmSensitive);
    }
}
