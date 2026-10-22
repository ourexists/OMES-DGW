package com.omes.dgw.device.online;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在线态变更本地队列批量落库 SQLite。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.collect.online-event", name = "enabled", havingValue = "true")
@ConditionalOnBean({DeviceOnlineEventQueueService.class, DeviceOnlineEventBatchWriter.class})
public class DeviceOnlineEventConsumer {

    private final DeviceOnlineEventQueueService queueService;
    private final DeviceOnlineEventBatchWriter batchWriter;
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    public DeviceOnlineEventConsumer(
            DeviceOnlineEventQueueService queueService,
            DeviceOnlineEventBatchWriter batchWriter
    ) {
        this.queueService = queueService;
        this.batchWriter = batchWriter;
    }

    public void onEnqueued() {
        if (queueService.reachedBatchThreshold() || queueService.exceedsMaxQueue()) {
            flushBatch();
        }
    }

    @Scheduled(fixedDelayString = "${app.collect.online-event.flush-interval-sec:30}000")
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
            while (queueService.queueSize() > 0) {
                List<DeviceOnlineEventQueueItem> batch = queueService.popBatch(queueService.batchSize());
                if (batch.isEmpty()) {
                    break;
                }
                batchWriter.applyBatch(batch);
                if (batch.size() < queueService.batchSize()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("在线态批量落库失败: {}", e.getMessage(), e);
        } finally {
            flushing.set(false);
        }
    }
}
