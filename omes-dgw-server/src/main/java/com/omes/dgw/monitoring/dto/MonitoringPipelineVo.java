package com.omes.dgw.monitoring.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MonitoringPipelineVo {

    private List<MonitoringQueueVo> queues = new ArrayList<>();
    private boolean influxEnabled;
    private boolean influxMetricsPersistEnabled;
    private boolean influxMetricsWriteLeaderEnabled;
    private boolean influxMetricsWriteLeader;
    private MonitoringRealtimeCacheWritesVo realtimeCacheWrites = new MonitoringRealtimeCacheWritesVo();
}
