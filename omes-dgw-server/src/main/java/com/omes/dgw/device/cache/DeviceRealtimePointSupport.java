package com.omes.dgw.device.cache;

import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.support.DevicePointConfigSupport;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备实时缓存中点位值的统一处理。
 */
public final class DeviceRealtimePointSupport {

    public static final String UNREAD_ERROR = "未读取到数据";

    private DeviceRealtimePointSupport() {
    }

    /**
     * 将配置内全部点位置为未读取：清空 raw/value，并设置 {@link #UNREAD_ERROR}。
     */
    public static void clearValuesAsUnread(DeviceRealtimeObj snapshot) {
        if (snapshot == null) {
            return;
        }
        Map<String, DevicePoint> pointsByName = DevicePointConfigSupport.toPointsByName(snapshot);
        LocalDateTime now = LocalDateTime.now();
        Map<String, DevicePointRealtimeObj> cleared = new LinkedHashMap<>();
        for (Map.Entry<String, DevicePoint> entry : pointsByName.entrySet()) {
            String key = entry.getKey();
            DevicePoint point = entry.getValue();
            DevicePointRealtimeObj existing = lookupRealtime(snapshot.getPoints(), key, point.getName());
            DevicePointRealtimeObj pv = new DevicePointRealtimeObj();
            pv.setCode(point.getCode());
            pv.setName(point.getName());
            pv.setAddress(point.getAddress());
            pv.setUnit(point.getEngUnit() != null ? point.getEngUnit()
                    : existing != null ? existing.getUnit() : null);
            pv.setUpdateTime(now);
            pv.setRawValue(null);
            pv.setValue(null);
            pv.setError(UNREAD_ERROR);
            cleared.put(key, pv);
        }
        snapshot.setPoints(cleared);
    }

    /**
     * 设备已离线且各配置点位均已标记为未读取（用于避免重复写 Redis）。
     */
    public static boolean isOfflineWithUnreadPoints(DeviceRealtimeObj snapshot) {
        if (snapshot == null || snapshot.isOnline()) {
            return false;
        }
        Map<String, DevicePoint> config = DevicePointConfigSupport.toPointsByName(snapshot);
        if (config.isEmpty()) {
            return snapshot.getPoints() == null || snapshot.getPoints().isEmpty();
        }
        Map<String, DevicePointRealtimeObj> points = snapshot.getPoints();
        if (points == null || points.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, DevicePoint> entry : config.entrySet()) {
            DevicePointRealtimeObj pv = lookupRealtime(points, entry.getKey(), entry.getValue().getName());
            if (pv == null || pv.getRawValue() != null || pv.getValue() != null
                    || !UNREAD_ERROR.equals(pv.getError())) {
                return false;
            }
        }
        return true;
    }

    public static DevicePointRealtimeObj lookupRealtime(
            Map<String, DevicePointRealtimeObj> points,
            String mapKey,
            String name
    ) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (mapKey != null && !mapKey.isBlank()) {
            DevicePointRealtimeObj byKey = points.get(mapKey);
            if (byKey != null) {
                return byKey;
            }
        }
        if (name != null && !name.isBlank() && !name.equals(mapKey)) {
            return points.get(name);
        }
        return null;
    }
}
