package com.omes.dgw.device.cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时快照防御性拷贝，避免外部改 Map 污染缓存条目。
 */
public final class DeviceRealtimeCacheCopies {

    private DeviceRealtimeCacheCopies() {
    }

    public static DeviceRealtimeObj copyOf(DeviceRealtimeObj source) {
        if (source == null) {
            return null;
        }
        DeviceRealtimeObj copy = new DeviceRealtimeObj();
        copy.setDeviceId(source.getDeviceId());
        copy.setEventId(source.getEventId());
        copy.setDeviceCode(source.getDeviceCode());
        copy.setDeviceName(source.getDeviceName());
        copy.setProtocol(source.getProtocol());
        copy.setIpAddress(source.getIpAddress());
        copy.setPort(source.getPort());
        copy.setCollectIntervalSec(source.getCollectIntervalSec());
        copy.setConnectTimeoutMs(source.getConnectTimeoutMs());
        copy.setCollectConfig(source.getCollectConfig());
        copy.setOnline(source.isOnline());
        copy.setOfflineReason(source.getOfflineReason());
        copy.setConnectionStatus(source.getConnectionStatus());
        copy.setConnectionStatusLabel(source.getConnectionStatusLabel());
        copy.setConnectionStatusMessage(source.getConnectionStatusMessage());
        copy.setLastUpdateTime(source.getLastUpdateTime());
        copy.setMessage(source.getMessage());
        if (source.getPoints() != null) {
            Map<String, DevicePointRealtimeObj> points = new LinkedHashMap<>(source.getPoints().size());
            for (Map.Entry<String, DevicePointRealtimeObj> entry : source.getPoints().entrySet()) {
                points.put(entry.getKey(), copyPoint(entry.getValue()));
            }
            copy.setPoints(points);
        } else {
            copy.setPoints(new LinkedHashMap<>());
        }
        return copy;
    }

    private static DevicePointRealtimeObj copyPoint(DevicePointRealtimeObj source) {
        if (source == null) {
            return null;
        }
        DevicePointRealtimeObj copy = new DevicePointRealtimeObj();
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setAddress(source.getAddress());
        copy.setRawValue(source.getRawValue());
        copy.setValue(source.getValue());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setUnit(source.getUnit());
        copy.setError(source.getError());
        return copy;
    }
}
