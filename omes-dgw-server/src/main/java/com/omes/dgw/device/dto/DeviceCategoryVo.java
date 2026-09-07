package com.omes.dgw.device.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceCategoryVo {

    private String id;
    private String name;
    private String code;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}
