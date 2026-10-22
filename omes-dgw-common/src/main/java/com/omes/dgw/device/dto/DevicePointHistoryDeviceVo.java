package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "单设备历史列表（按时间组合各点位）")
@Data
public class DevicePointHistoryDeviceVo {

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "本结果包含的点位列定义（与 values 的 key 一致；未传 pointNames 时为设备全部配置点位）")
    private List<DevicePointHistoryPointMetaVo> pointMetas = new ArrayList<>();

    @Schema(description = "按时间升序；每行同一时刻下所有点位取值")
    private List<DevicePointHistoryTimeRowVo> timeRows = new ArrayList<>();
}
