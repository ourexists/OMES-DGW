package com.omes.dgw.metrics;

import com.omes.dgw.device.cache.DeviceRealtimeHistoryQueueService;
import com.omes.dgw.device.online.DeviceOnlineEventQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期刷新本地队列深度 Gauge（消费端 pop 后深度仅靠入队更新会滞后）。
 */
@Slf4j
@Component
public class CollectQueueDepthRefresher {

    private final ObjectProvider<DeviceRealtimeHistoryQueueService> historyQueue;
    private final ObjectProvider<ProtocolMetricsInfluxQueueService> metricsQueue;
    private final ObjectProvider<DeviceOnlineEventQueueService> onlineEventQueue;

    public CollectQueueDepthRefresher(
            ObjectProvider<DeviceRealtimeHistoryQueueService> historyQueue,
            ObjectProvider<ProtocolMetricsInfluxQueueService> metricsQueue,
            ObjectProvider<DeviceOnlineEventQueueService> onlineEventQueue
    ) {
        this.historyQueue = historyQueue;
        this.metricsQueue = metricsQueue;
        this.onlineEventQueue = onlineEventQueue;
    }

    @Scheduled(fixedDelayString = "${app.collect.metrics.queue-depth-refresh-ms:30000}")
    public void refresh() {
        historyQueue.ifAvailable(DeviceRealtimeHistoryQueueService::publishQueueDepth);
        metricsQueue.ifAvailable(ProtocolMetricsInfluxQueueService::publishQueueDepth);
        onlineEventQueue.ifAvailable(DeviceOnlineEventQueueService::publishQueueDepth);
    }
}
