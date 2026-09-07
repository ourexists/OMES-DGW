package com.omes.dgw.device.online;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.queue.BoundedListQueueOps;
import com.omes.dgw.queue.CollectQueueMetrics;
import com.omes.dgw.queue.CollectQueueNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.collect.online-event", name = "enabled", havingValue = "true")
public class DeviceOnlineEventQueueService {

    private static final String QUEUE_KEY = "collect:online-event:queue";

    private final ObjectMapper objectMapper;
    private final BoundedListQueueOps boundedQueueOps;
    private final CollectQueueMetrics queueMetrics;
    private final int batchSize;
    private final int maxQueueSize;

    public DeviceOnlineEventQueueService(
            ObjectMapper objectMapper,
            BoundedListQueueOps boundedQueueOps,
            CollectQueueMetrics queueMetrics,
            AppProperties appProperties
    ) {
        this.objectMapper = objectMapper;
        this.boundedQueueOps = boundedQueueOps;
        this.queueMetrics = queueMetrics;
        AppProperties.OnlineEvent onlineEvent = appProperties.getCollect().getOnlineEvent();
        this.batchSize = Math.max(1, onlineEvent.getBatchSize());
        this.maxQueueSize = Math.max(batchSize, onlineEvent.getMaxQueueSize());
        log.info("设备在线态队列已启用（本地）, batchSize={}, maxQueueSize={}",
                batchSize, maxQueueSize);
    }

    public int batchSize() {
        return batchSize;
    }

    public void enqueue(DeviceOnlineEventQueueItem item) {
        if (item == null || item.getOp() == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(item);
            boundedQueueOps.rightPushWithTrim(
                    CollectQueueNames.ONLINE_EVENT,
                    QUEUE_KEY,
                    json,
                    maxQueueSize
            );
        } catch (JsonProcessingException e) {
            log.error("序列化在线态队列消息失败 eventId={}, deviceId={}",
                    item.getEventId(), item.getDeviceId(), e);
        }
    }

    public long queueSize() {
        return boundedQueueOps.size(QUEUE_KEY);
    }

    public void publishQueueDepth() {
        queueMetrics.updateDepth(CollectQueueNames.ONLINE_EVENT, queueSize());
    }

    public boolean reachedBatchThreshold() {
        return queueSize() >= batchSize;
    }

    public boolean exceedsMaxQueue() {
        return queueSize() >= maxQueueSize;
    }

    public List<DeviceOnlineEventQueueItem> popBatch(int count) {
        int n = Math.max(1, Math.min(count, batchSize));
        List<String> jsonList = boundedQueueOps.popBatch(QUEUE_KEY, n);
        if (jsonList.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeviceOnlineEventQueueItem> result = new ArrayList<>(jsonList.size());
        for (String json : jsonList) {
            DeviceOnlineEventQueueItem item = parse(json);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private DeviceOnlineEventQueueItem parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DeviceOnlineEventQueueItem.class);
        } catch (JsonProcessingException e) {
            log.warn("反序列化在线态队列 JSON 失败: {}", e.getMessage());
            return null;
        }
    }
}
