package com.omes.dgw.metrics.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备总览页：全站汇总曲线 + 各设备最新指标。
 */
@Data
public class CollectMetricsOverviewVo {

    private CollectMetricsChartVo summary = new CollectMetricsChartVo();
    private List<CollectMetricsDeviceSnapshotVo> devices = new ArrayList<>();
}
