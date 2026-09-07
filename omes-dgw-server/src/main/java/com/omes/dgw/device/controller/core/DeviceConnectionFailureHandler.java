package com.omes.dgw.device.controller.core;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.cache.DeviceRealtimePointSupport;
import com.omes.dgw.device.enums.DeviceConnectionStatus;
import com.omes.dgw.device.enums.DeviceOfflineReason;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 设备连接熔断：连续失败次数熔断 + 指数退避 + 半开恢复；超过 {@code maxOpenRetries} 次后放弃自动恢复。
 */
@Slf4j
@Service
public class DeviceConnectionFailureHandler {

    private static final DateTimeFormatter RETRY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Map<String, DeviceConnectionCircuitState> circuits = new ConcurrentHashMap<>();
    private final DeviceRealtimeCache realtimeCache;
    private final ObjectProvider<DeviceOfflineWatcher> offlineWatcher;
    private final ObjectProvider<DeviceLifecycleManager> lifecycleManager;
    private final ScheduledExecutorService recoveryScheduler;

    private final int maxConsecutiveFailures;
    private final long backoffInitialMs;
    private final long backoffMaxMs;
    private final double backoffMultiplier;
    private final int maxOpenRetries;
    private final int backoffMaxScaleSteps;
    private final double backoffMaxScaleFactor;

    public DeviceConnectionFailureHandler(
            AppProperties appProperties,
            DeviceRealtimeCache realtimeCache,
            ObjectProvider<DeviceOfflineWatcher> offlineWatcher,
            @Lazy ObjectProvider<DeviceLifecycleManager> lifecycleManager
    ) {
        this.realtimeCache = realtimeCache;
        this.offlineWatcher = offlineWatcher;
        this.lifecycleManager = lifecycleManager;
        AppProperties.Connection connection = appProperties.getCollect().getConnection();
        this.maxConsecutiveFailures = connection.getMaxConsecutiveFailures();
        this.backoffInitialMs = Math.max(1_000L, connection.getBackoffInitialMs());
        this.backoffMaxMs = Math.max(this.backoffInitialMs, connection.getBackoffMaxMs());
        this.backoffMultiplier = connection.getBackoffMultiplier() >= 1.0
                ? connection.getBackoffMultiplier()
                : 2.0;
        this.maxOpenRetries = Math.max(1, connection.getMaxOpenRetries());
        this.backoffMaxScaleSteps = Math.max(0, connection.getBackoffMaxScaleSteps());
        this.backoffMaxScaleFactor = connection.getBackoffMaxScaleFactor() >= 0.0
                ? connection.getBackoffMaxScaleFactor()
                : 0.1;
        this.recoveryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "device-conn-recovery");
            t.setDaemon(true);
            return t;
        });
        if (isCircuitBreakerEnabled()) {
            log.info("设备连接熔断: maxFailures={}, backoffInitialMs={}, backoffMaxMs={}, multiplier={}, "
                            + "maxOpenRetries={}, backoffMaxScaleSteps={}, backoffMaxScaleFactor={}",
                    maxConsecutiveFailures, backoffInitialMs, backoffMaxMs, backoffMultiplier,
                    maxOpenRetries, backoffMaxScaleSteps, backoffMaxScaleFactor);
        } else {
            log.info("设备连接熔断已关闭 (max-consecutive-failures=-1)");
        }
    }

    @PreDestroy
    void shutdown() {
        recoveryScheduler.shutdownNow();
    }

    /** -1 表示不启用熔断，永不断连。 */
    public boolean isCircuitBreakerEnabled() {
        return maxConsecutiveFailures >= 0;
    }

    public boolean isAbandoned(String deviceId) {
        DeviceConnectionCircuitState ctx = circuits.get(deviceId);
        return ctx != null && ctx.phase == DeviceConnectionCircuitState.Phase.ABANDONED;
    }

    public boolean isHalfOpen(String deviceId) {
        DeviceConnectionCircuitState ctx = circuits.get(deviceId);
        return ctx != null && ctx.phase == DeviceConnectionCircuitState.Phase.HALF_OPEN;
    }

    public boolean isOpenWaiting(String deviceId) {
        DeviceConnectionCircuitState ctx = circuits.get(deviceId);
        return ctx != null && ctx.phase == DeviceConnectionCircuitState.Phase.OPEN;
    }

    /** 轮询已启动但尚未成功完成首轮采集。 */
    public boolean isPollUnconfirmed(String deviceId) {
        DeviceConnectionCircuitState ctx = circuits.get(deviceId);
        return ctx == null || !ctx.pollConfirmed;
    }

    /** 熔断未打开、轮询仍在重试中的连续失败。 */
    public boolean hasAccumulatedFailures(String deviceId) {
        DeviceConnectionCircuitState ctx = circuits.get(deviceId);
        return ctx != null
                && ctx.phase == DeviceConnectionCircuitState.Phase.CLOSED
                && ctx.consecutiveFailures.get() > 0;
    }

    /**
     * 轮询成功：闭合熔断并重置失败计数。
     */
    public void onPollSuccess(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        DeviceConnectionCircuitState ctx = circuits.computeIfAbsent(
                deviceId, k -> new DeviceConnectionCircuitState());
        synchronized (ctx.lock) {
            boolean wasRecovering = ctx.phase != DeviceConnectionCircuitState.Phase.CLOSED;
            closeCircuitLocked(ctx);
            ctx.pollConfirmed = true;
            if (wasRecovering) {
                updateCacheStatus(deviceId, DeviceConnectionStatus.CONNECTED, "连接已恢复");
                log.info("设备连接半开恢复成功 deviceId={}", deviceId);
            }
        }
    }

    /**
     * @return true 表示应停止轮询（已熔断或半开探测失败）
     */
    public boolean onPollFailure(String deviceId, String protocol, String reason) {
        if (!StringUtils.hasText(deviceId)) {
            return false;
        }
        if (!isCircuitBreakerEnabled()) {
            DeviceConnectionCircuitState ctx = circuits.computeIfAbsent(
                    deviceId, k -> new DeviceConnectionCircuitState());
            ctx.consecutiveFailures.incrementAndGet();
            updateCacheStatus(
                    deviceId,
                    DeviceConnectionStatus.DISCONNECTED,
                    StringUtils.hasText(reason) ? reason : "连接失败");
            return false;
        }
        DeviceConnectionCircuitState ctx = circuits.computeIfAbsent(
                deviceId, k -> new DeviceConnectionCircuitState());
        synchronized (ctx.lock) {
            if (ctx.phase == DeviceConnectionCircuitState.Phase.ABANDONED) {
                return true;
            }
            ctx.lastProtocol = protocol;
            ctx.lastReason = reason;
            if (ctx.phase == DeviceConnectionCircuitState.Phase.HALF_OPEN) {
                int failures = ctx.consecutiveFailures.incrementAndGet();
                openCircuitLocked(deviceId, ctx, failures, true);
                return true;
            }
            int failures = ctx.consecutiveFailures.incrementAndGet();
            if (failures >= maxConsecutiveFailures) {
                openCircuitLocked(deviceId, ctx, failures, false);
                return true;
            }
            updateCacheStatus(
                    deviceId,
                    DeviceConnectionStatus.DISCONNECTED,
                    formatRetryFailureMessage(reason, failures));
            return false;
        }
    }

    /**
     * 采集超时离线（由 {@link DeviceOfflineWatcher} 检测后调用，统一写入 Redis 状态）。
     */
    public void onCollectTimeout(String deviceId, LocalDateTime expectedLastUpdate) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        DeviceRealtimeObj current = realtimeCache.getByDeviceId(deviceId);
        if (current == null || !current.isOnline()) {
            return;
        }
        if (DeviceRealtimePointSupport.isOfflineWithUnreadPoints(current)) {
            return;
        }
        LocalDateTime lastUpdate = current.getLastUpdateTime();
        if (lastUpdate == null || lastUpdate.isAfter(expectedLastUpdate)) {
            return;
        }
        DeviceRealtimeObj previous = snapshotBeforeStateChange(current);
        current.setOnline(false);
        current.setOfflineReason(DeviceOfflineReason.TIMEOUT);
        current.setMessage("采集超时，设备离线");
        DeviceRealtimePointSupport.clearValuesAsUnread(current);
        current.setLastUpdateTime(LocalDateTime.now());
        boolean saved = realtimeCache.saveIfOnlineStateMatches(
                current, previous, true, expectedLastUpdate, false);
        if (saved) {
            log.info("设备采集超时判离线 deviceId={}, expectedLastUpdate={}", deviceId, expectedLastUpdate);
        } else {
            log.debug("设备采集超时离线 CAS 未命中（可能已被熔断/恢复更新） deviceId={}", deviceId);
        }
    }

    public void reset(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        DeviceConnectionCircuitState ctx = circuits.remove(deviceId);
        if (ctx != null) {
            synchronized (ctx.lock) {
                cancelRecoveryTask(ctx);
                ctx.phase = DeviceConnectionCircuitState.Phase.CLOSED;
                ctx.consecutiveFailures.set(0);
                ctx.openCount.set(0);
                ctx.pollConfirmed = false;
            }
        }
    }

    private void openCircuitLocked(
            String deviceId,
            DeviceConnectionCircuitState ctx,
            int failureCount,
            boolean fromHalfOpen
    ) {
        cancelRecoveryTask(ctx);
        ctx.phase = DeviceConnectionCircuitState.Phase.OPEN;
        int openAttempt = ctx.openCount.incrementAndGet();
        if (shouldAbandon(openAttempt)) {
            abandonCircuitLocked(deviceId, ctx, fromHalfOpen);
            return;
        }
        long delayMs = computeBackoffMs(openAttempt);
        ctx.nextRetryAtEpochMs = System.currentTimeMillis() + delayMs;
        ctx.consecutiveFailures.set(0);

        offlineWatcher.ifAvailable(w -> w.cancelWatch(deviceId));

        String retryAt = RETRY_TIME_FORMAT.format(Instant.ofEpochMilli(ctx.nextRetryAtEpochMs));
        String message = fromHalfOpen
                ? "半开探测失败，" + (delayMs / 1000) + "秒后重试（约 " + retryAt + "）"
                : "连续" + failureCount + "次连接失败，" + (delayMs / 1000) + "秒后半开重试（约 " + retryAt + "）";
        updateCacheStatus(deviceId, DeviceConnectionStatus.DISCONNECTED, message);

        ctx.recoveryTask = recoveryScheduler.schedule(
                () -> triggerHalfOpenRecovery(deviceId),
                delayMs,
                TimeUnit.MILLISECONDS
        );

        log.warn("设备连接熔断打开 deviceId={}, failures={}, fromHalfOpen={}, openCount={}, backoffMs={}, retryAt={}",
                deviceId, failureCount, fromHalfOpen, openAttempt, delayMs, retryAt);
    }

    private void abandonCircuitLocked(
            String deviceId,
            DeviceConnectionCircuitState ctx,
            boolean fromHalfOpen
    ) {
        cancelRecoveryTask(ctx);
        ctx.phase = DeviceConnectionCircuitState.Phase.ABANDONED;
        ctx.nextRetryAtEpochMs = 0;
        ctx.consecutiveFailures.set(0);
        offlineWatcher.ifAvailable(w -> w.cancelWatch(deviceId));
        String message = "自动恢复已放弃（已重试 " + ctx.openCount.get() + " 次），请检查设备硬件或网络后手动「开启连接」";
        updateCacheStatus(deviceId, DeviceConnectionStatus.CIRCUIT_ABANDONED, message);
        log.warn("设备连接熔断放弃 deviceId={}, openCount={}, fromHalfOpen={}",
                deviceId, ctx.openCount.get(), fromHalfOpen);
    }

    private boolean shouldAbandon(int openCount) {
        return openCount > maxOpenRetries;
    }

    private void closeCircuitLocked(DeviceConnectionCircuitState ctx) {
        cancelRecoveryTask(ctx);
        ctx.phase = DeviceConnectionCircuitState.Phase.CLOSED;
        ctx.consecutiveFailures.set(0);
        ctx.openCount.set(0);
        ctx.nextRetryAtEpochMs = 0;
        ctx.pollConfirmed = false;
    }

    private void triggerHalfOpenRecovery(String deviceId) {
        DeviceConnectionCircuitState ctx = circuits.get(deviceId);
        if (ctx == null || ctx.phase == DeviceConnectionCircuitState.Phase.ABANDONED) {
            log.debug("半开恢复跳过：已放弃 deviceId={}", deviceId);
            return;
        }
        if (ctx.phase != DeviceConnectionCircuitState.Phase.OPEN) {
            log.debug("半开恢复跳过：非 OPEN 状态 deviceId={}", deviceId);
            return;
        }
        DeviceLifecycleManager lifecycle = lifecycleManager.getIfAvailable();
        if (lifecycle == null) {
            log.warn("半开恢复跳过：DeviceLifecycleManager 不可用 deviceId={}", deviceId);
            scheduleRecoveryRetry(deviceId, ctx);
            return;
        }
        try {
            lifecycle.attemptConnectionRecovery(deviceId);
        } catch (Exception e) {
            log.error("半开恢复执行失败 deviceId={}: {}", deviceId, e.getMessage(), e);
            DeviceConnectionCircuitState failed = circuits.get(deviceId);
            if (failed != null) {
                synchronized (failed.lock) {
                    scheduleRecoveryRetryLocked(deviceId, failed);
                }
            }
        }
    }

    private void scheduleRecoveryRetry(String deviceId, DeviceConnectionCircuitState ctx) {
        synchronized (ctx.lock) {
            scheduleRecoveryRetryLocked(deviceId, ctx);
        }
    }

    private void scheduleRecoveryRetryLocked(String deviceId, DeviceConnectionCircuitState ctx) {
        if (ctx.phase != DeviceConnectionCircuitState.Phase.OPEN) {
            return;
        }
        cancelRecoveryTask(ctx);
        int openAttempt = ctx.openCount.incrementAndGet();
        if (shouldAbandon(openAttempt)) {
            abandonCircuitLocked(deviceId, ctx, true);
            return;
        }
        long delayMs = computeBackoffMs(openAttempt);
        ctx.nextRetryAtEpochMs = System.currentTimeMillis() + delayMs;
        ctx.recoveryTask = recoveryScheduler.schedule(
                () -> triggerHalfOpenRecovery(deviceId),
                delayMs,
                TimeUnit.MILLISECONDS
        );
        log.warn("半开恢复将重试 deviceId={}, openCount={}, backoffMs={}", deviceId, openAttempt, delayMs);
    }

    /**
     * 进入半开：由生命周期管理器重启轮询前调用。
     */
    public void enterHalfOpen(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return;
        }
        DeviceConnectionCircuitState ctx = circuits.computeIfAbsent(
                deviceId, k -> new DeviceConnectionCircuitState());
        synchronized (ctx.lock) {
            cancelRecoveryTask(ctx);
            ctx.phase = DeviceConnectionCircuitState.Phase.HALF_OPEN;
            ctx.consecutiveFailures.set(0);
        }
        updateCacheStatus(deviceId, DeviceConnectionStatus.HALF_OPEN, "半开恢复探测中");
        log.info("设备连接进入半开恢复 deviceId={}", deviceId);
    }

    private long computeEffectiveBackoffMaxMs(int openCount) {
        double scale = 1.0 + Math.min(openCount, backoffMaxScaleSteps) * backoffMaxScaleFactor;
        return (long) (backoffMaxMs * scale);
    }

    private long computeBackoffMs(int openCount) {
        int exponent = Math.max(0, openCount - 1);
        double delay = backoffInitialMs * Math.pow(backoffMultiplier, exponent);
        long cap = computeEffectiveBackoffMaxMs(openCount);
        return (long) Math.min(cap, Math.max(backoffInitialMs, delay));
    }

    private void cancelRecoveryTask(DeviceConnectionCircuitState ctx) {
        ScheduledFuture<?> task = ctx.recoveryTask;
        if (task != null) {
            task.cancel(false);
            ctx.recoveryTask = null;
        }
    }

    private static String formatRetryFailureMessage(String reason, int failures) {
        String detail = StringUtils.hasText(reason) ? reason : "连接失败";
        return detail + "（重试中 " + failures + "）";
    }

    private void updateCacheStatus(String deviceId, DeviceConnectionStatus status, String message) {
        DeviceRealtimeObj cached = realtimeCache.getByDeviceId(deviceId);
        if (cached == null) {
            return;
        }
        DeviceRealtimeObj previous = snapshotBeforeStateChange(cached);
        cached.setConnectionStatus(status);
        cached.setConnectionStatusLabel(status.getLabel());
        cached.setConnectionStatusMessage(message);
        cached.setLastUpdateTime(LocalDateTime.now());

        if (status == DeviceConnectionStatus.CONNECTED) {
            cached.setOnline(true);
            cached.setOfflineReason(null);
            cached.setMessage(null);
            boolean saved = realtimeCache.saveIfOnlineStateMatches(
                    cached, previous, false, null, true);
            if (!saved) {
                // 半开恢复等场景：本轮 poll 已 publish online=true，离线→在线 CAS 会未命中，仍需写回连接说明
                realtimeCache.save(cached);
                log.debug("连接恢复写在线 CAS 未命中，已直写 deviceId={}", deviceId);
            }
        } else if (status == DeviceConnectionStatus.DISCONNECTED
                || status == DeviceConnectionStatus.CIRCUIT_ABANDONED) {
            if (DeviceRealtimePointSupport.isOfflineWithUnreadPoints(cached)) {
                cached.setConnectionStatus(status);
                cached.setConnectionStatusLabel(status.getLabel());
                cached.setConnectionStatusMessage(message);
                realtimeCache.save(cached);
                return;
            }
            cached.setOnline(false);
            cached.setOfflineReason(status == DeviceConnectionStatus.CIRCUIT_ABANDONED
                    ? DeviceOfflineReason.CIRCUIT_ABANDONED
                    : DeviceOfflineReason.CIRCUIT_BREAKER);
            cached.setMessage(message);
            DeviceRealtimePointSupport.clearValuesAsUnread(cached);
            boolean saved = realtimeCache.saveIfOnlineStateMatches(
                    cached, previous, true, null, false);
            if (!saved) {
                log.debug("熔断写离线 CAS 未命中 deviceId={}", deviceId);
            }
        } else {
            realtimeCache.save(cached);
        }
    }

    private static DeviceRealtimeObj snapshotBeforeStateChange(DeviceRealtimeObj from) {
        DeviceRealtimeObj previous = new DeviceRealtimeObj();
        previous.setDeviceId(from.getDeviceId());
        previous.setDeviceCode(from.getDeviceCode());
        previous.setEventId(from.getEventId());
        previous.setOnline(from.isOnline());
        previous.setOfflineReason(from.getOfflineReason());
        previous.setLastUpdateTime(from.getLastUpdateTime());
        if (from.getPoints() != null && !from.getPoints().isEmpty()) {
            previous.setPoints(new java.util.LinkedHashMap<>(from.getPoints()));
        }
        return previous;
    }

}
