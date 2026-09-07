package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "复制设备请求（含点位配置，协议与源设备一致）")
@Data
public class DeviceCopyRequest {

    @Schema(description = "源设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "源设备不能为空")
    private String sourceDeviceId;

    @Schema(description = "新设备基础信息（协议以源设备为准，强制停用）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "新设备信息不能为空")
    @Valid
    private DeviceDto device;
}
