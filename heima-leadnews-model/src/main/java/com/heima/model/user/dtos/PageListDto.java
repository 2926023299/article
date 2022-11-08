package com.heima.model.user.dtos;

import lombok.Data;

@Data
public class PageListDto {
        private Integer status;
        private Integer page;
        private Integer size;
}
