package com.omes.dgw.monitoring.dto;

import lombok.Data;

@Data
public class MonitoringDeviceSummaryVo {

    private int total;
    private int online;
    private int offline;

    private int connectionConnected;
    private int connectionDisconnected;
    private int connectionCircuitAbandoned;
    private int connectionHalfOpen;
    private int connectionDisabled;
    private int connectionUnsupported;
}
