package com.omes.dgw.monitoring.dto;

import lombok.Data;

@Data
public class MonitoringPollSkipVo {

    private String deviceId;
    private String deviceCode;
    private String deviceName;
    private String protocol;
    private long skipStreak;
    private long lastDurationMs;
}
