package com.omes.dgw.device.controller;

import com.omes.dgw.common.Result;
import com.omes.dgw.metrics.CollectMetricsQueryService;
import com.omes.dgw.metrics.dto.CollectMetricsChartVo;
import com.omes.dgw.metrics.dto.CollectMetricsOverviewVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "采集监控")
@RestController
@RequestMapping("/device/collect-metrics")
public class DeviceCollectMetricsController {

    private final CollectMetricsQueryService queryService;

    public DeviceCollectMetricsController(CollectMetricsQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(
            summary = "单设备采集指标时序（折线图）",
            description = "minutes 为查询最近 N 分钟；上限见 app.collect.metrics.influx-query-max-minutes。响应 rangeMinutes 为实际查询窗。"
    )
    @GetMapping("/chart")
    public Result<CollectMetricsChartVo> chart(
            @RequestParam String deviceId,
            @RequestParam(required = false, defaultValue = "30") Integer minutes
    ) {
        return Result.ok(queryService.chart(deviceId, minutes));
    }

    @Operation(summary = "全站采集指标汇总时序（折线图）")
    @GetMapping("/summary/chart")
    public Result<CollectMetricsChartVo> summaryChart(
            @RequestParam(required = false, defaultValue = "30") Integer minutes
    ) {
        return Result.ok(queryService.summaryChart(minutes));
    }

    @Operation(summary = "设备总览：汇总曲线 + 各设备最新指标")
    @GetMapping("/overview")
    public Result<CollectMetricsOverviewVo> overview(
            @RequestParam(required = false, defaultValue = "30") Integer minutes
    ) {
        return Result.ok(queryService.overview(minutes));
    }
}
