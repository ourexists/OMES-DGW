package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "设备快照历史折线图查询条件")
@Data
public class DeviceSnapshotChartRequest {

    @Schema(description = "设备主键 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "deviceId 不能为空")
    private String deviceId;

    @Schema(description = "点位名称或编码列表；为空则查询该设备全部配置点位")
    private List<String> pointNames = new ArrayList<>();

    @Schema(description = "开始时间", example = "2026-05-19 08:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @Schema(description = "结束时间", example = "2026-05-19 10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
}
