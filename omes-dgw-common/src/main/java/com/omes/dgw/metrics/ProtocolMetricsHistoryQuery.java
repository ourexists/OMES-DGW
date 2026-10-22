package com.omes.dgw.metrics;

import java.util.List;

/**
 * 协议监控指标历史查询 SPI（Influx）。
 */
public interface ProtocolMetricsHistoryQuery {

    List<ProtocolMetricsHistoryStore.MetricsPoint> queryDeviceSeries(String deviceId, int minutes);

    List<ProtocolMetricsHistoryStore.MetricsPoint> querySummarySeries(int minutes);

    /** 查询时间范围内各设备最新一条指标（用于总览表格）。 */
    List<ProtocolMetricsHistoryStore.MetricsPoint> queryLatestPerDevice(int minutes);
}
