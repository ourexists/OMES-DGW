package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "控制写入任务历史（按 taskId 聚合）")
@Data
public class DevicePointWriteTaskHistoryVo {

    @Schema(description = "任务标识")
    private String taskId;

    @Schema(description = "指令总数")
    private long commandCount;

    @Schema(description = "成功数")
    private long successCount;

    @Schema(description = "失败数")
    private long failedCount;

    @Schema(description = "等待中数量")
    private long pendingCount;

    @Schema(description = "执行中数量")
    private long runningCount;

    @Schema(description = "聚合状态：PENDING/RUNNING/SUCCESS/FAILED/PARTIAL")
    private String status;

    @Schema(description = "涉及设备 ID（逗号分隔）")
    private String deviceIds;

    @Schema(description = "任务创建时间（首条指令创建时间）")
    private LocalDateTime createTime;

    @Schema(description = "最近执行时间")
    private LocalDateTime lastExecutedTime;
}
