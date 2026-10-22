package com.omes.dgw.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.omes.dgw.device.enums.PointDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个点位的独立折线图数据（各自 timestamps / values，前端一图一点位）。
 */
@Schema(description = "单点位独立折线图")
@Data
public class DeviceSnapshotPointChartVo {

    @Schema(description = "点位名称（与采集配置 name 一致）")
    private String pointName;

    @Schema(description = "折线图标题（默认：点位名，含单位时为 name(unit)）")
    private String title;

    @Schema(description = "点位编码")
    private String pointCode;

    @Schema(description = "采集地址")
    private String address;

    @Schema(description = "工程单位")
    private String unit;

    @Schema(description = "数据类型；BOOL / BOOL_INV 由前端用甘特图展示")
    private PointDataType dataType;

    @Schema(description = "本图横轴时间标签")
    private List<String> timestamps = new ArrayList<>();

    @Schema(description = "本图纵轴工程值（与 timestamps 一一对应）")
    @JsonProperty("values")
    private List<Double> valueSeries = new ArrayList<>();
}
