package com.omes.dgw.device.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.device.controller.core.DeviceOfflineWatcher;
import com.omes.dgw.device.online.DeviceOnlineEventCollector;
import com.omes.dgw.metrics.CollectDeviceOfflineMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 设备实时缓存门面：副作用（history / online / metrics / offlineWatch）+ 委托 {@link DeviceRealtimeCacheStore}。
 */
@Slf4j
@Service
public class DeviceRealtimeCache {

    /** {@link #saveIfOnlineStateMatches}：写入成功（兼容原 Lua 返回值语义） */
    public static final long CAS_SAVE_OK = 1L;
    /** {@link #saveIfOnlineStateMatches}：当前 online/lastUpdate 与预期不一致 */
    public static final long CAS_SAVE_MISMATCH = 0L;
    /** {@link #saveIfOnlineStateMatches}：设备键不存在 */
    public static final long CAS_SAVE_NOT_FOUND = -1L;

    private final DeviceRealtimeCacheStore store;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<DeviceRealtimeHistoryConsumer> historyConsumer;
    private final ObjectProvider<DeviceOnlineEventCollector> onlineEventCollector;
    private final ObjectProvider<DeviceOfflineWatcher> offlineWatchService;
    private final DeviceRealtimeCacheMetrics cacheMetrics;
    private final CollectDeviceOfflineMetrics deviceOfflineMetrics;

    public DeviceRealtimeCache(
            DeviceRealtimeCacheStore store,
            ObjectMapper objectMapper,
            DeviceRealtimeCacheMetrics cacheMetrics,
            CollectDeviceOfflineMetrics deviceOfflineMetrics,
            ObjectProvider<DeviceRealtimeHistoryConsumer> historyConsumer,
            ObjectProvider<DeviceOnlineEventCollector> onlineEventCollector,
            @Lazy ObjectProvider<DeviceOfflineWatcher> offlineWatchService
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.cacheMetrics = cacheMetrics;
        this.deviceOfflineMetrics = deviceOfflineMetrics;
        this.historyConsumer = historyConsumer;
        this.onlineEventCollector = onlineEventCollector;
        this.offlineWatchService = offlineWatchService;
        log.info("设备实时缓存门面已初始化 store={}", store.getClass().getSimpleName());
    }

    public void save(DeviceRealtimeObj snapshot) {
        persist(snapshot, null, true);
    }

    /**
     * 保存实时快照；传入上一轮缓存可避免 persist 前再次读取缓存（轮询路径使用）。
     */
    public void save(DeviceRealtimeObj snapshot, DeviceRealtimeObj previous) {
        persist(snapshot, previous, true);
    }

    /**
     * 条件写入实时缓存：仅当存储中当前 online（及可选 lastUpdateTime）与预期一致时写入。
     *
     * @param expectCurrentOnline 写入前缓存中应为的 online 值
     * @param expectLastUpdate    写入前缓存中应为的 lastUpdateTime；null 表示不校验
     * @return true 表示 CAS 成功并已写入
     */
    public boolean saveIfOnlineStateMatches(
            DeviceRealtimeObj snapshot,
            DeviceRealtimeObj previous,
            boolean expectCurrentOnline,
            LocalDateTime expectLastUpdate,
            boolean scheduleOfflineWatch
    ) {
        if (snapshot == null || !StringUtils.hasText(snapshot.getDeviceId())) {
            return false;
        }
        try {
            DeviceRealtimeObj prev = previous != null ? previous : getByDeviceId(snapshot.getDeviceId());
            onlineEventCollector.ifAvailable(collector -> collector.beforePersist(prev, snapshot));

            String json = objectMapper.writeValueAsString(snapshot);
            long casResult = store.saveIfOnlineStateMatches(
                    snapshot, json, expectCurrentOnline, expectLastUpdate);
            if (casResult == CAS_SAVE_MISMATCH) {
                log.debug("实时缓存 CAS 未命中 deviceId={}, expectOnline={}, expectLastUpdate={}",
                        snapshot.getDeviceId(), expectCurrentOnline, expectLastUpdate);
                return false;
            }
            if (casResult == CAS_SAVE_NOT_FOUND) {
                log.debug("实时缓存 CAS 跳过：设备不存在 deviceId={}", snapshot.getDeviceId());
                return false;
            }
            historyConsumer.ifAvailable(consumer -> consumer.onSnapshotSaved(snapshot, json));
            if (scheduleOfflineWatch) {
                offlineWatchService.ifAvailable(watcher -> watcher.watch(snapshot));
            }
            cacheMetrics.recordSuccess();
            deviceOfflineMetrics.observe(
                    snapshot.getDeviceId(), snapshot.isOnline(), snapshot.getLastUpdateTime());
            log.debug("实时缓存 CAS 已写入 deviceId={}, online={}, offlineReason={}",
                    snapshot.getDeviceId(), snapshot.isOnline(), snapshot.getOfflineReason());
            return true;
        } catch (JsonProcessingException e) {
            cacheMetrics.recordSerializationError();
            log.error("序列化设备实时缓存失败 deviceId={}", snapshot.getDeviceId(), e);
            throw new RealtimeCacheSerializationException("序列化设备实时缓存失败", e);
        } catch (RealtimeCacheInfrastructureException e) {
            cacheMetrics.recordDegraded();
            log.warn("实时缓存基础设施不可用（CAS 降级） deviceId={}: {}",
                    snapshot.getDeviceId(), e.getMessage());
            return false;
        } catch (Exception e) {
            if (isInfrastructureFailure(e)) {
                cacheMetrics.recordDegraded();
                log.warn("实时缓存基础设施不可用（CAS 降级） deviceId={}: {}",
                        snapshot.getDeviceId(), e.getMessage());
                return false;
            }
            cacheMetrics.recordError();
            log.error("CAS 写入实时缓存失败 deviceId={}", snapshot.getDeviceId(), e);
            throw e;
        }
    }

    private void persist(DeviceRealtimeObj snapshot, DeviceRealtimeObj previous, boolean scheduleOfflineWatch) {
        if (snapshot == null || !StringUtils.hasText(snapshot.getDeviceId())) {
            log.warn("跳过保存实时缓存: snapshot 或 deviceId 为空");
            return;
        }
        try {
            DeviceRealtimeObj prev = previous != null ? previous : getByDeviceId(snapshot.getDeviceId());
            onlineEventCollector.ifAvailable(collector -> collector.beforePersist(prev, snapshot));

            String json = objectMapper.writeValueAsString(snapshot);
            store.save(snapshot, json);
            historyConsumer.ifAvailable(consumer -> consumer.onSnapshotSaved(snapshot, json));
            if (scheduleOfflineWatch) {
                offlineWatchService.ifAvailable(watcher -> watcher.watch(snapshot));
            }
            cacheMetrics.recordSuccess();
            deviceOfflineMetrics.observe(
                    snapshot.getDeviceId(), snapshot.isOnline(), snapshot.getLastUpdateTime());
            log.debug("实时缓存已写入 deviceId={}, eventId={}, code={}, online={}, pointCount={}",
                    snapshot.getDeviceId(),
                    snapshot.getEventId(),
                    snapshot.getDeviceCode(),
                    snapshot.isOnline(),
                    snapshot.getPoints() != null ? snapshot.getPoints().size() : 0);
        } catch (JsonProcessingException e) {
            cacheMetrics.recordSerializationError();
            log.error("序列化设备实时缓存失败 deviceId={}", snapshot.getDeviceId(), e);
            throw new RealtimeCacheSerializationException("序列化设备实时缓存失败", e);
        } catch (RealtimeCacheInfrastructureException e) {
            cacheMetrics.recordDegraded();
            log.warn("实时缓存基础设施不可用（写入降级） deviceId={}: {}",
                    snapshot.getDeviceId(), e.getMessage());
        } catch (Exception e) {
            if (isInfrastructureFailure(e)) {
                cacheMetrics.recordDegraded();
                log.warn("实时缓存基础设施不可用（写入降级） deviceId={}: {}",
                        snapshot.getDeviceId(), e.getMessage());
                return;
            }
            cacheMetrics.recordError();
            log.error("写入实时缓存失败 deviceId={}", snapshot.getDeviceId(), e);
            throw e;
        }
    }

    public DeviceRealtimeObj getByDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        try {
            DeviceRealtimeObj vo = store.getByDeviceId(deviceId);
            log.debug("按 ID 查询实时缓存 deviceId={}, hit={}", deviceId, vo != null);
            return vo;
        } catch (RealtimeCacheInfrastructureException e) {
            cacheMetrics.recordDegraded();
            log.warn("实时缓存基础设施不可用（查询降级） deviceId={}: {}", deviceId, e.getMessage());
            return null;
        } catch (Exception e) {
            if (isInfrastructureFailure(e)) {
                cacheMetrics.recordDegraded();
                log.warn("实时缓存基础设施不可用（查询降级） deviceId={}: {}", deviceId, e.getMessage());
                return null;
            }
            throw e;
        }
    }

    public DeviceRealtimeObj getByDeviceCode(String deviceCode) {
        if (!StringUtils.hasText(deviceCode)) {
            return null;
        }
        try {
            DeviceRealtimeObj vo = store.getByDeviceCode(deviceCode);
            log.debug("按编码查询实时缓存 deviceCode={}, hit={}", deviceCode, vo != null);
            return vo;
        } catch (RealtimeCacheInfrastructureException e) {
            cacheMetrics.recordDegraded();
            log.warn("实时缓存基础设施不可用（按编码查询降级） deviceCode={}: {}",
                    deviceCode, e.getMessage());
            return null;
        } catch (Exception e) {
            if (isInfrastructureFailure(e)) {
                cacheMetrics.recordDegraded();
                log.warn("实时缓存基础设施不可用（按编码查询降级） deviceCode={}: {}",
                        deviceCode, e.getMessage());
                return null;
            }
            throw e;
        }
    }

    public List<DeviceRealtimeObj> listAll() {
        try {
            List<DeviceRealtimeObj> list = store.listAll();
            log.debug("查询全部实时缓存: count={}", list.size());
            return list;
        } catch (RealtimeCacheInfrastructureException e) {
            cacheMetrics.recordDegraded();
            log.warn("实时缓存基础设施不可用（listAll 降级）: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            if (isInfrastructureFailure(e)) {
                cacheMetrics.recordDegraded();
                log.warn("实时缓存基础设施不可用（listAll 降级）: {}", e.getMessage());
                return Collections.emptyList();
            }
            throw e;
        }
    }

    public void remove(String deviceId, String deviceCode) {
        boolean hasDevice = StringUtils.hasText(deviceId);
        boolean hasCode = StringUtils.hasText(deviceCode);
        if (!hasDevice && !hasCode) {
            return;
        }
        try {
            store.remove(deviceId, deviceCode);
            log.info("已删除实时缓存 deviceId={}, deviceCode={}", deviceId, deviceCode);
        } catch (RealtimeCacheInfrastructureException e) {
            cacheMetrics.recordDegraded();
            log.warn("实时缓存基础设施不可用（删除降级） deviceId={}, deviceCode={}: {}",
                    deviceId, deviceCode, e.getMessage());
        } catch (Exception e) {
            if (isInfrastructureFailure(e)) {
                cacheMetrics.recordDegraded();
                log.warn("实时缓存基础设施不可用（删除降级） deviceId={}, deviceCode={}: {}",
                        deviceId, deviceCode, e.getMessage());
                return;
            }
            throw e;
        }
    }

    private static boolean isInfrastructureFailure(Throwable error) {
        Throwable cur = error;
        while (cur != null) {
            if (cur instanceof RealtimeCacheInfrastructureException) {
                return true;
            }
            String name = cur.getClass().getName();
            if (name.contains("RedisConnectionFailure")
                    || name.contains("RedisSystemException")
                    || name.contains("QueryTimeoutException")
                    || name.contains("RedisCommandTimeout")
                    || name.contains("RedisConnectionException")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
