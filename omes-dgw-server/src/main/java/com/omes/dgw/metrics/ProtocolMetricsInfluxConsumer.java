package com.omes.dgw.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 监控指标采样结果同步入队，达阈值或定时批量写入 InfluxDB。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.collect.metrics", name = "persist-to-influx", havingValue = "true")
@ConditionalOnBean({ProtocolMetricsInfluxQueueService.class, ProtocolMetricsHistoryWriter.class})
public class ProtocolMetricsInfluxConsumer implements ProtocolMetricsSampleListener {

    private final ProtocolMetricsInfluxQueueService queueService;
    private final ProtocolMetricsHistoryWriter influxWriter;
    private final MetricsInfluxWriteLeader writeLeader;
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    public ProtocolMetricsInfluxConsumer(
            ProtocolMetricsInfluxQueueService queueService,
            ProtocolMetricsHistoryWriter batchWriter,
            MetricsInfluxWriteLeader writeLeader
    ) {
        this.queueService = queueService;
        this.influxWriter = batchWriter;
        this.writeLeader = writeLeader;
    }

    /**
     * 单次采样周期产生的全部指标点入队。
     */
    @Override
    public void onSampleCompleted(List<ProtocolMetricsHistoryStore.MetricsPoint> points) {
        if (points == null || points.isEmpty() || !writeLeader.isLeader()) {
            return;
        }
        queueService.enqueueAll(points);
        if (queueService.reachedBatchThreshold() || queueService.exceedsMaxQueue()) {
            flushBatch();
        }
    }

    @Scheduled(fixedDelayString = "${app.collect.metrics.influx-flush-interval-sec:60}000")
    public void scheduledFlush() {
        if (writeLeader.isLeader() && queueService.queueSize() > 0) {
            flushBatch();
        }
    }

    public void flushBatch() {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            while (queueService.queueSize() > 0) {
                List<ProtocolMetricsHistoryStore.MetricsPoint> batch =
                        queueService.popBatch(queueService.batchSize());
                if (batch.isEmpty()) {
                    break;
                }
                influxWriter.writeMetrics(batch);
                if (batch.size() < queueService.batchSize()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("监控指标批量刷写 Influx 失败: {}", e.getMessage(), e);
        } finally {
            flushing.set(false);
        }
    }
}
