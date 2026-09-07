package com.omes.dgw.monitoring.dto;

import com.omes.dgw.metrics.dto.CollectMetricsOverviewVo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MonitoringOverviewVo {

    private String serverTime;
    private String instanceId;
    private String status;
    private String message;

    private MonitoringDeviceSummaryVo devices = new MonitoringDeviceSummaryVo();
    private CollectMetricsOverviewVo collectPerformance = new CollectMetricsOverviewVo();
    private MonitoringPipelineVo pipelines = new MonitoringPipelineVo();
    private MonitoringSchedulingVo scheduling = new MonitoringSchedulingVo();
    private MonitoringInfrastructureVo infrastructure = new MonitoringInfrastructureVo();
    private List<MonitoringAlertVo> alerts = new ArrayList<>();
}
