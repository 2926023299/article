package com.heima.model.wemedia.dtos;

import lombok.Data;

@Data
public class AdminNewsPageDto {
    private Integer page;
    private Integer size;
    private Integer status;
    private String title;
}
