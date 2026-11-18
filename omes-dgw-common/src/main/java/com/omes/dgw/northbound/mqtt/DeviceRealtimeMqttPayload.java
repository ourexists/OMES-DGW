package com.omes.dgw.northbound.mqtt;

import com.omes.dgw.device.cache.DevicePointRealtimeObj;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.enums.DeviceOfflineReason;
import com.omes.dgw.device.enums.DeviceProtocol;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT推送消息体：单设备一条，不含采集配置等大字段。
 */
@Data
public class DeviceRealtimeMqttPayload {

    private String deviceId;
    private String deviceCode;
    private String deviceName;
    private DeviceProtocol protocol;
    private boolean online;
    private DeviceOfflineReason offlineReason;
    private LocalDateTime lastUpdateTime;
    private String message;
    private Map<String, PointPayload> points = new LinkedHashMap<>();

    public static DeviceRealtimeMqttPayload from(DeviceRealtimeObj snapshot) {
        DeviceRealtimeMqttPayload payload = new DeviceRealtimeMqttPayload();
        if (snapshot == null) {
            return payload;
        }
        payload.setDeviceId(snapshot.getDeviceId());
        payload.setDeviceCode(snapshot.getDeviceCode());
        payload.setDeviceName(snapshot.getDeviceName());
        payload.setProtocol(snapshot.getProtocol());
        payload.setOnline(snapshot.isOnline());
        payload.setOfflineReason(snapshot.getOfflineReason());
        payload.setLastUpdateTime(snapshot.getLastUpdateTime());
        payload.setMessage(snapshot.getMessage());
        Map<String, DevicePointRealtimeObj> source = snapshot.getPoints();
        if (source != null && !source.isEmpty()) {
            Map<String, PointPayload> points = new LinkedHashMap<>(source.size());
            for (Map.Entry<String, DevicePointRealtimeObj> e : source.entrySet()) {
                DevicePointRealtimeObj p = e.getValue();
                if (p == null) {
                    continue;
                }
                PointPayload point = new PointPayload();
                point.setCode(p.getCode() != null ? p.getCode() : e.getKey());
                point.setName(p.getName());
                point.setValue(p.getValue() != null ? p.getValue() : p.getRawValue());
                point.setUnit(p.getUnit());
                point.setUpdateTime(p.getUpdateTime());
                point.setError(p.getError());
                points.put(e.getKey(), point);
            }
            payload.setPoints(points);
        }
        return payload;
    }

    @Data
    public static class PointPayload {
        private String code;
        private String name;
        private Object value;
        private String unit;
        private LocalDateTime updateTime;
        private String error;
    }
}
