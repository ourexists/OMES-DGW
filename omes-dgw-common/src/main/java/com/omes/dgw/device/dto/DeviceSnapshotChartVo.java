package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备快照历史查询结果：公共查询条件 + {@link #charts}，每个点位一条独立折线图数据。
 */
@Schema(description = "设备快照历史（多图，每点位一图）")
@Data
public class DeviceSnapshotChartVo {

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "采集周期（秒）")
    private int intervalSec;

    @Schema(description = "返回数据点间隔（秒）；短时段为采集周期，长时段为 Influx 聚合窗口")
    private int sampleIntervalSec;

    @Schema(description = "是否在 Influx 侧按时间窗口聚合降采样")
    private boolean aggregated;

    @Schema(description = "查询开始时间")
    private LocalDateTime startTime;

    @Schema(description = "查询结束时间")
    private LocalDateTime endTime;

    @Schema(description = "各点位独立折线图列表，一项对应一张图（非同一图多曲线）")
    private List<DeviceSnapshotPointChartVo> charts = new ArrayList<>();
}
