package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "设备连接测试结果")
@Data
public class DeviceConnectionTestVo {

    @Schema(description = "是否连接成功")
    private boolean success;

    @Schema(description = "结果说明")
    private String message;

    @Schema(description = "连接耗时（毫秒）")
    private Long latencyMs;

    public static DeviceConnectionTestVo ok(String message, long latencyMs) {
        DeviceConnectionTestVo vo = new DeviceConnectionTestVo();
        vo.setSuccess(true);
        vo.setMessage(message);
        vo.setLatencyMs(latencyMs);
        return vo;
    }

    public static DeviceConnectionTestVo fail(String message) {
        DeviceConnectionTestVo vo = new DeviceConnectionTestVo();
        vo.setSuccess(false);
        vo.setMessage(message);
        return vo;
    }
}
