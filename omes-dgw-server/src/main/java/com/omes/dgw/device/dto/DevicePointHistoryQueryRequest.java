package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "多设备点位历史值查询条件")
@Data
public class DevicePointHistoryQueryRequest {

    @Schema(description = "查询项：每项指定一个设备及待查点位", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少指定一个设备点位查询项")
    @Valid
    private List<DevicePointHistoryItemRequest> items = new ArrayList<>();

    @Schema(description = "开始时间", example = "2026-05-19 08:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @Schema(description = "结束时间", example = "2026-05-19 10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @Schema(description = """
            时间聚合窗口（秒）。不传则按时间跨度自动降采样；
            传 0 表示不聚合（原始采样）；传正整数则在 Influx 侧 aggregateWindow(last) 按该窗口聚合（不小于设备采集周期）。
            """)
    private Integer aggregateIntervalSec;

    @Schema(description = "自动聚合时单序列最大返回点数；不传则使用配置 app.collect.history.chart-max-points-per-series")
    private Integer maxPointsPerSeries;
}
