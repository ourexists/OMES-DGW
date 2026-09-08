package com.omes.dgw.device.cache;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.timeseries.DeviceRealtimeHistoryWriter;
import com.omes.dgw.timeseries.InfluxRuntime;
import lombok.extern.slf4j.Slf4j;
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
public class DeviceRealtimeHistoryConsumer {

    private final DeviceRealtimeHistoryQueueService queueService;
    private final DeviceRealtimeHistoryWriter influxWriter;
    private final InfluxRuntime influx;
    private final AppProperties properties;
    private final com.omes.dgw.plugin.StorePluginRuntime runtime;
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final AtomicBoolean influxWritePaused = new AtomicBoolean(false);

    public DeviceRealtimeHistoryConsumer(
            DeviceRealtimeHistoryQueueService queueService,
            DeviceRealtimeHistoryWriter influxWriter,
            InfluxRuntime influx,
            AppProperties appProperties,
            com.omes.dgw.plugin.StorePluginRuntime runtime
    ) {
        this.queueService = queueService;
        this.influxWriter = influxWriter;
        this.influx = influx;
        this.properties = appProperties;
        this.runtime = runtime;
    }

    /**
     * 新实时快照写入本地缓存后同步入队（复用已序列化的 JSON）。
     */
    public void onSnapshotSaved(DeviceRealtimeObj snapshot, String snapshotJson) {
        runtime.read(() -> {
            if (runtime.historyEnabled()) onSnapshotSavedActive(snapshot, snapshotJson);
        });
    }

    private void onSnapshotSavedActive(DeviceRealtimeObj snapshot, String snapshotJson) {
        queueService.enqueueJson(snapshotJson);
        if (queueService.reachedBatchThreshold() || queueService.exceedsMaxQueue()) {
            flushBatch();
        }
    }

    private volatile long lastScheduledFlush;

    @Scheduled(fixedDelay = 1000)
    public void scheduledFlush() {
        long now = System.nanoTime();
        if (now - lastScheduledFlush < java.util.concurrent.TimeUnit.SECONDS.toNanos(Math.max(1, properties.getCollect().getHistory().getFlushIntervalSec()))) return;
        lastScheduledFlush = now;
        if (queueService.queueSize() > 0) {
            flushBatch();
        }
    }

    public void flushBatch() {
        runtime.read(() -> { if (runtime.historyEnabled()) flushActive(); });
    }

    private void flushActive() {
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
            if (nextRetry > Math.max(0, properties.getCollect().getHistory().getMaxWriteRetries())) {
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
