package com.omes.dgw.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceCategoryDto {

    /** 主键，新增可不传，修改必填 */
    private String id;

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "编号不能为空")
    private String code;
}
