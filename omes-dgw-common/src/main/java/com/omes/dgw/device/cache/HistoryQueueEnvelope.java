package com.omes.dgw.device.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 历史写入本地队列条目：携带 Influx 刷写重试次数，与实时缓存 JSON 解耦。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryQueueEnvelope {

    /** Influx 写入失败累计次数（首次入队为 0） */
    private int retryCount;

    private DeviceRealtimeObj snapshot;
}
