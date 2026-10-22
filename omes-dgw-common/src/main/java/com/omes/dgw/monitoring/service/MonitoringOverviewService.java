package com.omes.dgw.monitoring.service;

import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.controller.core.DeviceLifecycleManager;
import com.omes.dgw.device.enums.DeviceConnectionStatus;
import com.omes.dgw.metrics.CollectMetricsQueryService;
import com.omes.dgw.metrics.dto.CollectMetricsDeviceSnapshotVo;
import com.omes.dgw.metrics.dto.CollectMetricsOverviewVo;
import com.omes.dgw.monitoring.dto.MonitoringAlertVo;
import com.omes.dgw.monitoring.dto.MonitoringDeviceSummaryVo;
import com.omes.dgw.monitoring.dto.MonitoringOverviewVo;
import com.omes.dgw.monitoring.dto.MonitoringPollSkipVo;
import com.omes.dgw.monitoring.dto.MonitoringQueueVo;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class MonitoringOverviewService {

    private static final double QUEUE_USAGE_WARN = 0.8;
    private static final double POLL_FAIL_RATE_WARN = 0.1;
    private static final int SKIP_STREAK_WARN = 3;

    private final CollectMetricsQueryService collectMetricsQueryService;
    private final DeviceLifecycleManager deviceLifecycleManager;
    private final MonitoringSnapshotService monitoringSnapshotService;

    public MonitoringOverviewService(
            CollectMetricsQueryService collectMetricsQueryService,
            DeviceLifecycleManager deviceLifecycleManager,
            MonitoringSnapshotService monitoringSnapshotService
    ) {
        this.collectMetricsQueryService = collectMetricsQueryService;
        this.deviceLifecycleManager = deviceLifecycleManager;
        this.monitoringSnapshotService = monitoringSnapshotService;
    }

    public MonitoringOverviewVo overview(Integer minutes) {
        MonitoringOverviewVo vo = new MonitoringOverviewVo();
        vo.setServerTime(Instant.now().toString());
        vo.setInstanceId(monitoringSnapshotService.instanceId());
        vo.setStatus("running");
        vo.setMessage("采集服务运行中");

        vo.setDevices(buildDeviceSummary());
        vo.setCollectPerformance(collectMetricsQueryService.overview(minutes));
        vo.setPipelines(monitoringSnapshotService.pipelineSnapshot());
        vo.setScheduling(monitoringSnapshotService.schedulingSnapshot());
        vo.setInfrastructure(monitoringSnapshotService.infrastructureSnapshot());
        vo.setAlerts(buildAlerts(vo));
        return vo;
    }

    private MonitoringDeviceSummaryVo buildDeviceSummary() {
        List<DeviceRealtimeObj> realtime = deviceLifecycleManager.listEnabledRealtime();
        MonitoringDeviceSummaryVo summary = new MonitoringDeviceSummaryVo();
        summary.setTotal(realtime.size());
        summary.setOnline((int) realtime.stream().filter(DeviceRealtimeObj::isOnline).count());
        summary.setOffline(summary.getTotal() - summary.getOnline());
        summary.setConnectionConnected(countByConnection(realtime, DeviceConnectionStatus.CONNECTED));
        summary.setConnectionDisconnected(countByConnection(realtime, DeviceConnectionStatus.DISCONNECTED));
        summary.setConnectionCircuitAbandoned(countByConnection(realtime, DeviceConnectionStatus.CIRCUIT_ABANDONED));
        summary.setConnectionHalfOpen(countByConnection(realtime, DeviceConnectionStatus.HALF_OPEN));
        summary.setConnectionDisabled(countByConnection(realtime, DeviceConnectionStatus.DISABLED));
        summary.setConnectionUnsupported(countByConnection(realtime, DeviceConnectionStatus.UNSUPPORTED));
        return summary;
    }

    private static int countByConnection(List<DeviceRealtimeObj> realtime, DeviceConnectionStatus status) {
        return (int) realtime.stream()
                .filter(item -> item.getConnectionStatus() == status)
                .count();
    }

    private List<MonitoringAlertVo> buildAlerts(MonitoringOverviewVo overview) {
        List<MonitoringAlertVo> alerts = new ArrayList<>();

        for (MonitoringQueueVo queue : overview.getPipelines().getQueues()) {
            if (queue.getUsageRatio() >= QUEUE_USAGE_WARN) {
                MonitoringAlertVo alert = new MonitoringAlertVo();
                alert.setCode("QUEUE_DEPTH_HIGH");
                alert.setSeverity("warning");
                alert.setTarget(queue.getName());
                alert.setMessage(String.format(
                        "队列 %s 深度 %d / %d (%.0f%%)",
                        queue.getName(),
                        queue.getDepth(),
                        queue.getMaxSize(),
                        queue.getUsageRatio() * 100
                ));
                alerts.add(alert);
            }
            if (queue.getTrimmedTotal() > 0) {
                MonitoringAlertVo alert = new MonitoringAlertVo();
                alert.setCode("QUEUE_TRIMMED");
                alert.setSeverity("warning");
                alert.setTarget(queue.getName());
                alert.setMessage(String.format("队列 %s 已累计丢弃 %d 条", queue.getName(), queue.getTrimmedTotal()));
                alerts.add(alert);
            }
        }

        CollectMetricsOverviewVo perf = overview.getCollectPerformance();
        if (perf.getDevices() != null) {
            for (CollectMetricsDeviceSnapshotVo device : perf.getDevices()) {
                if (device.getPollFailRate() >= POLL_FAIL_RATE_WARN) {
                    MonitoringAlertVo alert = new MonitoringAlertVo();
                    alert.setCode("POLL_FAIL_RATE_HIGH");
                    alert.setSeverity("warning");
                    alert.setTarget(device.getDeviceId());
                    alert.setMessage(String.format(
                            "设备 %s 轮询失败率 %.1f%%",
                            device.getDeviceName(),
                            device.getPollFailRate() * 100
                    ));
                    alerts.add(alert);
                }
            }
        }

        for (MonitoringPollSkipVo skip : overview.getScheduling().getTopSkipStreak()) {
            if (skip.getSkipStreak() >= SKIP_STREAK_WARN) {
                MonitoringAlertVo alert = new MonitoringAlertVo();
                alert.setCode("POLL_SKIP_STREAK");
                alert.setSeverity("warning");
                alert.setTarget(skip.getDeviceId());
                alert.setMessage(String.format(
                        "设备 %s 连续跳过轮询 %d 次（最近一轮耗时 %d ms）",
                        skip.getDeviceName(),
                        skip.getSkipStreak(),
                        skip.getLastDurationMs()
                ));
                alerts.add(alert);
            }
        }

        if (overview.getInfrastructure().getJvmHeapUsedRatio() >= 0.85) {
            MonitoringAlertVo alert = new MonitoringAlertVo();
            alert.setCode("JVM_HEAP_HIGH");
            alert.setSeverity("warning");
            alert.setTarget("jvm");
            alert.setMessage(String.format(
                    "JVM 堆内存使用率 %.1f%%",
                    overview.getInfrastructure().getJvmHeapUsedRatio() * 100
            ));
            alerts.add(alert);
        }

        return alerts;
    }
}
