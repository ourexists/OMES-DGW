package com.omes.dgw.monitoring.dto;

import lombok.Data;

@Data
public class MonitoringQueueVo {

    private String name;
    private long depth;
    private int maxSize;
    private long trimmedTotal;
    private double usageRatio;
}
