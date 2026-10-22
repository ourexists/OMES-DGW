package com.omes.dgw.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.queue.BoundedListQueueOps;
import com.omes.dgw.queue.CollectQueueMetrics;
import com.omes.dgw.queue.CollectQueueNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ProtocolMetricsInfluxQueueService {

    private static final String QUEUE_KEY = "omes:dwg:metrics:queue";

    private final ObjectMapper objectMapper;
    private final BoundedListQueueOps boundedQueueOps;
    private final CollectQueueMetrics queueMetrics;
    private final AppProperties properties;

    public ProtocolMetricsInfluxQueueService(
            ObjectMapper objectMapper,
            BoundedListQueueOps boundedQueueOps,
            CollectQueueMetrics queueMetrics,
            AppProperties appProperties
    ) {
        this.objectMapper = objectMapper;
        this.boundedQueueOps = boundedQueueOps;
        this.queueMetrics = queueMetrics;
        this.properties = appProperties;
    }

    private int maxQueueSize() { return Math.max(batchSize(), properties.getCollect().getMetrics().getInfluxMaxQueueSize()); }

    public int batchSize() {
        return Math.max(1, properties.getCollect().getMetrics().getInfluxBatchSize());
    }

    public void enqueueAll(List<ProtocolMetricsHistoryStore.MetricsPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        for (ProtocolMetricsHistoryStore.MetricsPoint point : points) {
            enqueue(point);
        }
    }

    public void enqueue(ProtocolMetricsHistoryStore.MetricsPoint point) {
        if (point == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(point);
            boundedQueueOps.rightPushWithTrim(
                    CollectQueueNames.METRICS,
                    QUEUE_KEY,
                    json,
                    maxQueueSize()
            );
        } catch (JsonProcessingException e) {
            log.error("序列化监控指标失败: {}", e.getMessage(), e);
        }
    }

    public long queueSize() {
        return boundedQueueOps.size(QUEUE_KEY);
    }

    public void publishQueueDepth() {
        queueMetrics.updateDepth(CollectQueueNames.METRICS, queueSize());
    }

    public boolean reachedBatchThreshold() {
        return queueSize() >= batchSize();
    }

    public boolean exceedsMaxQueue() {
        return queueSize() >= maxQueueSize();
    }

    public List<ProtocolMetricsHistoryStore.MetricsPoint> popBatch(int count) {
        int n = Math.max(1, Math.min(count, batchSize()));
        List<String> jsonList = boundedQueueOps.popBatch(QUEUE_KEY, n);
        if (jsonList.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProtocolMetricsHistoryStore.MetricsPoint> result = new ArrayList<>(jsonList.size());
        for (String json : jsonList) {
            ProtocolMetricsHistoryStore.MetricsPoint point = parse(json);
            if (point != null) {
                result.add(point);
            }
        }
        return result;
    }

    public void requeue(List<ProtocolMetricsHistoryStore.MetricsPoint> points) {
        List<String> payloads = new ArrayList<>();
        try {
            for (var point : points) payloads.add(objectMapper.writeValueAsString(point));
        } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
        boundedQueueOps.requeueToHeadWithTrim(CollectQueueNames.METRICS, QUEUE_KEY, payloads, maxQueueSize());
    }

    private ProtocolMetricsHistoryStore.MetricsPoint parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProtocolMetricsHistoryStore.MetricsPoint.class);
        } catch (JsonProcessingException e) {
            log.warn("反序列化监控指标队列 JSON 失败: {}", e.getMessage());
            return null;
        }
    }
}
