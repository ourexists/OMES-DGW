package com.omes.dgw.device.controller;

import com.omes.dgw.common.Result;
import com.omes.dgw.device.dto.DevicePointHistoryQueryRequest;
import com.omes.dgw.device.dto.DevicePointHistoryVo;
import com.omes.dgw.device.dto.DeviceSnapshotChartRequest;
import com.omes.dgw.device.dto.DeviceSnapshotChartVo;
import com.omes.dgw.device.service.DevicePointHistoryQueryService;
import com.omes.dgw.device.service.DeviceSnapshotQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备快照历史", description = "从 Influx 查询采集点位历史（折线图 / 列表）")
@RestController
@RequestMapping("/device/snapshot")
public class DeviceSnapshotController {

    private final DeviceSnapshotQueryService queryService;
    private final DevicePointHistoryQueryService pointHistoryQueryService;

    public DeviceSnapshotController(
            DeviceSnapshotQueryService queryService,
            DevicePointHistoryQueryService pointHistoryQueryService
    ) {
        this.queryService = queryService;
        this.pointHistoryQueryService = pointHistoryQueryService;
    }

    @Operation(
            summary = "点位快照时序（折线图）",
            description = """
                    按 startTime、endTime 指定时间段，查询设备点位工程值序列。
                    pointNames 为空时返回该设备全部配置点位。
                    响应 charts 数组：每个元素为一张独立时序（各自 timestamps、values、dataType）。
                    BOOL / BOOL_INV 由前端拆到开关量甘特图，其余点位按折线图渲染。
                    时间跨度较大时自动在 Influx 侧 aggregateWindow(last) 降采样（见 aggregated、sampleIntervalSec）。
                    """
    )
    @PostMapping("/chart")
    public Result<DeviceSnapshotChartVo> chart(@Valid @RequestBody DeviceSnapshotChartRequest request) {
        return Result.ok(queryService.chart(request));
    }

    @Operation(
            summary = "多设备点位历史值（列表）",
            description = """
                    按时间段查询历史工程值，供表格列表展示。
                    items 每项指定 deviceId；pointNames 为空时查询该设备全部配置点位。
                    响应按设备分组：pointMetas 为表头列定义，timeRows 按时间倒序（最新在前），values 为点位名称->值的 Map（同一时刻组合）。
                    传入 pointNames 时仅包含指定点位（名称或编码）。
                    时间聚合：aggregateIntervalSec 不传则按跨度自动降采样；传 0 不聚合；传正数为聚合窗口（秒）。
                    maxPointsPerSeries 仅影响自动模式。响应见 aggregated、sampleIntervalSec。
                    """
    )
    @PostMapping("/history")
    public Result<DevicePointHistoryVo> history(@Valid @RequestBody DevicePointHistoryQueryRequest request) {
        return Result.ok(pointHistoryQueryService.query(request));
    }
}
