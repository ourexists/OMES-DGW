package com.omes.dgw.monitoring.dto;

import lombok.Data;

@Data
public class MonitoringInfrastructureVo {

    private double jvmHeapUsedRatio;
    private int hikariActiveConnections;
    private int hikariMaxConnections;
    private double hikariActiveRatio;
}
