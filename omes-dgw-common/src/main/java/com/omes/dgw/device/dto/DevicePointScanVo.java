package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "设备点位一次性扫描结果（类似 ModScan 全表读取）")
public class DevicePointScanVo {

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "是否读取成功（至少一个点位有值）")
    private boolean success;

    @Schema(description = "是否在线（与实时缓存一致）")
    private boolean online;

    @Schema(description = "状态说明或异常信息")
    private String message;

    @Schema(description = "本次读取耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "读取完成时间")
    private LocalDateTime readTime;

    @Schema(description = "点位列表（按配置顺序）")
    private List<DevicePointScanItemVo> points = new ArrayList<>();
}
