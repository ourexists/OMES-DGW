package com.omes.dgw.metrics;

import java.util.List;

/**
 * 指标采样完成回调 SPI；由 Influx 消费者等可选组件实现。
 */
public interface ProtocolMetricsSampleListener {

    void onSampleCompleted(List<ProtocolMetricsHistoryStore.MetricsPoint> points);
}
