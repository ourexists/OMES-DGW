package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "复制点位配置请求（同协议设备之间全量覆盖目标点位）")
@Data
public class DevicePointCopyRequest {

    @Schema(description = "源设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "源设备不能为空")
    private String sourceDeviceId;

    @Schema(description = "目标设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "目标设备不能为空")
    private String targetDeviceId;
}
