package com.omes.dgw.store.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.omes.dgw.device.cache.DevicePointRealtimeObj;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.cache.PointValueScaler;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.timeseries.DeviceRealtimeHistoryWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "influxdb")
@ConditionalOnBean(InfluxDBClient.class)
public class DeviceRealtimeInfluxBatchWriter implements DeviceRealtimeHistoryWriter {

    private final InfluxConnectionService influx;
    private final WritePrecision writePrecision;

    public DeviceRealtimeInfluxBatchWriter(
            InfluxConnectionService influx,
            WritePrecision writePrecision
    ) {
        this.influx = influx;
        this.writePrecision = writePrecision;
    }

    @Override
    public void writeSnapshots(List<DeviceRealtimeObj> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        List<Point> deviceRecords = new ArrayList<>();
        List<Point> pointRecords = new ArrayList<>();
        int skippedPoints = 0;
        for (DeviceRealtimeObj snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            Point deviceRecord = toDevicePoint(snapshot);
            if (deviceRecord != null) {
                deviceRecords.add(deviceRecord);
            }
            int skipped = appendPointRecords(pointRecords, snapshot);
            skippedPoints += skipped;
        }
        if (deviceRecords.isEmpty() && pointRecords.isEmpty()) {
            log.warn("Influx 批量写入跳过: 无有效 Point, snapshots={}", snapshots.size());
            return;
        }
        try {
            if (!deviceRecords.isEmpty()) {
                influx.writeApi().writePoints(deviceRecords);
            }
            if (!pointRecords.isEmpty()) {
                influx.writeApi().writePoints(pointRecords);
            }
            log.info(
                    "Influx 批量写入完成 snapshots={}, device_realtime={}, device_point={}, skippedPointRecords={}",
                    snapshots.size(),
                    deviceRecords.size(),
                    pointRecords.size(),
                    skippedPoints
            );
        } catch (Exception e) {
            log.error(
                    "Influx 批量写入失败 snapshots={}, device_realtime={}, device_point={}: {}",
                    snapshots.size(),
                    deviceRecords.size(),
                    pointRecords.size(),
                    e.getMessage(),
                    e
            );
            throw e;
        }
    }

    private Point toDevicePoint(DeviceRealtimeObj snapshot) {
        if (snapshot.getDeviceId() == null || snapshot.getDeviceId().isBlank()) {
            return null;
        }
        Instant deviceTime = toInstant(snapshot.getLastUpdateTime());
        Point devicePoint = Point.measurement(MEASUREMENT_DEVICE)
                .addTag("device_id", snapshot.getDeviceId())
                .addTag("device_code", nullToEmpty(snapshot.getDeviceCode()))
                .addTag("protocol", protocolTag(snapshot.getProtocol()))
                .addField("online", snapshot.isOnline() ? 1 : 0)
                .time(deviceTime, writePrecision);
        if (StringUtils.hasText(snapshot.getMessage())) {
            devicePoint.addField("message", snapshot.getMessage());
        }
        return devicePoint;
    }

    private int appendPointRecords(List<Point> target, DeviceRealtimeObj snapshot) {
        Map<String, DevicePointRealtimeObj> pointMap = snapshot.getPoints();
        if (pointMap == null || pointMap.isEmpty()) {
            return 0;
        }
        String deviceId = snapshot.getDeviceId();
        if (deviceId == null || deviceId.isBlank()) {
            return pointMap.size();
        }
        String deviceCode = nullToEmpty(snapshot.getDeviceCode());
        String protocol = protocolTag(snapshot.getProtocol());
        int skipped = 0;
        for (DevicePointRealtimeObj point : pointMap.values()) {
            if (point == null) {
                continue;
            }
            Point record = toPointRecord(snapshot, deviceId, deviceCode, protocol, point);
            if (record != null) {
                target.add(record);
            } else {
                skipped++;
            }
        }
        return skipped;
    }

    private Point toPointRecord(
            DeviceRealtimeObj snapshot,
            String deviceId,
            String deviceCode,
            String protocol,
            DevicePointRealtimeObj point
    ) {
        if (!StringUtils.hasText(point.getName())) {
            return null;
        }
        Instant pointTime = toInstant(
                point.getUpdateTime() != null ? point.getUpdateTime() : snapshot.getLastUpdateTime()
        );
        Point record = Point.measurement(MEASUREMENT_POINT)
                .addTag("device_id", deviceId)
                .addTag("device_code", deviceCode)
                .addTag("protocol", protocol)
                .addTag("point_name", point.getName())
                .addTag("point_code", nullToEmpty(point.getCode()))
                .time(pointTime, writePrecision);
        if (StringUtils.hasText(point.getAddress())) {
            record = record.addTag("address", point.getAddress());
        }
        Point withValue = addValueFields(record, point.getValue());
        boolean hasField = withValue != null;
        if (hasField) {
            record = withValue;
        }
        if (point.getRawValue() != null && !point.getRawValue().equals(point.getValue())) {
            record = record.addField("raw_value", String.valueOf(point.getRawValue()));
            hasField = true;
        }
        if (StringUtils.hasText(point.getError())) {
            record = record.addField("error", point.getError());
            hasField = true;
        }
        if (!hasField) {
            log.debug("跳过无 field 的点位记录 deviceId={}, point={}", deviceId, point.getName());
            return null;
        }
        return record;
    }

    private static Point addValueFields(Point point, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return point.addField("value", number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return point.addField("value", bool ? 1.0 : 0.0);
        }
        String text = value.toString();
        Double numeric = tryParseDouble(text);
        if (numeric != null) {
            return point.addField("value", numeric);
        }
        return point.addField("value_text", text);
    }

    private static Double tryParseDouble(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
        }
        try {
            Number parsed = PointValueScaler.parseDisplayNumber(trimmed);
            return parsed != null ? parsed.doubleValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String protocolTag(DeviceProtocol protocol) {
        return protocol != null ? protocol.getCode() : "UNKNOWN";
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static Instant toInstant(LocalDateTime time) {
        if (time == null) {
            return Instant.now();
        }
        return time.atZone(ZoneId.systemDefault()).toInstant();
    }
}
