package com.omes.dgw.metrics;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 协议采集指标内存时序（供前端图表轮询）。
 */
@Component
public class ProtocolMetricsHistoryStore {

    /** 内存时序 Map 的汇总序列键 */
    public static final String SUMMARY_KEY = "__summary__";
    /** 汇总点在 MetricsPoint / Influx 中的 device_id 标识（勿与真实设备混淆） */
    public static final String SUMMARY_DEVICE_ID = "_summary_";
    public static final String SUMMARY_PROTOCOL = "ALL";

    public static boolean isSummary(MetricsPoint point) {
        if (point == null) {
            return false;
        }
        String deviceId = point.deviceId();
        return SUMMARY_DEVICE_ID.equals(deviceId)
                || SUMMARY_KEY.equals(deviceId)
                || deviceId == null
                || deviceId.isBlank();
    }

    private volatile int maxPoints = 360;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<MetricsPoint>> seriesMap = new ConcurrentHashMap<>();

    public void configure(int historyMinutes, int sampleIntervalSec) {
        int interval = Math.max(1, sampleIntervalSec);
        int minutes = Math.max(1, historyMinutes);
        this.maxPoints = Math.max(10, minutes * 60 / interval);
    }

    public void append(String seriesKey, MetricsPoint point) {
        ConcurrentLinkedDeque<MetricsPoint> deque = seriesMap.computeIfAbsent(seriesKey, k -> new ConcurrentLinkedDeque<>());
        deque.addLast(point);
        while (deque.size() > maxPoints) {
            deque.pollFirst();
        }
    }

    public List<MetricsPoint> getPoints(String seriesKey) {
        ConcurrentLinkedDeque<MetricsPoint> deque = seriesMap.get(seriesKey);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        return List.copyOf(deque);
    }

    public Map<String, ConcurrentLinkedDeque<MetricsPoint>> snapshotSeries() {
        return Map.copyOf(seriesMap);
    }

    public record MetricsPoint(
            Instant time,
            String protocol,
            String deviceId,
            double readQps,
            double pollQps,
            double writeQps,
            double readFailRate,
            double pollFailRate,
            double writeFailRate,
            double readLatencyMs,
            double pollLatencyMs,
            double writeLatencyMs,
            double pollSkipQps,
            double pollSkipStreak,
            double pollLastDurationMs,
            double offlineDurationSec
    ) {
    }
}
