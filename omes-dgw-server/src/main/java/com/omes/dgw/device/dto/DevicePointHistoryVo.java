package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "多设备点位历史值列表查询结果（按设备、按时间组合点位）")
@Data
public class DevicePointHistoryVo {

    @Schema(description = "查询开始时间")
    private LocalDateTime startTime;

    @Schema(description = "查询结束时间")
    private LocalDateTime endTime;

    @Schema(description = "是否在 Influx 侧按时间窗口聚合降采样")
    private boolean aggregated;

    @Schema(description = "实际采样/聚合时间间隔（秒）；未聚合时为设备采集周期")
    private int sampleIntervalSec;

    @Schema(description = "时间行总数（各设备 timeRows 之和）")
    private long total;

    @Schema(description = "各设备历史数据")
    private List<DevicePointHistoryDeviceVo> devices = new ArrayList<>();
}
