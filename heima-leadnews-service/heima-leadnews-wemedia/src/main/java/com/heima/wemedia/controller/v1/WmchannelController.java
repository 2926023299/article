package com.heima.wemedia.controller.v1;

import com.heima.model.wemedia.dtos.AdChannelDto;
import com.heima.model.user.dtos.PageListDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmPageListDto;
import com.heima.wemedia.service.WmChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/channel")
public class WmchannelController {

    @Autowired
    private WmChannelService wmChannelService;

    @GetMapping("/channels")
    public ResponseResult findAll(){
        return wmChannelService.findAll();
    }

    @PostMapping("/list")
    public ResponseResult list(@RequestBody WmPageListDto dto){
        return wmChannelService.findAll(dto);
    }

    @PostMapping("/save")
    public ResponseResult save(@RequestBody AdChannelDto dto){
        return wmChannelService.insertChannel(dto);
    }

    @PostMapping("/update")
    public ResponseResult update(@RequestBody AdChannelDto dto){
        return wmChannelService.updateChannel(dto);
    }

    @GetMapping("/del/{id}")
    public ResponseResult del(@PathVariable("id") Integer id){
        return wmChannelService.delChannel(id);
    }
}
