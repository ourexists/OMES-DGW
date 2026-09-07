package com.omes.dgw.device.dto;

import com.omes.dgw.device.model.DevicePointConfigItem;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DevicePointDto {

    /** 所属设备 ID（单条新增/修改必填；批量保存时可省略，以 batch 请求中的 deviceId 为准） */
    private String deviceId;

    @NotEmpty
    private List<DevicePointConfigItem> items;
}
