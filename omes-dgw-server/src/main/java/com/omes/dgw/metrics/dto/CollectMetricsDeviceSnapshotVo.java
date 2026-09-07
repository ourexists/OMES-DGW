package com.omes.dgw.metrics.dto;

import lombok.Data;

/**
 * 单设备最新采样快照（表格/卡片）。
 */
@Data
public class CollectMetricsDeviceSnapshotVo {

    private String deviceId;
    private String deviceCode;
    private String deviceName;
    private String protocol;
    private String sampleTime;

    private double readQps;
    private double pollQps;
    private double writeQps;
    private double readFailRate;
    private double pollFailRate;
    private double writeFailRate;
    private double readLatencyMs;
    private double pollLatencyMs;
    private double writeLatencyMs;
    private double pollSkipQps;
    private double pollSkipStreak;
    private double pollLastDurationMs;
    private double offlineDurationSec;
}
