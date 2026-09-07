package com.omes.dgw.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轮询防重入跳过：累计次数、连续跳过 streak、最近一轮耗时（用于判断 cron 是否跟得上）。
 */
@Component
public class ProtocolPollSkipMetrics {

    public static final String METER_SKIP_TOTAL = "collect.protocol.poll.skip";
    public static final String METER_SKIP_STREAK = "collect.protocol.poll.skip_streak";
    public static final String METER_LAST_DURATION_MS = "collect.protocol.poll.last_duration_ms";

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> streakHolders = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastDurationHolders = new ConcurrentHashMap<>();
    private final Map<String, Counter> skipCounters = new ConcurrentHashMap<>();

    public ProtocolPollSkipMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordGateSkipped(String protocol, String deviceId, int consecutiveSkips) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        skipCounter(protocol, deviceId).increment();
        streakHolder(protocol, deviceId).set(Math.max(0, consecutiveSkips));
    }

    public void clearGateSkipStreak(String protocol, String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        streakHolder(protocol, deviceId).set(0);
    }

    public void recordPollDurationMs(String protocol, String deviceId, long durationMs) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        lastDurationHolder(protocol, deviceId).set(Math.max(0, durationMs));
    }

    public void removeDevice(String protocol, String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        String key = metricKey(protocol, deviceId);
        streakHolders.remove(key);
        lastDurationHolders.remove(key);
        skipCounters.remove(key);
    }

    private Counter skipCounter(String protocol, String deviceId) {
        String key = metricKey(protocol, deviceId);
        return skipCounters.computeIfAbsent(key, k ->
                Counter.builder(METER_SKIP_TOTAL)
                        .tag(ProtocolCollectMetrics.TAG_PROTOCOL, protocol)
                        .tag(ProtocolCollectMetrics.TAG_DEVICE_ID, deviceId)
                        .description("因上一轮仍在执行而被防重入门禁跳过的 cron 触发次数")
                        .register(registry)
        );
    }

    private AtomicLong streakHolder(String protocol, String deviceId) {
        String key = metricKey(protocol, deviceId);
        return streakHolders.computeIfAbsent(key, k -> {
            AtomicLong holder = new AtomicLong(0);
            Gauge.builder(METER_SKIP_STREAK, holder, AtomicLong::get)
                    .tag(ProtocolCollectMetrics.TAG_PROTOCOL, protocol)
                    .tag(ProtocolCollectMetrics.TAG_DEVICE_ID, deviceId)
                    .description("连续因防重入跳过的 cron 次数；成功开始新一轮采集时归零")
                    .register(registry);
            return holder;
        });
    }

    private AtomicLong lastDurationHolder(String protocol, String deviceId) {
        String key = metricKey(protocol, deviceId);
        return lastDurationHolders.computeIfAbsent(key, k -> {
            AtomicLong holder = new AtomicLong(0);
            Gauge.builder(METER_LAST_DURATION_MS, holder, AtomicLong::get)
                    .tag(ProtocolCollectMetrics.TAG_PROTOCOL, protocol)
                    .tag(ProtocolCollectMetrics.TAG_DEVICE_ID, deviceId)
                    .description("最近一轮整轮轮询耗时（毫秒）：设备读+解析+写实时缓存，不含防重入跳过")
                    .register(registry);
            return holder;
        });
    }

    private static String metricKey(String protocol, String deviceId) {
        return protocol + ":" + deviceId;
    }
}
