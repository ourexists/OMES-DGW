package com.omes.dgw.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内有界队列：尾部入队、头部出队；超限丢弃最旧数据。
 */
@Slf4j
public class BoundedLocalListQueueOps implements BoundedListQueueOps {

    private final CollectQueueMetrics queueMetrics;
    private final Map<String, Deque<String>> queues = new ConcurrentHashMap<>();

    public BoundedLocalListQueueOps(CollectQueueMetrics queueMetrics) {
        this.queueMetrics = queueMetrics;
    }

    @Override
    public EnqueueResult rightPushWithTrim(
            String queueMetricName,
            String queueKey,
            String payload,
            int maxQueueSize
    ) {
        if (!StringUtils.hasText(payload)) {
            return new EnqueueResult(0, 0);
        }
        int max = Math.max(1, maxQueueSize);
        EnqueueResult result;
        synchronized (queueFor(queueKey)) {
            Deque<String> queue = queueFor(queueKey);
            queue.addLast(payload);
            long trimmed = trimOldest(queue, max);
            result = new EnqueueResult(queue.size(), trimmed);
        }
        afterEnqueue(queueMetricName, queueKey, max, result);
        return result;
    }

    @Override
    public EnqueueResult requeueToHeadWithTrim(
            String queueMetricName,
            String queueKey,
            List<String> payloads,
            int maxQueueSize
    ) {
        if (payloads == null || payloads.isEmpty()) {
            return new EnqueueResult(0, 0);
        }
        int max = Math.max(1, maxQueueSize);
        EnqueueResult result;
        synchronized (queueFor(queueKey)) {
            Deque<String> queue = queueFor(queueKey);
            for (int i = payloads.size() - 1; i >= 0; i--) {
                String json = payloads.get(i);
                if (StringUtils.hasText(json)) {
                    queue.addFirst(json);
                }
            }
            long trimmed = trimOldest(queue, max);
            result = new EnqueueResult(queue.size(), trimmed);
        }
        afterEnqueue(queueMetricName, queueKey, max, result);
        return result;
    }

    @Override
    public List<String> popBatch(String queueKey, int count) {
        int n = Math.max(1, count);
        synchronized (queueFor(queueKey)) {
            Deque<String> queue = queueFor(queueKey);
            if (queue.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>(Math.min(n, queue.size()));
            for (int i = 0; i < n && !queue.isEmpty(); i++) {
                result.add(queue.removeFirst());
            }
            return result;
        }
    }

    @Override
    public long size(String queueKey) {
        synchronized (queueFor(queueKey)) {
            return queueFor(queueKey).size();
        }
    }

    private Deque<String> queueFor(String queueKey) {
        return queues.computeIfAbsent(queueKey, key -> new ArrayDeque<>());
    }

    private static long trimOldest(Deque<String> queue, int maxQueueSize) {
        long trimmed = 0;
        while (queue.size() > maxQueueSize) {
            queue.removeFirst();
            trimmed++;
        }
        return trimmed;
    }

    private void afterEnqueue(
            String queueMetricName,
            String queueKey,
            int maxQueueSize,
            EnqueueResult result
    ) {
        queueMetrics.updateDepth(queueMetricName, result.length());
        if (result.trimmed() > 0) {
            queueMetrics.recordTrimmed(queueMetricName, result.trimmed());
            log.warn("本地队列已达硬上限并丢弃最旧数据 queue={}, key={}, trimmed={}, max={}, currentLen={}",
                    queueMetricName, queueKey, result.trimmed(), maxQueueSize, result.length());
        } else if (result.length() >= maxQueueSize) {
            log.warn("本地队列已达上限 queue={}, key={}, len={}, max={}, 将尝试刷写",
                    queueMetricName, queueKey, result.length(), maxQueueSize);
        }
    }
}
