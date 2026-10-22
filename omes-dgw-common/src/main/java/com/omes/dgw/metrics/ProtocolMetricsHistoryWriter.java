package com.omes.dgw.metrics;

import java.util.List;

/**
 * 协议监控指标历史写入 SPI（Influx）。
 */
public interface ProtocolMetricsHistoryWriter {

    String MEASUREMENT = "collect_protocol_metrics";

    void writeMetrics(List<ProtocolMetricsHistoryStore.MetricsPoint> points);
}
