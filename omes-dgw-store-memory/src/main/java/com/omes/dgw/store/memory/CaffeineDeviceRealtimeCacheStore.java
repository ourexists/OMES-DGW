package com.omes.dgw.store.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeCacheCopies;
import com.omes.dgw.device.cache.DeviceRealtimeCacheStore;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-process Caffeine realtime snapshot store.
 */
@Slf4j
public class CaffeineDeviceRealtimeCacheStore implements DeviceRealtimeCacheStore {

    private final Cache<String, DeviceRealtimeObj> byDeviceId = Caffeine.newBuilder().build();
    private final ConcurrentHashMap<String, String> codeToDeviceId = new ConcurrentHashMap<>();

    public CaffeineDeviceRealtimeCacheStore() {
        log.info("Device realtime cache backend (Caffeine) initialized");
    }

    @Override
    public void save(DeviceRealtimeObj snapshot, String json) {
        putSnapshot(snapshot);
    }

    @Override
    public long saveIfOnlineStateMatches(
            DeviceRealtimeObj snapshot,
            String json,
            boolean expectCurrentOnline,
            LocalDateTime expectLastUpdate
    ) {
        return casPut(snapshot, expectCurrentOnline, expectLastUpdate);
    }

    @Override
    public DeviceRealtimeObj getByDeviceId(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        DeviceRealtimeObj cached = byDeviceId.getIfPresent(deviceId);
        return cached == null ? null : DeviceRealtimeCacheCopies.copyOf(cached);
    }

    @Override
    public DeviceRealtimeObj getByDeviceCode(String deviceCode) {
        if (!StringUtils.hasText(deviceCode)) {
            return null;
        }
        String deviceId = codeToDeviceId.get(deviceCode);
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        return getByDeviceId(deviceId);
    }

    @Override
    public List<DeviceRealtimeObj> listAll() {
        Map<String, DeviceRealtimeObj> view = byDeviceId.asMap();
        if (view.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeviceRealtimeObj> list = new ArrayList<>(view.size());
        for (DeviceRealtimeObj cached : view.values()) {
            if (cached != null) {
                list.add(DeviceRealtimeCacheCopies.copyOf(cached));
            }
        }
        return list;
    }

    @Override
    public void remove(String deviceId, String deviceCode) {
        boolean hasDevice = StringUtils.hasText(deviceId);
        boolean hasCode = StringUtils.hasText(deviceCode);
        if (!hasDevice && !hasCode) {
            return;
        }
        if (hasDevice) {
            byDeviceId.invalidate(deviceId);
        }
        if (hasCode) {
            codeToDeviceId.remove(deviceCode);
        }
    }

    private void putSnapshot(DeviceRealtimeObj snapshot) {
        String deviceId = snapshot.getDeviceId();
        DeviceRealtimeObj stored = DeviceRealtimeCacheCopies.copyOf(snapshot);
        AtomicReference<String> previousCode = new AtomicReference<>();
        byDeviceId.asMap().compute(deviceId, (id, previous) -> {
            if (previous != null && StringUtils.hasText(previous.getDeviceCode())) {
                previousCode.set(previous.getDeviceCode());
            }
            return stored;
        });
        indexDeviceCode(deviceId, snapshot.getDeviceCode(), previousCode.get());
    }

    private long casPut(
            DeviceRealtimeObj snapshot,
            boolean expectCurrentOnline,
            LocalDateTime expectLastUpdate
    ) {
        String deviceId = snapshot.getDeviceId();
        DeviceRealtimeObj stored = DeviceRealtimeCacheCopies.copyOf(snapshot);
        AtomicBoolean notFound = new AtomicBoolean(false);
        AtomicBoolean mismatch = new AtomicBoolean(false);
        AtomicReference<String> previousCode = new AtomicReference<>();

        byDeviceId.asMap().compute(deviceId, (id, current) -> {
            if (current == null) {
                notFound.set(true);
                return null;
            }
            if (current.isOnline() != expectCurrentOnline) {
                mismatch.set(true);
                return current;
            }
            if (expectLastUpdate != null
                    && !Objects.equals(current.getLastUpdateTime(), expectLastUpdate)) {
                mismatch.set(true);
                return current;
            }
            if (StringUtils.hasText(current.getDeviceCode())) {
                previousCode.set(current.getDeviceCode());
            }
            return stored;
        });

        if (notFound.get()) {
            return DeviceRealtimeCache.CAS_SAVE_NOT_FOUND;
        }
        if (mismatch.get()) {
            return DeviceRealtimeCache.CAS_SAVE_MISMATCH;
        }
        indexDeviceCode(deviceId, snapshot.getDeviceCode(), previousCode.get());
        return DeviceRealtimeCache.CAS_SAVE_OK;
    }

    private void indexDeviceCode(String deviceId, String newCode, String previousCode) {
        if (StringUtils.hasText(previousCode)
                && (!StringUtils.hasText(newCode) || !previousCode.equals(newCode))) {
            codeToDeviceId.remove(previousCode, deviceId);
        }
        if (StringUtils.hasText(newCode)) {
            codeToDeviceId.put(newCode, deviceId);
        }
    }
}
