package com.omes.dgw.device.cache;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.influx.DeviceRealtimeHistoryWriter;
import com.omes.dgw.influx.InfluxRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 新实时快照同步入队，达批量或定时批量写入 InfluxDB。
 * 写入失败带重试上限；超过最大重试次数则丢弃并记日志。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.collect.history", name = "enabled", havingValue = "true")
@ConditionalOnBean({DeviceRealtimeHistoryQueueService.class, DeviceRealtimeHistoryWriter.class})
public class DeviceRealtimeHistoryConsumer {

    private final DeviceRealtimeHistoryQueueService queueService;
    private final DeviceRealtimeHistoryWriter influxWriter;
    private final InfluxRuntime influx;
    private final int maxWriteRetries;
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final AtomicBoolean influxWritePaused = new AtomicBoolean(false);

    public DeviceRealtimeHistoryConsumer(
            DeviceRealtimeHistoryQueueService queueService,
            DeviceRealtimeHistoryWriter influxWriter,
            InfluxRuntime influx,
            AppProperties appProperties
    ) {
        this.queueService = queueService;
        this.influxWriter = influxWriter;
        this.influx = influx;
        this.maxWriteRetries = Math.max(0, appProperties.getCollect().getHistory().getMaxWriteRetries());
    }

    /**
     * 新实时快照写入本地缓存后同步入队（复用已序列化的 JSON）。
     */
    public void onSnapshotSaved(DeviceRealtimeObj snapshot, String snapshotJson) {
        queueService.enqueueJson(snapshotJson);
        if (queueService.reachedBatchThreshold() || queueService.exceedsMaxQueue()) {
            flushBatch();
        }
    }

    @Scheduled(fixedDelayString = "${app.collect.history.flush-interval-sec:60}000")
    public void scheduledFlush() {
        if (queueService.queueSize() > 0) {
            flushBatch();
        }
    }

    public void flushBatch() {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            if (influxWritePaused.get() && !influx.ping()) {
                return;
            }
            if (influxWritePaused.get()) {
                influxWritePaused.set(false);
            }

            while (queueService.queueSize() > 0) {
                List<HistoryQueueEnvelope> batch = queueService.popBatch(queueService.batchSize());
                if (batch.isEmpty()) {
                    break;
                }
                try {
                    influxWriter.writeSnapshots(toSnapshots(batch));
                } catch (Exception e) {
                    handleWriteFailure(batch, e);
                    break;
                }
                if (batch.size() < queueService.batchSize()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("实时历史刷写异常: {}", e.getMessage(), e);
        } finally {
            flushing.set(false);
        }
    }

    private void handleWriteFailure(List<HistoryQueueEnvelope> batch, Exception e) {
        boolean influxDown = !influx.ping();
        if (influxDown) {
            influxWritePaused.set(true);
        }
        List<HistoryQueueEnvelope> requeue = new ArrayList<>();
        int discarded = 0;
        for (HistoryQueueEnvelope envelope : batch) {
            int nextRetry = envelope.getRetryCount() + 1;
            envelope.setRetryCount(nextRetry);
            String deviceId = envelope.getSnapshot().getDeviceId();
            if (nextRetry > maxWriteRetries) {
                discarded++;
                log.warn("实时历史写入放弃（超过最大重试） deviceId={} retryCount={}", deviceId, nextRetry);
            } else {
                requeue.add(envelope);
            }
        }
        if (!requeue.isEmpty()) {
            queueService.requeueEnvelopes(requeue);
        }
        log.error("实时历史批量刷写 Influx 失败，回队={} 丢弃={} influxDown={}: {}",
                requeue.size(), discarded, influxDown, e.getMessage(), e);
    }

    private static List<DeviceRealtimeObj> toSnapshots(List<HistoryQueueEnvelope> batch) {
        return batch.stream()
                .map(HistoryQueueEnvelope::getSnapshot)
                .collect(Collectors.toList());
    }
}
