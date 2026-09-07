package com.omes.dgw.protocol.fins;

import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.protocol.modbus.ModbusNumericCodec;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Omron 常用地址：D100、DM100、CIO100.05、W50、H0.00、A200 等。
 */
public final class FinsAddressHelper {

    private static final Pattern WORD_PATTERN = Pattern.compile(
            "^(DM|D|CIO|W|H|A|T|C)(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BIT_PATTERN = Pattern.compile(
            "^(DM|D|CIO|W|H|A|T|C)(\\d+)\\.(\\d{1,2})$",
            Pattern.CASE_INSENSITIVE);

    private FinsAddressHelper() {
    }

    public record Parsed(int memoryAreaCode, int wordAddress, Integer bitOffset, boolean bitAccess) {
    }

    public static Parsed parse(String address) {
        if (!StringUtils.hasText(address)) {
            throw new IllegalArgumentException("FINS 地址不能为空");
        }
        String trimmed = address.trim().toUpperCase(Locale.ROOT);
        Matcher bitMatcher = BIT_PATTERN.matcher(trimmed);
        if (bitMatcher.matches()) {
            String area = bitMatcher.group(1);
            int word = Integer.parseInt(bitMatcher.group(2));
            int bit = Integer.parseInt(bitMatcher.group(3));
            if (bit < 0 || bit > 15) {
                throw new IllegalArgumentException("FINS 位偏移无效: " + address);
            }
            return new Parsed(bitAreaCode(area), word, bit, true);
        }
        Matcher wordMatcher = WORD_PATTERN.matcher(trimmed);
        if (wordMatcher.matches()) {
            String area = wordMatcher.group(1);
            int word = Integer.parseInt(wordMatcher.group(2));
            return new Parsed(wordAreaCode(area), word, null, false);
        }
        throw new IllegalArgumentException("不支持的 FINS 地址: " + address);
    }

    public static String normalizeAddress(String address, PointDataType dataType) {
        Parsed parsed = parse(address);
        if (dataType != null && dataType.isBoolType() && !parsed.bitAccess()) {
            return address.trim().toUpperCase(Locale.ROOT) + ".00";
        }
        return address.trim().toUpperCase(Locale.ROOT);
    }

    public static int wordCount(String address, PointDataType dataType, Integer stringLength) {
        if (dataType == null) {
            return 1;
        }
        if (dataType.isBoolType()) {
            return 1;
        }
        return ModbusNumericCodec.registerCount(dataType, stringLength);
    }

    public static boolean isBitAccess(String address, PointDataType dataType) {
        Parsed parsed = parse(address);
        return parsed.bitAccess() || (dataType != null && dataType.isBoolType());
    }

    public static FinsPollingTag toPollingTag(String tagName, String address, DevicePoint point) {
        PointDataType dataType = point != null ? point.getDataType() : null;
        String normalized = dataType != null
                ? normalizeAddress(address, dataType)
                : address.trim().toUpperCase(Locale.ROOT);
        Parsed parsed = parse(normalized);
        boolean bitAccess = parsed.bitAccess() || (dataType != null && dataType.isBoolType());
        int spanWords = bitAccess
                ? 1
                : wordCount(normalized, dataType, point != null ? point.getStringLength() : null);
        int areaCode = bitAccess ? parsed.memoryAreaCode() : parsed.memoryAreaCode();
        if (bitAccess && !parsed.bitAccess() && dataType != null && dataType.isBoolType()) {
            Matcher m = WORD_PATTERN.matcher(normalized);
            if (m.matches()) {
                areaCode = bitAreaCode(m.group(1));
            }
        }
        Integer bitOffset = parsed.bitOffset() != null ? parsed.bitOffset() : (bitAccess ? 0 : null);
        return new FinsPollingTag(tagName, areaCode, parsed.wordAddress(), bitOffset, bitAccess, spanWords, point);
    }

    /**
     * 位区读合并为字读时的目标字区码。
     */
    public static int wordAreaCodeForBitArea(int bitAreaCode) {
        return switch (bitAreaCode) {
            case 0x02 -> 0x82;
            case 0x30 -> 0xB0;
            case 0x31 -> 0xB1;
            case 0x32 -> 0xB2;
            case 0x09 -> 0x89;
            case 0x33 -> 0xB3;
            default -> bitAreaCode;
        };
    }

    private static int wordAreaCode(String area) {
        return switch (area.toUpperCase(Locale.ROOT)) {
            case "D", "DM" -> 0x82;
            case "CIO" -> 0xB0;
            case "W" -> 0xB1;
            case "H" -> 0xB2;
            case "A" -> 0xB3;
            case "T", "C" -> 0x89;
            default -> throw new IllegalArgumentException("不支持的 FINS 内存区: " + area);
        };
    }

    private static int bitAreaCode(String area) {
        return switch (area.toUpperCase(Locale.ROOT)) {
            case "CIO" -> 0x30;
            case "W" -> 0x31;
            case "H" -> 0x32;
            case "T", "C" -> 0x09;
            case "D", "DM" -> 0x02;
            case "A" -> 0x33;
            default -> throw new IllegalArgumentException("不支持的 FINS 位区: " + area);
        };
    }
}
