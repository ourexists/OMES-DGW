package com.omes.dgw.device.dto;

import com.omes.dgw.device.enums.WriteCommandStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "控制写入指令历史查询条件")
@Data
public class DevicePointWriteCommandQuery {

    @Schema(description = "任务标识（精确匹配）")
    private String taskId;

    @Schema(description = "设备 ID（精确匹配）")
    private String deviceId;

    @Schema(description = "执行状态（指令级筛选）")
    private WriteCommandStatus status;

    @Schema(description = "任务聚合状态筛选：PENDING/RUNNING/SUCCESS/FAILED/PARTIAL")
    private String taskStatus;

    @Schema(description = "写入地址（模糊匹配）")
    private String address;

    @Schema(description = "创建时间起（含）")
    private LocalDateTime createTimeFrom;

    @Schema(description = "创建时间止（含）")
    private LocalDateTime createTimeTo;

    @Schema(description = "执行完成时间起（含）")
    private LocalDateTime executedTimeFrom;

    @Schema(description = "执行完成时间止（含）")
    private LocalDateTime executedTimeTo;

    @Schema(description = "页码，从 1 开始", example = "1")
    private long page = 1;

    @Schema(description = "每页条数", example = "20")
    private long size = 20;
}
