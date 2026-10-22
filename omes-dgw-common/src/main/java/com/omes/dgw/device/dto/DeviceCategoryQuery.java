package com.omes.dgw.device.dto;

import lombok.Data;

@Data
public class DeviceCategoryQuery {

    private String name;
    private String code;
    private long page = 1;
    private long size = 10;
}
