package com.omes.dgw.queue;

import java.util.List;

/**
 * 有界 List 队列操作（本地进程内 / Redis List）。
 */
public interface BoundedListQueueOps {

    record EnqueueResult(long length, long trimmed) {
    }

    /**
     * 尾部入队并截断为最旧丢弃。
     */
    EnqueueResult rightPushWithTrim(
            String queueMetricName,
            String queueKey,
            String payload,
            int maxQueueSize
    );

    /**
     * 批量回队到队头（保持 payloads 原有顺序：旧→新），并截断。
     */
    EnqueueResult requeueToHeadWithTrim(
            String queueMetricName,
            String queueKey,
            List<String> payloads,
            int maxQueueSize
    );

    List<String> popBatch(String queueKey, int count);

    long size(String queueKey);
}
