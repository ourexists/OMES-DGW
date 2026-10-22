package com.omes.dgw.device.controller.core;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设备离线观测器：仅负责超时检测调度，状态变更统一由 {@link DeviceConnectionFailureHandler} 写入。
 */
@Slf4j
@Service
public class DeviceOfflineWatcher {

    private final DeviceRealtimeCache realtimeCacheService;
    private final ObjectProvider<DeviceConnectionFailureHandler> connectionFailureHandler;
    private final long checkDelayMs;
    private final long offlineTimeoutMs;
    private final boolean enabled;
    private final ScheduledThreadPoolExecutor scheduler;
    private final Map<String, ScheduledFuture<?>> pendingByDevice = new ConcurrentHashMap<>();

    public DeviceOfflineWatcher(
            DeviceRealtimeCache realtimeCacheService,
            @Lazy ObjectProvider<DeviceConnectionFailureHandler> connectionFailureHandler,
            AppProperties appProperties
    ) {
        this.realtimeCacheService = realtimeCacheService;
        this.connectionFailureHandler = connectionFailureHandler;
        AppProperties.Offline offline = appProperties.getCollect().getOffline();
        this.enabled = offline.isEnabled();
        this.checkDelayMs = Math.max(1, offline.getCheckDelayMs());
        this.offlineTimeoutMs = Math.max(1, offline.getOfflineTimeoutMs());
        int poolSize = Math.max(1, offline.getSchedulerPoolSize());
        this.scheduler = new ScheduledThreadPoolExecutor(
                poolSize,
                daemonThreadFactory("device-offline-watch-")
        );
        this.scheduler.setRemoveOnCancelPolicy(true);
        log.info("设备离线检测已初始化, enabled={}, checkDelayMs={}, offlineTimeoutMs={}, poolSize={}",
                enabled, checkDelayMs, offlineTimeoutMs, poolSize);
    }

    /**
     * 观测
     */
    public void watch(DeviceRealtimeObj snapshot) {
        if (!enabled || snapshot == null || !StringUtils.hasText(snapshot.getDeviceId())) {
            return;
        }
        if (!snapshot.isOnline()) {
            return;
        }
        String deviceId = snapshot.getDeviceId();
        LocalDateTime lastUpdate = snapshot.getLastUpdateTime() != null
                ? snapshot.getLastUpdateTime()
                : LocalDateTime.now();

        cancelPending(deviceId);

        ScheduledFuture<?> future = scheduler.schedule(
                () -> checkOffline(deviceId, lastUpdate),
                checkDelayMs,
                TimeUnit.MILLISECONDS
        );
        pendingByDevice.put(deviceId, future);
    }

    public void cancelWatch(String deviceId) {
        if (deviceId == null) {
            return;
        }
        cancelPending(deviceId);
    }

    private void checkOffline(String deviceId, LocalDateTime expectedLastUpdate) {
        pendingByDevice.remove(deviceId);
        try {
            DeviceRealtimeObj current = realtimeCacheService.getByDeviceId(deviceId);
            if (current == null || !current.isOnline()) {
                return;
            }
            LocalDateTime lastUpdate = current.getLastUpdateTime();
            if (lastUpdate == null) {
                return;
            }
            if (lastUpdate.isAfter(expectedLastUpdate)) {
                return;
            }
            long ageMs = Duration.between(lastUpdate, LocalDateTime.now()).toMillis();
            if (ageMs <= offlineTimeoutMs) {
                return;
            }
            connectionFailureHandler.ifAvailable(
                    handler -> handler.onCollectTimeout(deviceId, expectedLastUpdate));
            log.debug("设备采集超时检测完成 deviceId={}, ageMs={}, offlineTimeoutMs={}",
                    deviceId, ageMs, offlineTimeoutMs);
        } catch (Exception e) {
            log.warn("设备离线检查失败 deviceId={}: {}", deviceId, e.getMessage());
        }
    }

    private void cancelPending(String deviceId) {
        ScheduledFuture<?> previous = pendingByDevice.remove(deviceId);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        pendingByDevice.values().forEach(f -> f.cancel(false));
        pendingByDevice.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
