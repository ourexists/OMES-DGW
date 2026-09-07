package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "单设备点位历史查询项")
@Data
public class DevicePointHistoryItemRequest {

    @Schema(description = "设备主键 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "deviceId 不能为空")
    private String deviceId;

    @Schema(description = "点位名称或编码列表；为空则查询该设备全部配置点位")
    private List<String> pointNames = new ArrayList<>();
}
