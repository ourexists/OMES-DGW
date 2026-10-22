package com.omes.dgw.monitoring.dto;

import lombok.Data;

@Data
public class MonitoringRealtimeCacheWritesVo {

    private long success;
    private long degraded;
    private long serializationError;
    private long error;
}
