package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "设备协议连接操作结果")
@Data
public class DeviceConnectionVo {

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "状态说明")
    private String message;

    @Schema(description = "耗时（毫秒）")
    private Long latencyMs;

    public static DeviceConnectionVo ok(String deviceId, String message, long latencyMs) {
        DeviceConnectionVo vo = new DeviceConnectionVo();
        vo.setDeviceId(deviceId);
        vo.setSuccess(true);
        vo.setMessage(message);
        vo.setLatencyMs(latencyMs);
        return vo;
    }

    public static DeviceConnectionVo fail(String deviceId, String message) {
        DeviceConnectionVo vo = new DeviceConnectionVo();
        vo.setDeviceId(deviceId);
        vo.setSuccess(false);
        vo.setMessage(message);
        return vo;
    }
}
