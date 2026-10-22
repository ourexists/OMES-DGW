package com.omes.dgw.device.collect;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.omes.dgw.device.cache.BitFieldExtractor;
import com.omes.dgw.device.cache.DevicePointRealtimeObj;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.cache.DeviceRealtimePointSupport;
import com.omes.dgw.device.cache.PointValueScaler;
import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.ValueNormalizer;
import com.omes.dgw.protocol.PollTransportFailures;
import com.omes.dgw.codec.modbus.ModbusNumericCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PLC/Modbus 采集数据解析，输出 {@link DeviceRealtimeObj} 供本地实时缓存使用。
 */
@Slf4j
@Component
public class PlcDeviceDataParser implements DeviceDataParser {

    @Override
    public DeviceRealtimeObj parse(
            DeviceRealtimeObj deviceBase,
            Map<String, DevicePoint> pointsByName,
            Map<String, Object> rawValues,
            String error
    ) {
        return parseWithTagErrors(deviceBase, pointsByName, rawValues, error, null);
    }

    public DeviceRealtimeObj parseWithTagErrors(
            DeviceRealtimeObj deviceBase,
            Map<String, DevicePoint> pointsByName,
            Map<String, Object> rawValues,
            String error,
            Map<String, String> tagReadErrors
    ) {
        LocalDateTime now = LocalDateTime.now();
        DeviceRealtimeObj snapshot = copyHeader(deviceBase);
        Map<String, Object> values = rawValues != null ? rawValues : Map.of();
        boolean anySuccess = fillPoints(snapshot, deviceBase, pointsByName, values, error, tagReadErrors, now);
        applyOnlineState(snapshot, pointsByName, error, tagReadErrors, anySuccess);
        if (error == null) {
            snapshot.setLastUpdateTime(now);
        }
        log.debug("解析采集数据 deviceId={}, online={}, pointCount={}, error={}",
                snapshot.getDeviceId(),
                snapshot.isOnline(),
                snapshot.getPoints() != null ? snapshot.getPoints().size() : 0,
                error);
        return snapshot;
    }

    private static DeviceRealtimeObj copyHeader(DeviceRealtimeObj base) {
        DeviceRealtimeObj snapshot = new DeviceRealtimeObj();
        if (base == null) {
            return snapshot;
        }
        snapshot.setDeviceId(base.getDeviceId());
        snapshot.setEventId(base.getEventId());
        snapshot.setDeviceCode(base.getDeviceCode());
        snapshot.setDeviceName(base.getDeviceName());
        snapshot.setProtocol(base.getProtocol());
        snapshot.setIpAddress(base.getIpAddress());
        snapshot.setPort(base.getPort());
        snapshot.setCollectIntervalSec(base.getCollectIntervalSec());
        snapshot.setConnectTimeoutMs(base.getConnectTimeoutMs());
        snapshot.setCollectConfig(base.getCollectConfig());
        snapshot.setConnectionStatus(base.getConnectionStatus());
        snapshot.setConnectionStatusLabel(base.getConnectionStatusLabel());
        snapshot.setConnectionStatusMessage(base.getConnectionStatusMessage());
        snapshot.setLastUpdateTime(base.getLastUpdateTime());
        return snapshot;
    }

    /**
     * 将采集 JSON 转为点位名 -> 原始值 Map。
     */
    public Map<String, Object> parseJsonPayload(String sourceData) {
        if (!StringUtils.hasText(sourceData)) {
            return Map.of();
        }
        JSONObject json = JSON.parseObject(sourceData);
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>(json.size());
        for (String key : json.keySet()) {
            map.put(key, json.get(key));
        }
        return map;
    }

    /**
     * 基于上一轮快照增量更新点位；本轮无值且非整轮失败时保留上次有效读数。
     */
    private static boolean fillPoints(
            DeviceRealtimeObj snapshot,
            DeviceRealtimeObj previous,
            Map<String, DevicePoint> pointsByName,
            Map<String, Object> rawValues,
            String error,
            Map<String, String> tagReadErrors,
            LocalDateTime now
    ) {
        boolean anySuccess = false;
        DeviceProtocol protocol = snapshot.getProtocol();
        Map<String, DevicePoint> points = pointsByName != null ? pointsByName : Map.of();
        Map<String, DevicePointRealtimeObj> previousPoints =
                previous != null && previous.getPoints() != null ? previous.getPoints() : Map.of();

        Map<String, DevicePointRealtimeObj> nextPoints = new LinkedHashMap<>(points.size());
        for (Map.Entry<String, DevicePoint> entry : points.entrySet()) {
            String key = entry.getKey();
            DevicePoint point = entry.getValue();
            DevicePointRealtimeObj previousView = previousPoints.get(key);
            if (previousView == null && StringUtils.hasText(point.getName())) {
                DevicePointRealtimeObj byName = previousPoints.get(point.getName());
                if (byName != null && (!StringUtils.hasText(byName.getCode())
                        || byName.getCode().equals(point.getCode()))) {
                    previousView = byName;
                }
            }
            DevicePointRealtimeObj pv = clonePointView(previousView, point);

            Object raw = resolveRawValue(rawValues.get(key));
            raw = normalizeBoolRaw(protocol, raw, point.getDataType());

            if (raw != null) {
                pv.setUpdateTime(now);
                Object fieldRaw = BitFieldExtractor.extract(raw, point);
                pv.setRawValue(fieldRaw);
                pv.setValue(PointValueScaler.applyScale(fieldRaw, point));
                pv.setError(null);
                anySuccess = true;
            } else if (tagReadErrors != null && tagReadErrors.containsKey(key)) {
                pv.setUpdateTime(now);
                pv.setRawValue(null);
                pv.setValue(null);
                pv.setError(tagReadErrors.get(key));
            } else if (StringUtils.hasText(error)) {
                pv.setUpdateTime(now);
                pv.setRawValue(null);
                pv.setValue(null);
                pv.setError(error);
            } else if (hasLastKnownGood(pv)) {
                anySuccess = true;
            } else {
                pv.setUpdateTime(now);
                pv.setRawValue(null);
                pv.setValue(null);
                pv.setError(DeviceRealtimePointSupport.UNREAD_ERROR);
            }
            nextPoints.put(key, pv);
        }
        snapshot.setPoints(nextPoints);
        return anySuccess;
    }

    private static DevicePointRealtimeObj clonePointView(DevicePointRealtimeObj previous, DevicePoint config) {
        DevicePointRealtimeObj pv = new DevicePointRealtimeObj();
        pv.setCode(config.getCode());
        pv.setName(config.getName());
        pv.setAddress(config.getAddress());
        if (previous != null) {
            pv.setUnit(config.getEngUnit() != null ? config.getEngUnit() : previous.getUnit());
            pv.setRawValue(previous.getRawValue());
            pv.setValue(previous.getValue());
            pv.setUpdateTime(previous.getUpdateTime());
            pv.setError(previous.getError());
        } else {
            pv.setUnit(config.getEngUnit());
        }
        return pv;
    }

    private static boolean hasLastKnownGood(DevicePointRealtimeObj pv) {
        return pv.getRawValue() != null && !StringUtils.hasText(pv.getError());
    }

    private static Object resolveRawValue(Object value) {
        if (value == null) {
            return null;
        }
        return ValueNormalizer.needsConversion(value) ? ValueNormalizer.toJava(value) : value;
    }

    /**
     * Modbus 轮询已在 {@link ModbusNumericCodec} 处理 BOOL/BOOL_INV；其它协议仅对反相布尔在此解码。
     */
    private static Object normalizeBoolRaw(DeviceProtocol protocol, Object raw, PointDataType dataType) {
        if (raw == null || dataType == null || !dataType.isBoolInverted()) {
            return raw;
        }
        if (protocol == DeviceProtocol.MODBUS_TCP) {
            return raw;
        }
        if (protocol == DeviceProtocol.FINS_TCP) {
            return raw;
        }
        if (protocol == DeviceProtocol.SLMP_TCP) {
            return raw;
        }
        Boolean decoded = ModbusNumericCodec.decodeBool(raw, dataType);
        return decoded != null ? decoded : raw;
    }

    private static void applyOnlineState(
            DeviceRealtimeObj snapshot,
            Map<String, DevicePoint> pointsByName,
            String error,
            Map<String, String> tagReadErrors,
            boolean anySuccess
    ) {
        if (error != null) {
            snapshot.setOnline(false);
            snapshot.setMessage(error);
            return;
        }
        if (pointsByName == null || pointsByName.isEmpty()) {
            snapshot.setOnline(true);
            snapshot.setMessage("连接成功，无配置点位");
            return;
        }
        if (tagReadErrors != null && !tagReadErrors.isEmpty()) {
            if (PollTransportFailures.hasTransportTagReadError(tagReadErrors)) {
                snapshot.setOnline(false);
                snapshot.setMessage(firstTransportError(tagReadErrors, error));
                return;
            }
            snapshot.setOnline(true);
            snapshot.setMessage(anySuccess ? "部分点位读取失败" : "全部点位读取失败");
            return;
        }
        snapshot.setOnline(anySuccess);
        snapshot.setMessage(anySuccess ? null : "全部点位读取失败");
    }

    private static String firstTransportError(Map<String, String> tagReadErrors, String error) {
        if (tagReadErrors != null) {
            for (String err : tagReadErrors.values()) {
                if (PollTransportFailures.isTransportFailure(err)) {
                    return err;
                }
            }
        }
        return StringUtils.hasText(error) ? error : "连接中断";
    }
}
