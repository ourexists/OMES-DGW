package com.omes.dgw.metrics.dto;

import com.omes.dgw.monitoring.dto.MonitoringInfrastructureVo;
import com.omes.dgw.monitoring.dto.MonitoringPollSkipVo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 前端 ECharts 折线图时序数据（多序列共享横轴 timestamps），含设备状态与基础设施快照。
 */
@Data
public class CollectMetricsChartVo {

    private int intervalSec;

    /** 实际查询的时间范围（分钟） */
    private int rangeMinutes;

    /** 数据来源：{@code memory}=进程内环形缓冲；{@code influx}=InfluxDB */
    private String querySource;

    private String deviceId;
    private String deviceCode;
    private String deviceName;
    private String protocol;

    private List<String> timestamps = new ArrayList<>();
    private List<Double> readQps = new ArrayList<>();
    private List<Double> pollQps = new ArrayList<>();
    private List<Double> writeQps = new ArrayList<>();
    private List<Double> readFailRate = new ArrayList<>();
    private List<Double> pollFailRate = new ArrayList<>();
    private List<Double> writeFailRate = new ArrayList<>();
    private List<Double> readLatencyMs = new ArrayList<>();
    private List<Double> pollLatencyMs = new ArrayList<>();
    private List<Double> writeLatencyMs = new ArrayList<>();
    private List<Double> pollSkipQps = new ArrayList<>();
    private List<Double> pollSkipStreak = new ArrayList<>();
    private List<Double> pollLastDurationMs = new ArrayList<>();
    private List<Double> offlineDurationSec = new ArrayList<>();

    /** 设备实时状态快照（仅单设备 chart 接口填充） */
    private Boolean online;
    private String connectionStatus;
    private String connectionStatusLabel;
    private String connectionStatusMessage;
    private String offlineReason;
    private String lastUpdateTime;

    /** 轮询调度快照（仅单设备 chart 接口填充） */
    private MonitoringPollSkipVo pollSkip;

    /** 基础设施快照（仅单设备 chart 接口填充） */
    private MonitoringInfrastructureVo infrastructure;
    private String instanceId;
}
