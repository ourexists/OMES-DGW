package com.omes.dgw.device.cache;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "点位实时值")
@Data
public class DevicePointRealtimeObj {

    @Schema(description = "点位编码")
    private String code;

    @Schema(description = "点位名称")
    private String name;

    @Schema(description = "采集地址")
    private String address;

    @Schema(description = "原始值")
    private Object rawValue;

    @Schema(description = "工程值（缩放/换算后）")
    private Object value;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "工程单位")
    private String unit;

    @Schema(description = "采集错误信息")
    private String error;
}
