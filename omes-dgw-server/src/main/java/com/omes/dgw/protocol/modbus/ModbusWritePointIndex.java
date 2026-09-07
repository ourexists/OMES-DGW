package com.omes.dgw.protocol.modbus;

import com.omes.dgw.device.entity.DevicePoint;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/** 写入时按规范化地址 O(1) 查找 {@link DevicePoint}。 */
public final class ModbusWritePointIndex {

    private final Map<String, DevicePoint> byKey;

    private ModbusWritePointIndex(Map<String, DevicePoint> byKey) {
        this.byKey = byKey;
    }

    public static ModbusWritePointIndex build(Map<String, DevicePoint> writablePointsByName) {
        Map<String, DevicePoint> index = new HashMap<>();
        if (writablePointsByName != null) {
            for (DevicePoint point : writablePointsByName.values()) {
                if (point != null && StringUtils.hasText(point.getSetpointAddress())) {
                    registerKeys(index, point.getSetpointAddress(), point);
                }
            }
        }
        return new ModbusWritePointIndex(Map.copyOf(index));
    }

    public DevicePoint resolve(String address) {
        if (!StringUtils.hasText(address) || byKey.isEmpty()) {
            return null;
        }
        String requested = address.trim();
        String legacy = ModbusAddressHelper.convertLegacyAddress(requested);
        String stripped = ModbusAddressHelper.stripDataTypeSuffix(legacy);
        DevicePoint point = byKey.get(stripped.toLowerCase());
        if (point != null) {
            return point;
        }
        point = byKey.get(legacy.toLowerCase());
        if (point != null) {
            return point;
        }
        return byKey.get(requested.toLowerCase());
    }

    private static void registerKeys(Map<String, DevicePoint> index, String address, DevicePoint point) {
        String requested = address.trim();
        String legacy = ModbusAddressHelper.convertLegacyAddress(requested);
        String stripped = ModbusAddressHelper.stripDataTypeSuffix(legacy);
        index.putIfAbsent(stripped.toLowerCase(), point);
        index.putIfAbsent(legacy.toLowerCase(), point);
        index.putIfAbsent(requested.toLowerCase(), point);
    }
}
