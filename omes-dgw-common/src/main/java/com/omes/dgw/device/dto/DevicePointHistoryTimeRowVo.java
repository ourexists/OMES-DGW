package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "同一采集时刻下各点位取值（key 为点位名称）")
@Data
public class DevicePointHistoryTimeRowVo {

    @Schema(description = "采样时间", example = "2026-05-19 08:00:00")
    private String time;

    @Schema(description = "点位名称 -> 工程值；该时刻无数据的点位为 null")
    private Map<String, Double> values = new LinkedHashMap<>();
}
