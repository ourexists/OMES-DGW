package com.omes.dgw.collectlog;

import com.omes.dgw.device.cache.DevicePointRealtimeObj;
import com.omes.dgw.device.cache.DeviceRealtimePointSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 检测点位「有值 → 无值/未读」变更并输出结构化日志。
 */
@Slf4j
public final class PointValueChangeDetector {

    private PointValueChangeDetector() {
    }

    public static void logValueLost(
            boolean enabled,
            String deviceId,
            String protocol,
            Map<String, DevicePointRealtimeObj> previousPoints,
            Map<String, DevicePointRealtimeObj> currentPoints
    ) {
        if (!enabled || !StringUtils.hasText(deviceId) || currentPoints == null) {
            return;
        }
        Map<String, DevicePointRealtimeObj> prev = previousPoints != null ? previousPoints : Map.of();
        for (Map.Entry<String, DevicePointRealtimeObj> entry : currentPoints.entrySet()) {
            String pointName = entry.getKey();
            DevicePointRealtimeObj now = entry.getValue();
            DevicePointRealtimeObj before = prev.get(pointName);
            if (!hadReadableValue(before) || hasReadableValue(now)) {
                continue;
            }
            String prevVal = formatValue(before);
            String reason = now != null && StringUtils.hasText(now.getError())
                    ? now.getError()
                    : DeviceRealtimePointSupport.UNREAD_ERROR;
            log.warn(StructuredCollectLog.format(
                    "event=point_value_lost",
                    "deviceId", deviceId,
                    "protocol", protocol,
                    "point", pointName,
                    "address", now != null ? now.getAddress() : (before != null ? before.getAddress() : null),
                    "previousValue", prevVal,
                    "reason", reason
            ));
        }
    }

    private static boolean hadReadableValue(DevicePointRealtimeObj point) {
        return point != null && point.getValue() != null && !StringUtils.hasText(point.getError());
    }

    private static boolean hasReadableValue(DevicePointRealtimeObj point) {
        return point != null && point.getValue() != null && !StringUtils.hasText(point.getError());
    }

    private static String formatValue(DevicePointRealtimeObj point) {
        if (point == null) {
            return "null";
        }
        Object v = point.getValue() != null ? point.getValue() : point.getRawValue();
        return v != null ? String.valueOf(v) : "null";
    }
}
