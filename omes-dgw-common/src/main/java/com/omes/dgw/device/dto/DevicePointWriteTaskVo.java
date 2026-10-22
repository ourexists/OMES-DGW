package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "控制点位写入任务（虚拟任务，由调用方 taskId 标识）")
@Data
public class DevicePointWriteTaskVo {

    @Schema(description = "调用方任务标识（可跨设备，设备信息见各指令）")
    private String taskId;

    private List<DevicePointWriteCommandVo> commands = new ArrayList<>();
}
