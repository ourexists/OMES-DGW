package com.omes.dgw.device.cache;

import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.ValueNormalizer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

public final class PointValueScaler {

    private static final BigInteger SIXTEEN = BigInteger.valueOf(16);

    private PointValueScaler() {
    }

    /**
     * 将工程值还原为设备原始写入值。若开启 BCD 转码，则先按缩放还原，再编码为 Packed BCD。
     */
    public static Object toRawWriteValue(Object engineeringValue, DevicePoint point) {
        if (engineeringValue == null || point == null) {
            return null;
        }
        return toRawWriteValue(
                engineeringValue,
                point.getDataType(),
                point.getScaleFactor(),
                point.getBcdConvert(),
                point.getDecimalPlaces());
    }

    public static Object toRawWriteValue(
            Object engineeringValue, PointDataType dataType, BigDecimal scaleFactor
    ) {
        return toRawWriteValue(engineeringValue, dataType, scaleFactor, false, null);
    }

    public static Object toRawWriteValue(
            Object engineeringValue,
            PointDataType dataType,
            BigDecimal scaleFactor,
            Boolean bcdConvert
    ) {
        return toRawWriteValue(engineeringValue, dataType, scaleFactor, bcdConvert, null);
    }

    public static Object toRawWriteValue(
            Object engineeringValue,
            PointDataType dataType,
            BigDecimal scaleFactor,
            Boolean bcdConvert,
            Integer decimalPlaces
    ) {
        if (engineeringValue == null || dataType == null) {
            return null;
        }
        if (engineeringValue instanceof Boolean) {
            return coerceToDataType(engineeringValue, dataType);
        }
        Number decimalEngineering = parseDisplayNumber(engineeringValue);
        if (decimalEngineering == null) {
            return coerceToDataType(engineeringValue, dataType);
        }
        // 与采集显示一致：先按小数位截断工程值，再还原为原始写入值，避免浮点多余小数位
        Object formattedEngineering = formatDecimalPlaces(
                toBigDecimal(decimalEngineering), decimalPlaces, dataType);
        BigDecimal engineeringDecimal = toBigDecimal(
                formattedEngineering instanceof Number number ? number : decimalEngineering);
        BigDecimal scale = scaleFactor != null ? scaleFactor : BigDecimal.ONE;
        if (scale.compareTo(BigDecimal.ZERO) == 0) {
            scale = BigDecimal.ONE;
        }
        BigDecimal rawDecimal = scale.compareTo(BigDecimal.ONE) == 0
                ? engineeringDecimal
                : engineeringDecimal.divide(scale, 10, RoundingMode.HALF_UP);
        rawDecimal = normalizeWriteDecimal(rawDecimal);
        Number rawNumber = rawDecimal;
        if (shouldConvertBcd(bcdConvert, dataType)) {
            rawNumber = bcdEncodeOrNull(rawDecimal);
            if (rawNumber == null) {
                return null;
            }
        }
        return coerceToDataType(rawNumber, dataType);
    }

    public static Object reverseScale(Object engineeringValue, DevicePoint point) {
        if (engineeringValue == null || point == null) {
            return null;
        }
        if (engineeringValue instanceof Boolean) {
            return engineeringValue;
        }
        BigDecimal scale = point.getScaleFactor() != null ? point.getScaleFactor() : BigDecimal.ONE;
        if (scale.compareTo(BigDecimal.ZERO) == 0) {
            scale = BigDecimal.ONE;
        }
        Number decimal = parseDisplayNumber(engineeringValue);
        if (decimal == null) {
            return engineeringValue;
        }
        BigDecimal raw = toBigDecimal(decimal).divide(scale, 10, RoundingMode.HALF_UP);
        return raw;
    }

    public static Object coerceToDataType(Object value, PointDataType dataType) {
        if (value == null || dataType == null) {
            return value;
        }
        return switch (dataType) {
            case BOOL, BOOL_INV -> {
                if (value instanceof Boolean b) {
                    yield dataType.isBoolInverted() != b;
                }
                if (value instanceof Number n) {
                    boolean eng = n.intValue() != 0;
                    yield dataType.isBoolInverted() != eng;
                }
                String str = value.toString().trim().toLowerCase();
                boolean eng = "true".equals(str) || "1".equals(str) || "on".equals(str);
                yield dataType.isBoolInverted() != eng;
            }
            case TEXT8 -> value.toString();
            case INT -> toNumber(value).shortValue();
            case UINT -> toNumber(value).intValue() & 0xFFFF;
            case LONG -> toNumber(value).intValue();
            case ULONG -> toNumber(value).longValue() & 0xFFFFFFFFL;
            case REAL -> parsePlainFloat(toBigDecimal(toNumber(value)));
            case LREAL -> parsePlainDouble(toBigDecimal(toNumber(value)));
        };
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        Number parsed = parseDisplayNumber(value);
        if (parsed != null) {
            return parsed;
        }
        return new BigDecimal(value.toString().trim());
    }

    /**
     * 采集缩放：PLC 原始值若开启 BCD 转码则先按 Packed BCD 解码，再乘缩放系数。
     * 例如原始寄存器 {@code 0x1234}（十进制 4660）解码后为 1234。
     */
    public static Object applyScale(Object raw, DevicePoint point) {
        if (raw == null || point == null) {
            return null;
        }
        if (raw instanceof Boolean) {
            return raw;
        }
        Number number = normalizeToNumber(raw);
        if (number == null) {
            return raw.toString();
        }
        if (shouldConvertBcd(point.getBcdConvert(), point.getDataType())) {
            number = bcdDecodeOrSame(number);
        }
        BigDecimal scale = point.getScaleFactor() != null ? point.getScaleFactor() : BigDecimal.ONE;
        if (scale.compareTo(BigDecimal.ZERO) == 0) {
            scale = BigDecimal.ONE;
        }
        BigDecimal engineering = toBigDecimal(number).multiply(scale);
        return formatDecimalPlaces(engineering, point.getDecimalPlaces(), point.getDataType());
    }

    /**
     * 按点位工程值范围校验写入值。上下限均可选：未配置的一侧不限制。
     *
     * @throws IllegalArgumentException 超出已配置的范围时
     */
    public static void assertWithinEngRange(Object engineeringValue, BigDecimal engValueMin, BigDecimal engValueMax) {
        if (engValueMin == null && engValueMax == null) {
            return;
        }
        if (engineeringValue instanceof Boolean) {
            return;
        }
        Number number = parseDisplayNumber(engineeringValue);
        if (number == null) {
            return;
        }
        BigDecimal value = toBigDecimal(number);
        if (engValueMin != null && value.compareTo(engValueMin) < 0) {
            throw new IllegalArgumentException("写入值低于工程值下限 " + engValueMin.toPlainString());
        }
        if (engValueMax != null && value.compareTo(engValueMax) > 0) {
            throw new IllegalArgumentException("写入值高于工程值上限 " + engValueMax.toPlainString());
        }
    }

    public static Object formatDecimalPlaces(BigDecimal value, Integer decimalPlaces) {
        return formatDecimalPlaces(value, decimalPlaces, null);
    }

    public static Object formatDecimalPlaces(
            BigDecimal value, Integer decimalPlaces, PointDataType dataType
    ) {
        if (value == null) {
            return null;
        }
        int scale = resolveDecimalPlaces(decimalPlaces, dataType);
        BigDecimal truncated = value.setScale(scale, RoundingMode.DOWN);
        if (scale == 0 && !isFloatingDataType(dataType)) {
            return truncated.longValue();
        }
        if (scale == 0) {
            return parsePlainDouble(truncated);
        }
        return truncated;
    }

    public static Number parseDisplayNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            if (looksLikeDecimal(text)) {
                return new BigDecimal(text);
            }
            return parseIntegerText(text, detectRadixHint(text));
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    private static boolean shouldConvertBcd(Boolean bcdConvert, PointDataType dataType) {
        if (!Boolean.TRUE.equals(bcdConvert)) {
            return false;
        }
        return dataType == null || dataType.isSupportsBcdConvert();
    }

    private static Number bcdDecodeOrSame(Number number) {
        try {
            BigInteger raw = toBigDecimal(number).setScale(0, RoundingMode.DOWN).toBigInteger();
            return toJsonNumber(bcdDecode(raw));
        } catch (NumberFormatException | ArithmeticException ignored) {
            return number;
        }
    }

    private static Number bcdEncodeOrNull(BigDecimal rawDecimal) {
        try {
            BigInteger decimal = rawDecimal.setScale(0, RoundingMode.DOWN).toBigInteger();
            return toJsonNumber(bcdEncode(decimal));
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    /**
     * Packed BCD 解码：每个 nibble（4 bit）为一位十进制数字 0-9。
     * {@code 0x1234} → 1234。nibble 大于 9 时抛出异常。
     */
    static BigInteger bcdDecode(BigInteger raw) {
        if (raw.signum() < 0) {
            return bcdDecode(raw.abs()).negate();
        }
        if (raw.signum() == 0) {
            return BigInteger.ZERO;
        }
        BigInteger result = BigInteger.ZERO;
        BigInteger multiplier = BigInteger.ONE;
        BigInteger value = raw;
        while (value.signum() > 0) {
            int digit = value.mod(SIXTEEN).intValue();
            if (digit > 9) {
                throw new NumberFormatException("invalid BCD nibble: " + digit);
            }
            result = result.add(BigInteger.valueOf(digit).multiply(multiplier));
            multiplier = multiplier.multiply(BigInteger.TEN);
            value = value.divide(SIXTEEN);
        }
        return result;
    }

    /**
     * Packed BCD 编码：每位十进制数字写入一个 nibble。1234 → {@code 0x1234}。
     */
    static BigInteger bcdEncode(BigInteger decimal) {
        if (decimal.signum() < 0) {
            return bcdEncode(decimal.abs()).negate();
        }
        if (decimal.signum() == 0) {
            return BigInteger.ZERO;
        }
        BigInteger result = BigInteger.ZERO;
        int shift = 0;
        BigInteger value = decimal;
        while (value.signum() > 0) {
            int digit = value.mod(BigInteger.TEN).intValue();
            result = result.add(BigInteger.valueOf(digit).shiftLeft(shift));
            shift += 4;
            value = value.divide(BigInteger.TEN);
        }
        return result;
    }

    private static boolean isFloatingDataType(PointDataType dataType) {
        return dataType == PointDataType.REAL
                || dataType == PointDataType.LREAL;
    }

    /**
     * 去掉缩放/除法留下的多余小数位（如 12.3000000000 → 12.3），再写入设备。
     */
    private static BigDecimal normalizeWriteDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized;
    }

    /**
     * 避免 float/double 写入后出现 23.399999999 等二进制误差。
     */
    private static Float parsePlainFloat(BigDecimal value) {
        return Float.parseFloat(normalizeWriteDecimal(value).toPlainString());
    }

    /**
     * 避免 float/double 写入 JSON 后出现 23.399999999 等二进制误差。
     */
    private static Double parsePlainDouble(BigDecimal value) {
        return Double.parseDouble(normalizeWriteDecimal(value).toPlainString());
    }

    private static int resolveDecimalPlaces(Integer decimalPlaces) {
        return resolveDecimalPlaces(decimalPlaces, null);
    }

    private static int resolveDecimalPlaces(Integer decimalPlaces, PointDataType dataType) {
        if (decimalPlaces == null) {
            return dataType != null ? dataType.getDefaultDecimalPlaces() : 0;
        }
        return Math.max(0, Math.min(10, decimalPlaces));
    }

    private static Number normalizeToNumber(Object raw) {
        Object java = ValueNormalizer.toJava(raw);
        if (java instanceof Number number) {
            return number;
        }
        if (java instanceof List<?> list && list.size() == 1) {
            return normalizeToNumber(list.get(0));
        }
        if (java != null) {
            String text = java.toString().trim();
            if (!text.isEmpty()) {
                Number parsed = parseDisplayNumber(text);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static int detectRadixHint(String text) {
        String lower = text.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("-")) {
            lower = lower.substring(1).trim();
        }
        if (lower.startsWith("0x") || lower.startsWith("#") || lower.startsWith("h")) {
            return 16;
        }
        if (lower.startsWith("0b") || lower.startsWith("b")) {
            return 2;
        }
        if (lower.startsWith("0o") || lower.startsWith("o")) {
            return 8;
        }
        return 10;
    }

    private static boolean looksLikeDecimal(String text) {
        String t = text.trim();
        if (t.startsWith("-")) {
            t = t.substring(1).trim();
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return !(lower.startsWith("0x") || lower.startsWith("0b") || lower.startsWith("0o")
                || lower.startsWith("#") || lower.startsWith("h") || lower.startsWith("b") || lower.startsWith("o"));
    }

    private static Number toJsonNumber(BigInteger value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException ignored) {
            return value;
        }
    }

    private static BigInteger parseIntegerText(String text, int radix) {
        String normalized = stripRadixPrefix(text, radix);
        boolean negative = normalized.startsWith("-");
        if (negative) {
            normalized = normalized.substring(1).trim();
        }
        BigInteger value = new BigInteger(normalized, radix);
        return negative ? value.negate() : value;
    }

    private static String stripRadixPrefix(String text, int radix) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        boolean negative = trimmed.startsWith("-");
        String body = negative ? trimmed.substring(1).trim() : trimmed;
        String lower = body.toLowerCase(Locale.ROOT);
        if (radix == 16 && (lower.startsWith("0x") || lower.startsWith("#") || lower.startsWith("h"))) {
            if (lower.startsWith("0x")) {
                body = body.substring(2);
            } else if (lower.startsWith("#") || lower.startsWith("h")) {
                body = body.substring(1);
            }
        } else if (radix == 2 && (lower.startsWith("0b") || lower.startsWith("b"))) {
            body = lower.startsWith("0b") ? body.substring(2) : body.substring(1);
        } else if (radix == 8 && (lower.startsWith("0o") || lower.startsWith("o"))) {
            body = lower.startsWith("0o") ? body.substring(2) : body.substring(1);
        }
        return negative ? "-" + body.trim() : body.trim();
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal bd) {
            return bd;
        }
        if (number instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (number instanceof Long || number instanceof Integer || number instanceof Short || number instanceof Byte) {
            return BigDecimal.valueOf(number.longValue());
        }
        return new BigDecimal(number.toString());
    }
}
