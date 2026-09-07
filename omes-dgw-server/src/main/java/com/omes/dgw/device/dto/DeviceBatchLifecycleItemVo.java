package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "批量启用/停用单台设备结果")
@Data
public class DeviceBatchLifecycleItemVo {

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "是否成功（含跳过）")
    private boolean success;

    @Schema(description = "是否因已是目标状态而跳过")
    private boolean skipped;

    @Schema(description = "结果说明")
    private String message;
}
