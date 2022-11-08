package com.heima.model.wemedia.dtos;

import lombok.Data;

import java.util.Date;

@Data
public class AdChannelDto {

    private Integer id;
    private String name;
    private String description;
    private Integer ord;
    private Boolean status;
}
