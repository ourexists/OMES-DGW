package com.omes.dgw.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 监控指标采样结果同步入队，达阈值或定时批量写入 InfluxDB。
 */
@Slf4j
@Service
public class ProtocolMetricsInfluxConsumer implements ProtocolMetricsSampleListener {

    private final ProtocolMetricsInfluxQueueService queueService;
    private final ProtocolMetricsHistoryWriter influxWriter;
    private final MetricsInfluxWriteLeader writeLeader;
    private final com.omes.dgw.plugin.StorePluginRuntime runtime;
    private final com.omes.dgw.config.AppProperties properties;
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    public ProtocolMetricsInfluxConsumer(
            ProtocolMetricsInfluxQueueService queueService,
            ProtocolMetricsHistoryWriter batchWriter,
            MetricsInfluxWriteLeader writeLeader,
            com.omes.dgw.plugin.StorePluginRuntime runtime,
            com.omes.dgw.config.AppProperties properties
    ) {
        this.queueService = queueService;
        this.influxWriter = batchWriter;
        this.writeLeader = writeLeader;
        this.runtime = runtime;
        this.properties = properties;
    }

    /**
     * 单次采样周期产生的全部指标点入队。
     */
    @Override
    public void onSampleCompleted(List<ProtocolMetricsHistoryStore.MetricsPoint> points) {
        runtime.read(() -> { if (runtime.metricsEnabled()) onSampleActive(points); });
    }

    private void onSampleActive(List<ProtocolMetricsHistoryStore.MetricsPoint> points) {
        if (points == null || points.isEmpty() || !writeLeader.isLeader()) {
            return;
        }
        queueService.enqueueAll(points);
        if (queueService.reachedBatchThreshold() || queueService.exceedsMaxQueue()) {
            flushBatch();
        }
    }

    private volatile long lastScheduledFlush;

    @Scheduled(fixedDelay = 1000)
    public void scheduledFlush() {
        long now = System.nanoTime();
        if (now - lastScheduledFlush < java.util.concurrent.TimeUnit.SECONDS.toNanos(Math.max(1, properties.getCollect().getMetrics().getInfluxFlushIntervalSec()))) return;
        lastScheduledFlush = now;
        if (runtime.metricsEnabled() && queueService.queueSize() > 0) {
            flushBatch();
        }
    }

    public void flushBatch() {
        runtime.read(() -> { if (runtime.metricsEnabled() && writeLeader.isLeader()) flushActive(); });
    }

    private void flushActive() {
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
                try {
                    influxWriter.writeMetrics(batch);
                } catch (Exception ex) {
                    queueService.requeue(batch);
                    throw ex;
                }
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
