package com.omes.dgw.device.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.influx.InfluxRuntime;
import com.omes.dgw.queue.BoundedListQueueOps;
import com.omes.dgw.queue.CollectQueueMetrics;
import com.omes.dgw.queue.CollectQueueNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.collect.history", name = "enabled", havingValue = "true")
@ConditionalOnBean(InfluxRuntime.class)
public class DeviceRealtimeHistoryQueueService {

    private static final String QUEUE_KEY = "collect:history:queue";

    private final ObjectMapper objectMapper;
    private final BoundedListQueueOps boundedQueueOps;
    private final CollectQueueMetrics queueMetrics;
    private final int batchSize;
    private final int maxQueueSize;

    public DeviceRealtimeHistoryQueueService(
            ObjectMapper objectMapper,
            BoundedListQueueOps boundedQueueOps,
            CollectQueueMetrics queueMetrics,
            AppProperties appProperties
    ) {
        this.objectMapper = objectMapper;
        this.boundedQueueOps = boundedQueueOps;
        this.queueMetrics = queueMetrics;
        AppProperties.History history = appProperties.getCollect().getHistory();
        this.batchSize = Math.max(1, history.getBatchSize());
        this.maxQueueSize = Math.max(batchSize, history.getMaxQueueSize());
        log.info("设备实时历史队列已启用（本地）, batchSize={}, maxQueueSize={}",
                batchSize, maxQueueSize);
    }

    public int batchSize() {
        return batchSize;
    }

    /**
     * 入队已序列化的快照 JSON（与实时缓存写入路径共用同一份，避免二次序列化）。
     */
    public void enqueueJson(String snapshotJson) {
        if (!StringUtils.hasText(snapshotJson)) {
            return;
        }
        boundedQueueOps.rightPushWithTrim(
                CollectQueueNames.HISTORY,
                QUEUE_KEY,
                snapshotJson,
                maxQueueSize
        );
    }

    public void enqueue(DeviceRealtimeObj snapshot) {
        if (snapshot == null || snapshot.getDeviceId() == null) {
            return;
        }
        try {
            enqueueJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            log.error("序列化历史快照失败 deviceId={}", snapshot.getDeviceId(), e);
        }
    }

    /**
     * 写入 Influx 失败时将批次恢复到队列头部（携带重试计数）；超长时丢弃最旧条目。
     */
    public void requeueEnvelopes(List<HistoryQueueEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return;
        }
        List<String> jsonPayloads = new ArrayList<>(envelopes.size());
        for (HistoryQueueEnvelope envelope : envelopes) {
            if (envelope == null || envelope.getSnapshot() == null
                    || envelope.getSnapshot().getDeviceId() == null) {
                continue;
            }
            try {
                jsonPayloads.add(objectMapper.writeValueAsString(envelope));
            } catch (JsonProcessingException e) {
                log.error("历史快照回队序列化失败 deviceId={}",
                        envelope.getSnapshot().getDeviceId(), e);
            }
        }
        if (jsonPayloads.isEmpty()) {
            return;
        }
        boundedQueueOps.requeueToHeadWithTrim(
                CollectQueueNames.HISTORY,
                QUEUE_KEY,
                jsonPayloads,
                maxQueueSize
        );
    }

    public long queueSize() {
        return boundedQueueOps.size(QUEUE_KEY);
    }

    /** 将当前队列长度写入 {@link CollectQueueMetrics}（供定时刷新与告警）。 */
    public void publishQueueDepth() {
        queueMetrics.updateDepth(CollectQueueNames.HISTORY, queueSize());
    }

    public boolean reachedBatchThreshold() {
        return queueSize() >= batchSize;
    }

    /** 队列已达硬上限，应优先触发刷写。 */
    public boolean exceedsMaxQueue() {
        return queueSize() >= maxQueueSize;
    }

    public List<HistoryQueueEnvelope> popBatch(int count) {
        int n = Math.max(1, Math.min(count, batchSize));
        List<String> jsonList = boundedQueueOps.popBatch(QUEUE_KEY, n);
        if (jsonList.isEmpty()) {
            return Collections.emptyList();
        }
        List<HistoryQueueEnvelope> result = new ArrayList<>(jsonList.size());
        for (String json : jsonList) {
            HistoryQueueEnvelope envelope = parseEnvelope(json);
            if (envelope != null && envelope.getSnapshot() != null) {
                result.add(envelope);
            }
        }
        return result;
    }

    /**
     * 解析队列 JSON：新入队为裸快照；回队/降级为 {@link HistoryQueueEnvelope}。
     */
    HistoryQueueEnvelope parseEnvelope(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("snapshot")) {
                return objectMapper.readValue(json, HistoryQueueEnvelope.class);
            }
            DeviceRealtimeObj snapshot = objectMapper.readValue(json, DeviceRealtimeObj.class);
            if (snapshot == null) {
                return null;
            }
            return new HistoryQueueEnvelope(0, snapshot);
        } catch (JsonProcessingException e) {
            log.warn("反序列化历史队列 JSON 失败: {}", e.getMessage());
            return null;
        }
    }
}
