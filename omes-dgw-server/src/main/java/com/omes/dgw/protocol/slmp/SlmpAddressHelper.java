package com.omes.dgw.protocol.slmp;

import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.protocol.modbus.ModbusNumericCodec;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析三菱 MC 协议常用地址：D100、D100.5、M100、X10、Y20、W100、B100、R100、ZR100 等。
 * <p>X/Y 为八进制编号；W/B/ZR/SW/SB 为十六进制（与 GX Works 一致）。
 */
public final class SlmpAddressHelper {

    private static final Pattern WORD_PATTERN = Pattern.compile(
            "^(D|W|R|ZR|SD|SW|TN|CN|SN)(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BIT_IN_WORD_PATTERN = Pattern.compile(
            "^(D|W|R|ZR|SD|SW)(\\d+)\\.(\\d{1,2})$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BIT_DEVICE_PATTERN = Pattern.compile(
            "^(M|L|F|V|B|SM|SB|X|Y|TS|TC|SS|SC|CS|CC|DX|DY|S)(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private SlmpAddressHelper() {
    }

    public record Parsed(int deviceCode, int deviceNumber, boolean bitAccess, int bitInWord) {
        public Parsed(int deviceCode, int deviceNumber, boolean bitAccess) {
            this(deviceCode, deviceNumber, bitAccess, -1);
        }

        /** 字软元件上的位（D100.5），应用字读再取位，不能走 0401 位单位。 */
        public boolean wordDeviceBit() {
            return bitInWord >= 0 && bitInWord <= 15;
        }
    }

    public static Parsed parse(String address) {
        if (!StringUtils.hasText(address)) {
            throw new IllegalArgumentException("SLMP 地址不能为空");
        }
        String trimmed = address.trim().toUpperCase(Locale.ROOT);

        Matcher bitInWord = BIT_IN_WORD_PATTERN.matcher(trimmed);
        if (bitInWord.matches()) {
            String area = bitInWord.group(1);
            int word = parseDeviceNumber(area, bitInWord.group(2));
            int bit = Integer.parseInt(bitInWord.group(3));
            if (bit < 0 || bit > 15) {
                throw new IllegalArgumentException("SLMP 位偏移无效: " + address);
            }
            return new Parsed(wordDeviceCode(area), word, true, bit);
        }

        Matcher bitDevice = BIT_DEVICE_PATTERN.matcher(trimmed);
        if (bitDevice.matches()) {
            String area = bitDevice.group(1);
            int num = parseDeviceNumber(area, bitDevice.group(2));
            return new Parsed(bitDeviceCode(area), num, true);
        }

        Matcher wordMatcher = WORD_PATTERN.matcher(trimmed);
        if (wordMatcher.matches()) {
            String area = wordMatcher.group(1);
            int num = parseDeviceNumber(area, wordMatcher.group(2));
            return new Parsed(wordDeviceCode(area), num, false);
        }

        throw new IllegalArgumentException("不支持的 SLMP 地址: " + address);
    }

    public static String normalizeAddress(String address, PointDataType dataType) {
        Parsed parsed = parse(address);
        if (dataType != null && dataType.isBoolType() && !parsed.bitAccess() && isWordArea(parsed.deviceCode())) {
            return address.trim().toUpperCase(Locale.ROOT) + ".0";
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
        if (parsed.wordDeviceBit() || (dataType != null && dataType.isBoolType() && isWordArea(parsed.deviceCode()))) {
            return false;
        }
        return parsed.bitAccess() || (dataType != null && dataType.isBoolType());
    }

    public static SlmpPollingTag toPollingTag(String tagName, String address, DevicePoint point) {
        Parsed parsed = parse(address);
        PointDataType dataType = point != null ? point.getDataType() : null;
        int bitInWord = parsed.bitInWord();
        boolean bitAccess = parsed.bitAccess() && !parsed.wordDeviceBit();
        if (dataType != null && dataType.isBoolType() && isWordArea(parsed.deviceCode()) && !parsed.bitAccess()) {
            bitInWord = 0;
            bitAccess = false;
        }
        int spanUnits = bitAccess
                ? 1
                : wordCount(address, dataType, point != null ? point.getStringLength() : null);
        return new SlmpPollingTag(
                tagName, parsed.deviceCode(), parsed.deviceNumber(), bitAccess, spanUnits, point, bitInWord);
    }

    private static int parseDeviceNumber(String area, String numStr) {
        String upper = area.toUpperCase(Locale.ROOT);
        if ("X".equals(upper) || "Y".equals(upper) || "DX".equals(upper) || "DY".equals(upper)) {
            return Integer.parseInt(numStr, 8);
        }
        if ("W".equals(upper) || "B".equals(upper) || "ZR".equals(upper)
                || "SW".equals(upper) || "SB".equals(upper)) {
            return Integer.parseInt(numStr, 16);
        }
        return Integer.parseInt(numStr);
    }

    private static boolean isWordArea(int deviceCode) {
        return deviceCode == 0xA8 || deviceCode == 0xB4 || deviceCode == 0xAF || deviceCode == 0xB0
                || deviceCode == 0xA9 || deviceCode == 0xB5;
    }

    private static int wordDeviceCode(String area) {
        return switch (area.toUpperCase(Locale.ROOT)) {
            case "D" -> 0xA8;
            case "W" -> 0xB4;
            case "R" -> 0xAF;
            case "ZR" -> 0xB0;
            case "SD" -> 0xA9;
            case "SW" -> 0xB5;
            case "TN" -> 0xC2;
            case "CN" -> 0xC5;
            case "SN" -> 0xC8;
            default -> throw new IllegalArgumentException("不支持的 SLMP 字设备: " + area);
        };
    }

    private static int bitDeviceCode(String area) {
        return switch (area.toUpperCase(Locale.ROOT)) {
            case "M" -> 0x90;
            case "L" -> 0x92;
            case "F" -> 0x93;
            case "V" -> 0x94;
            case "B" -> 0xA0;
            case "SM" -> 0x91;
            case "SB" -> 0xA1;
            case "X" -> 0x9C;
            case "Y" -> 0x9D;
            case "TS" -> 0xC1;
            case "TC" -> 0xC0;
            case "SS" -> 0xC7;
            case "SC" -> 0xC6;
            case "CS" -> 0xC4;
            case "CC" -> 0xC3;
            case "DX" -> 0xA2;
            case "DY" -> 0xA3;
            case "S" -> 0x98;
            default -> throw new IllegalArgumentException("不支持的 SLMP 位设备: " + area);
        };
    }
}
