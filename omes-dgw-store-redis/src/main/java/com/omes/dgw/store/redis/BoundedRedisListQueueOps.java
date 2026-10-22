package com.omes.dgw.store.redis;

import com.omes.dgw.queue.BoundedListQueueOps;
import com.omes.dgw.queue.CollectQueueMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis List + Lua 有界队列。
 */
@Slf4j
public class BoundedRedisListQueueOps implements BoundedListQueueOps {

    private final StringRedisTemplate redisTemplate;
    private final CollectQueueMetrics queueMetrics;

    private final DefaultRedisScript<List> rightPushTrimScript;
    private final DefaultRedisScript<List> requeueHeadTrimScript;
    private final DefaultRedisScript<List> popBatchScript;

    @SuppressWarnings("rawtypes")
    public BoundedRedisListQueueOps(StringRedisTemplate redisTemplate, CollectQueueMetrics queueMetrics) {
        this.redisTemplate = redisTemplate;
        this.queueMetrics = queueMetrics;

        this.rightPushTrimScript = new DefaultRedisScript<>();
        this.rightPushTrimScript.setScriptText(CollectRedisListQueueLuaScripts.RIGHT_PUSH_TRIM);
        this.rightPushTrimScript.setResultType(List.class);

        this.requeueHeadTrimScript = new DefaultRedisScript<>();
        this.requeueHeadTrimScript.setScriptText(CollectRedisListQueueLuaScripts.REQUEUE_HEAD_TRIM);
        this.requeueHeadTrimScript.setResultType(List.class);

        this.popBatchScript = new DefaultRedisScript<>();
        this.popBatchScript.setScriptText(CollectRedisListQueueLuaScripts.POP_BATCH);
        this.popBatchScript.setResultType(List.class);

        log.info("采集有界队列后端：Redis List");
    }

    @Override
    @SuppressWarnings("unchecked")
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
        List<Object> raw = redisTemplate.execute(
                rightPushTrimScript,
                List.of(queueKey),
                payload,
                String.valueOf(max)
        );
        EnqueueResult result = toEnqueueResult(raw);
        afterEnqueue(queueMetricName, queueKey, max, result);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
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
        List<String> args = new ArrayList<>(payloads.size() + 1);
        args.add(String.valueOf(max));
        for (String payload : payloads) {
            if (StringUtils.hasText(payload)) {
                args.add(payload);
            }
        }
        if (args.size() == 1) {
            return new EnqueueResult(size(queueKey), 0);
        }
        List<Object> raw = redisTemplate.execute(
                requeueHeadTrimScript,
                List.of(queueKey),
                args.toArray()
        );
        EnqueueResult result = toEnqueueResult(raw);
        afterEnqueue(queueMetricName, queueKey, max, result);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> popBatch(String queueKey, int count) {
        int n = Math.max(1, count);
        List<Object> raw = redisTemplate.execute(
                popBatchScript,
                List.of(queueKey),
                String.valueOf(n)
        );
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    @Override
    public long size(String queueKey) {
        Long len = redisTemplate.opsForList().size(queueKey);
        return len == null ? 0L : len;
    }

    private static EnqueueResult toEnqueueResult(List<Object> raw) {
        if (raw == null || raw.size() < 2) {
            return new EnqueueResult(0, 0);
        }
        long length = ((Number) raw.get(0)).longValue();
        long trimmed = ((Number) raw.get(1)).longValue();
        return new EnqueueResult(length, trimmed);
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
            log.warn("Redis 队列已达硬上限并丢弃最旧数据 queue={}, key={}, trimmed={}, max={}, currentLen={}",
                    queueMetricName, queueKey, result.trimmed(), maxQueueSize, result.length());
        } else if (result.length() >= maxQueueSize) {
            log.warn("Redis 队列已达上限 queue={}, key={}, len={}, max={}, 将尝试刷写",
                    queueMetricName, queueKey, result.length(), maxQueueSize);
        }
    }
}
