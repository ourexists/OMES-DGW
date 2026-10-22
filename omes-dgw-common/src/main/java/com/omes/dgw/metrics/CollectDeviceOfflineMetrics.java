package com.omes.dgw.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备离线持续时长（秒），供 Prometheus 告警「离线超过 N 分钟」。
 */
@Component
public class CollectDeviceOfflineMetrics {

    public static final String METER_OFFLINE_DURATION = "collect.device.offline.duration";
    public static final String TAG_DEVICE_ID = "device_id";

    private final MeterRegistry registry;
    private final Map<String, LocalDateTime> offlineSince = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> durationHolders = new ConcurrentHashMap<>();

    public CollectDeviceOfflineMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 在实时缓存成功写入后调用。
     */
    public void observe(String deviceId, boolean online, LocalDateTime lastUpdateTime) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        if (online) {
            offlineSince.remove(deviceId);
            holder(deviceId).set(0);
            return;
        }
        offlineSince.computeIfAbsent(deviceId, id -> lastUpdateTime != null ? lastUpdateTime : LocalDateTime.now());
        refreshDuration(deviceId);
    }

    public void refreshDuration(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        LocalDateTime since = offlineSince.get(deviceId);
        if (since == null) {
            holder(deviceId).set(0);
            return;
        }
        long seconds = Math.max(0, Duration.between(since, LocalDateTime.now()).getSeconds());
        holder(deviceId).set(seconds);
    }

    public void removeDevice(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        offlineSince.remove(deviceId);
        durationHolders.remove(deviceId);
    }

    private AtomicLong holder(String deviceId) {
        return durationHolders.computeIfAbsent(deviceId, id -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder(METER_OFFLINE_DURATION, value, AtomicLong::get)
                    .tag(TAG_DEVICE_ID, id)
                    .baseUnit("seconds")
                    .description("设备处于离线状态的持续秒数")
                    .register(registry);
            return value;
        });
    }
}
