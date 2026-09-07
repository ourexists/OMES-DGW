package com.omes.dgw.monitoring.controller;

import com.omes.dgw.common.Result;
import com.omes.dgw.monitoring.dto.MonitoringOverviewVo;
import com.omes.dgw.monitoring.service.MonitoringOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统监控")
@RestController
@RequestMapping("/monitoring")
public class MonitoringOverviewController {

    private final MonitoringOverviewService monitoringOverviewService;

    public MonitoringOverviewController(MonitoringOverviewService monitoringOverviewService) {
        this.monitoringOverviewService = monitoringOverviewService;
    }

    @Operation(
            summary = "系统监控总览",
            description = "聚合设备状态、采集性能、异步队列、轮询调度与基础设施指标"
    )
    @GetMapping("/overview")
    public Result<MonitoringOverviewVo> overview(
            @RequestParam(required = false, defaultValue = "30") Integer minutes
    ) {
        return Result.ok(monitoringOverviewService.overview(minutes));
    }
}
