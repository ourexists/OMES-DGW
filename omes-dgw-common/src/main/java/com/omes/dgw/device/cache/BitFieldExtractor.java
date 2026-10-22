package com.omes.dgw.device.cache;

import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.codec.ValueNormalizer;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WORD/整型寄存器内的位域拆分。
 * <p>
 * 配置位域名（BCD=DCBA）：{@code A}/{@code B}/{@code C}/{@code D} 各 4 bit；
 * {@code BA}/{@code DC}（别名 {@code AB}/{@code CD}）各 8 bit。先拆分，再可选 BCD 转码。
 */
public final class BitFieldExtractor {

    public record Layout(String code, int offset, int length) {
    }

    private static final Map<String, Layout> BY_ALIAS = new LinkedHashMap<>();
    private static final List<Layout> CANONICAL = List.of(
            new Layout("A", 0, 4),
            new Layout("B", 4, 4),
            new Layout("C", 8, 4),
            new Layout("D", 12, 4),
            new Layout("BA", 0, 8),
            new Layout("DC", 8, 8)
    );

    static {
        for (Layout layout : CANONICAL) {
            BY_ALIAS.put(layout.code(), layout);
        }
        BY_ALIAS.put("AB", BY_ALIAS.get("BA"));
        BY_ALIAS.put("CD", BY_ALIAS.get("DC"));
    }

    private BitFieldExtractor() {
    }

    public static String supportedCodesHint() {
        return "A/B/C/D/BA/DC（别名 AB/CD）";
    }

    public static List<Layout> canonicalLayouts() {
        return CANONICAL;
    }

    public static Layout resolve(String bitField) {
        if (!StringUtils.hasText(bitField)) {
            return null;
        }
        Layout layout = BY_ALIAS.get(normalizeCode(bitField));
        if (layout == null) {
            throw new IllegalArgumentException("不支持的位域: " + bitField.trim() + "，可选 " + supportedCodesHint());
        }
        return layout;
    }

    public static String canonicalize(String bitField) {
        Layout layout = resolve(bitField);
        return layout != null ? layout.code() : null;
    }

    public static Layout fromOffsetLength(Integer bitOffset, Integer bitLength) {
        if (bitLength == null || bitLength <= 0) {
            return null;
        }
        int offset = bitOffset != null ? bitOffset : 0;
        for (Layout layout : CANONICAL) {
            if (layout.offset() == offset && layout.length() == bitLength) {
                return layout;
            }
        }
        return null;
    }

    public static boolean isConfigured(DevicePoint point) {
        if (point == null) {
            return false;
        }
        if (StringUtils.hasText(point.getBitField())) {
            return true;
        }
        return point.getBitLength() != null && point.getBitLength() > 0;
    }

    public static boolean isConfigured(DevicePointConfigItem item) {
        if (item == null) {
            return false;
        }
        if (StringUtils.hasText(item.getBitField())) {
            return true;
        }
        return item.getBitLength() != null && item.getBitLength() > 0;
    }

    /**
     * 从整型原始值中按位域提取无符号字段值；未配置位域时原样返回。
     */
    public static Object extract(Object raw, DevicePoint point) {
        if (raw == null || !isConfigured(point)) {
            return raw;
        }
        if (raw instanceof Boolean) {
            return raw;
        }
        Number number = toNumber(raw);
        if (number == null) {
            return raw;
        }
        int offset;
        int length;
        if (StringUtils.hasText(point.getBitField())) {
            Layout layout = resolve(point.getBitField());
            offset = layout.offset();
            length = layout.length();
        } else {
            offset = point.getBitOffset() != null ? point.getBitOffset() : 0;
            length = point.getBitLength();
        }
        long bits = toBitPattern(number, point.getDataType());
        long mask = length >= 64 ? -1L : ((1L << length) - 1L);
        return (bits >>> offset) & mask;
    }

    private static String normalizeCode(String bitField) {
        String key = bitField.trim().toUpperCase(Locale.ROOT);
        if (key.endsWith("位")) {
            key = key.substring(0, key.length() - 1).trim();
        }
        return key;
    }

    private static long toBitPattern(Number number, PointDataType dataType) {
        int width = dataType != null ? dataType.getBitWidth() : 16;
        if (width <= 0) {
            width = 16;
        }
        if (width >= 64) {
            return number.longValue();
        }
        long mask = width >= 63 ? -1L : ((1L << width) - 1L);
        return number.longValue() & mask;
    }

    private static Number toNumber(Object raw) {
        Object java = ValueNormalizer.needsConversion(raw) ? ValueNormalizer.toJava(raw) : raw;
        if (java instanceof Number number) {
            return number;
        }
        if (java instanceof List<?> list && list.size() == 1) {
            return toNumber(list.get(0));
        }
        if (java != null) {
            return PointValueScaler.parseDisplayNumber(java.toString());
        }
        return null;
    }
}
