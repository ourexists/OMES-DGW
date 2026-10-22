package com.omes.dgw.device.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.device.cache.BitFieldExtractor;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import com.omes.dgw.codec.modbus.ModbusNumericCodec;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DevicePointConfigSupport {

    private DevicePointConfigSupport() {
    }

    public static DeviceCollectConfig emptyConfig() {
        return new DeviceCollectConfig();
    }

    public static DeviceCollectConfig normalize(DeviceCollectConfig config) {
        if (config == null) {
            return emptyConfig();
        }
        if (config.getPoints() == null) {
            config.setPoints(new ArrayList<>());
        }
        for (DevicePointConfigItem item : config.getPoints()) {
            sanitizeAccessAddresses(item);
            normalizeStringFields(item);
        }
        return config;
    }

    /**
     * 按数据类型元数据补默认值，并将不适用的配置置空或还原为类型默认值（保存、导入前调用）。
     */
    public static void preparePointConfig(
            DevicePointConfigItem item,
            DeviceProtocol protocol,
            String prefix
    ) {
        if (item == null) {
            throw new BusinessException(prefix + "点位配置不能为空");
        }
        if (item.getDataType() == null) {
            throw new BusinessException(prefix + "数据类型不能为空");
        }
        prepareAccessAddresses(item, prefix);
        PointDataType dataType = item.getDataType();
        String typeLabel = dataType.getLabel();

        if (protocol != null && !dataType.supports(protocol)) {
            throw new BusinessException(prefix + "数据类型「" + typeLabel
                    + "」不支持协议「" + protocol.getLabel() + "」");
        }

        boolean byteOrderApplicable = allowsByteOrder(dataType, protocol);

        if (!dataType.isSupportsEngRange()) {
            item.setEngValueMin(null);
            item.setEngValueMax(null);
        } else if (item.getEngValueMin() != null
                && item.getEngValueMax() != null
                && item.getEngValueMin().compareTo(item.getEngValueMax()) > 0) {
            throw new BusinessException(prefix + "工程值下限不能大于工程值上限");
        }
        // 工程值范围可选：未配置时读写不做范围限制；仅配置一侧时只约束该侧

        if (!byteOrderApplicable) {
            item.setByteOrder(null);
        }

        item.setScaleFactor(resolveScaleFactor(item.getScaleFactor(), dataType, prefix));
        item.setDecimalPlaces(resolveDecimalPlaces(item.getDecimalPlaces(), dataType, prefix));
        item.setBcdConvert(resolveBcdConvert(item.getBcdConvert(), dataType));
        resolveBitField(item, dataType, prefix);

        normalizeStringFields(item);
    }

    public static void preparePoints(List<DevicePointConfigItem> points, DeviceProtocol protocol) {
        if (points == null || points.isEmpty()) {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < points.size(); i++) {
            DevicePointConfigItem item = points.get(i);
            if (item == null) {
                throw new BusinessException("点位配置不能为空");
            }
            preparePointConfig(item, protocol, pointPrefix(item, i));
            if (!StringUtils.hasText(item.getCode())) {
                throw new BusinessException(pointPrefix(item, i) + "点位编码不能为空");
            }
            if (!codes.add(item.getCode().trim())) {
                throw new BusinessException("存在重复点位编码: " + item.getCode().trim());
            }
        }
    }

    public static DeviceCollectConfig normalize(DeviceCollectConfig config, DeviceProtocol protocol) {
        normalize(config);
        if (protocol != null && config.getPoints() != null) {
            preparePoints(config.getPoints(), protocol);
        }
        return config;
    }

    public static String pointPrefix(DevicePointConfigItem item, int index) {
        if (item != null && StringUtils.hasText(item.getCode())) {
            return "点位「" + item.getCode().trim() + "」：";
        }
        return "点位[" + (index + 1) + "]：";
    }

    public static boolean allowsByteOrder(PointDataType dataType, DeviceProtocol protocol) {
        return dataType != null
                && dataType.isSupportsByteOrder()
                && (protocol == DeviceProtocol.MODBUS_TCP
                || protocol == DeviceProtocol.FINS_TCP
                || protocol == DeviceProtocol.SLMP_TCP);
    }

    private static void normalizeStringFields(DevicePointConfigItem item) {
        if (item == null || item.getDataType() == null || !item.getDataType().isModbusStringType()) {
            return;
        }
        if (item.getStringLength() == null || item.getStringLength() < 1) {
            item.setStringLength(ModbusNumericCodec.DEFAULT_STRING_LENGTH);
        }
    }

    /** 填充 PLC 类型名与中文说明（不写入 COLLECT_CONFIG） */
    public static void enrichDataTypeDisplay(DevicePointConfigItem item) {
        if (item == null || item.getDataType() == null) {
            return;
        }
        item.setDataTypeLabel(item.getDataType().getLabel());
        item.setDataTypeDescription(item.getDataType().getDescription());
    }

    public static String toJson(ObjectMapper objectMapper, DeviceCollectConfig config) {
        try {
            return objectMapper.writeValueAsString(normalize(config));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("采集配置序列化失败", e);
        }
    }

    public static DeviceCollectConfig fromJson(ObjectMapper objectMapper, String json) {
        if (!StringUtils.hasText(json)) {
            return emptyConfig();
        }
        try {
            DeviceCollectConfig config = objectMapper.readValue(json, DeviceCollectConfig.class);
            return normalize(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("采集配置解析失败", e);
        }
    }

    /**
     * 可采集点位，key 为点位编码（唯一）；无编码时回退为名称。
     * 点位名称允许重复，不能再用名称去重。
     */
    public static Map<String, DevicePoint> toPointsByName(DeviceRealtimeObj device) {
        if (device == null || !StringUtils.hasText(device.getDeviceId())) {
            return Map.of();
        }
        return toPointsByName(device.getDeviceId(), device.getCollectConfig());
    }

    private static Map<String, DevicePoint> toPointsByName(String deviceId, DeviceCollectConfig collectConfig) {
        DeviceCollectConfig config = normalize(collectConfig);
        Map<String, DevicePoint> result = new LinkedHashMap<>();
        for (DevicePointConfigItem item : config.getPoints()) {
            String key = pointMapKey(item);
            if (item == null || !StringUtils.hasText(key)) {
                continue;
            }
            PointAccessType accessType = item.getAccessType() != null
                    ? item.getAccessType()
                    : PointAccessType.READ_ONLY;
            if (!accessType.isCollectable() || !StringUtils.hasText(item.getActualAddress())) {
                continue;
            }
            result.putIfAbsent(key, toPoint(item, deviceId));
        }
        return result;
    }

    public static Map<String, DevicePoint> toWritablePointsByName(DeviceRealtimeObj device) {
        if (device == null || !StringUtils.hasText(device.getDeviceId())) {
            return Map.of();
        }
        return toWritablePointsByName(device.getDeviceId(), device.getCollectConfig());
    }

    private static Map<String, DevicePoint> toWritablePointsByName(String deviceId, DeviceCollectConfig collectConfig) {
        DeviceCollectConfig config = normalize(collectConfig);
        Map<String, DevicePoint> result = new LinkedHashMap<>();
        for (DevicePointConfigItem item : config.getPoints()) {
            String key = pointMapKey(item);
            if (item == null || !StringUtils.hasText(key)) {
                continue;
            }
            PointAccessType accessType = item.getAccessType() != null
                    ? item.getAccessType()
                    : PointAccessType.READ_ONLY;
            if (!accessType.isWritable() || !StringUtils.hasText(item.getSetpointAddress())) {
                continue;
            }
            result.putIfAbsent(key, toPoint(item, deviceId));
        }
        return result;
    }

    /** 实时/采集 Map 的唯一键：优先点位编码，其次名称。 */
    public static String pointMapKey(DevicePointConfigItem item) {
        if (item == null) {
            return null;
        }
        if (StringUtils.hasText(item.getCode())) {
            return item.getCode().trim();
        }
        return StringUtils.hasText(item.getName()) ? item.getName().trim() : null;
    }

    public static String pointMapKey(DevicePoint point) {
        if (point == null) {
            return null;
        }
        if (StringUtils.hasText(point.getCode())) {
            return point.getCode().trim();
        }
        return StringUtils.hasText(point.getName()) ? point.getName().trim() : null;
    }

    public static DevicePoint toPoint(DevicePointConfigItem item, String deviceId) {
        DevicePoint point = new DevicePoint();
        BeanUtils.copyProperties(item, point);
        point.setDeviceId(deviceId);
        if (point.getAccessType() == null) {
            point.setAccessType(PointAccessType.READ_ONLY);
        }
        return point;
    }

    private static void prepareAccessAddresses(DevicePointConfigItem item, String prefix) {
        sanitizeAccessAddresses(item);
        PointAccessType accessType = item.getAccessType();
        if (accessType.isCollectable() && !StringUtils.hasText(item.getActualAddress())) {
            throw new BusinessException(prefix + "实际值点位不能为空");
        }
        if (accessType.isWritable() && !StringUtils.hasText(item.getSetpointAddress())) {
            throw new BusinessException(prefix + "设定值点位不能为空");
        }
    }

    /** 按读写权限清掉不该存在的地址，读取时也调用，避免只读点带出设定值。 */
    private static void sanitizeAccessAddresses(DevicePointConfigItem item) {
        if (item == null) {
            return;
        }
        PointAccessType accessType = item.getAccessType() != null
                ? item.getAccessType()
                : PointAccessType.READ_ONLY;
        item.setAccessType(accessType);
        item.setActualAddress(accessType.isCollectable() ? trimToNull(item.getActualAddress()) : null);
        item.setSetpointAddress(accessType.isWritable() ? trimToNull(item.getSetpointAddress()) : null);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public static DevicePointConfigItem findPointByCode(DeviceCollectConfig collectConfig, String pointCode) {
        if (!StringUtils.hasText(pointCode)) {
            return null;
        }
        DeviceCollectConfig config = normalize(collectConfig);
        for (DevicePointConfigItem item : config.getPoints()) {
            if (item != null && pointCode.equals(item.getCode())) {
                return item;
            }
        }
        return null;
    }

    private static BigDecimal resolveScaleFactor(
            BigDecimal scaleFactor,
            PointDataType dataType,
            String prefix
    ) {
        if (!dataType.isSupportsScaleFactor()) {
            return dataType.getDefaultScaleFactor();
        }
        BigDecimal resolved = scaleFactor != null ? scaleFactor : dataType.getDefaultScaleFactor();
        if (resolved.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(prefix + "缩放系数不能为 0");
        }
        return resolved;
    }

    private static int resolveDecimalPlaces(
            Integer decimalPlaces,
            PointDataType dataType,
            String prefix
    ) {
        if (!dataType.isSupportsDecimalPlaces()) {
            return dataType.getDefaultDecimalPlaces();
        }
        int resolved = decimalPlaces != null ? decimalPlaces : dataType.getDefaultDecimalPlaces();
        if (resolved < 0 || resolved > 10) {
            throw new BusinessException(prefix + "小数位须在 0-10 之间");
        }
        return resolved;
    }

    private static Boolean resolveBcdConvert(Boolean bcdConvert, PointDataType dataType) {
        if (!dataType.isSupportsBcdConvert()) {
            return null;
        }
        return Boolean.TRUE.equals(bcdConvert);
    }

    private static void resolveBitField(DevicePointConfigItem item, PointDataType dataType, String prefix) {
        if (!dataType.isSupportsBitField()) {
            item.setBitField(null);
            item.setBitOffset(null);
            item.setBitLength(null);
            return;
        }
        if (StringUtils.hasText(item.getBitField())) {
            try {
                BitFieldExtractor.Layout layout = BitFieldExtractor.resolve(item.getBitField());
                if (layout.offset() + layout.length() > dataType.getBitWidth()) {
                    throw new BusinessException(prefix + "位域「" + layout.code()
                            + "」超出类型位宽 " + dataType.getBitWidth());
                }
                item.setBitField(layout.code());
                item.setBitOffset(layout.offset());
                item.setBitLength(layout.length());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(prefix + ex.getMessage());
            }
            return;
        }
        Integer bitLength = item.getBitLength();
        Integer bitOffset = item.getBitOffset();
        if (bitLength == null && bitOffset == null) {
            item.setBitField(null);
            return;
        }
        if (bitLength == null) {
            throw new BusinessException(prefix + "配置了位偏移时请填写位域（如 A/BA）或同时配置位长度");
        }
        int width = dataType.getBitWidth();
        if (bitLength < 1 || bitLength > width) {
            throw new BusinessException(prefix + "位长度须在 1-" + width + " 之间");
        }
        int offset = bitOffset != null ? bitOffset : 0;
        if (offset < 0 || offset >= width) {
            throw new BusinessException(prefix + "位偏移须在 0-" + (width - 1) + " 之间");
        }
        if (offset + bitLength > width) {
            throw new BusinessException(prefix + "位偏移+位长度不能超过类型位宽 " + width);
        }
        item.setBitOffset(offset);
        item.setBitLength(bitLength);
        BitFieldExtractor.Layout known = BitFieldExtractor.fromOffsetLength(offset, bitLength);
        item.setBitField(known != null ? known.code() : null);
    }
}
