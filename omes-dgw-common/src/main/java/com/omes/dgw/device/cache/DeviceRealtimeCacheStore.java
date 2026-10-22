package com.omes.dgw.device.cache;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备实时快照存储后端（Caffeine / Redis 等）。
 */
public interface DeviceRealtimeCacheStore {

    void save(DeviceRealtimeObj snapshot, String json);

    /**
     * @return {@link DeviceRealtimeCache#CAS_SAVE_OK} /
     * {@link DeviceRealtimeCache#CAS_SAVE_MISMATCH} /
     * {@link DeviceRealtimeCache#CAS_SAVE_NOT_FOUND}
     */
    long saveIfOnlineStateMatches(
            DeviceRealtimeObj snapshot,
            String json,
            boolean expectCurrentOnline,
            LocalDateTime expectLastUpdate
    );

    DeviceRealtimeObj getByDeviceId(String deviceId);

    DeviceRealtimeObj getByDeviceCode(String deviceCode);

    List<DeviceRealtimeObj> listAll();

    void remove(String deviceId, String deviceCode);
}
