package com.omes.dgw.queue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地有界队列深度（Gauge）与截断丢弃条数（Counter），供 Prometheus 告警。
 */
@Component
public class CollectQueueMetrics {

    public static final String METER_DEPTH = "collect.queue.depth";
    public static final String METER_TRIMMED = "collect.queue.trimmed";
    public static final String TAG_QUEUE = "queue";

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> depthHolders = new ConcurrentHashMap<>();
    private final Map<String, Counter> trimmedCounters = new ConcurrentHashMap<>();

    public CollectQueueMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void updateDepth(String queueName, long depth) {
        depthHolders.computeIfAbsent(queueName, name -> {
            AtomicLong holder = new AtomicLong(0);
            Gauge.builder(METER_DEPTH, holder, AtomicLong::get)
                    .tag(TAG_QUEUE, name)
                    .description("本地待刷写队列当前长度")
                    .register(registry);
            return holder;
        }).set(depth);
    }

    public void recordTrimmed(String queueName, long dropped) {
        if (dropped <= 0) {
            return;
        }
        trimmedCounter(queueName).increment(dropped);
    }

    private Counter trimmedCounter(String queueName) {
        return trimmedCounters.computeIfAbsent(queueName, name ->
                Counter.builder(METER_TRIMMED)
                        .tag(TAG_QUEUE, name)
                        .description("因超过 maxQueueSize 被丢弃的队列元素条数")
                        .register(registry)
        );
    }
}
