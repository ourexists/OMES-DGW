package com.omes.dgw.protocol.modbus;

import com.omes.dgw.device.enums.PointDataType;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modbus 地址转换：点位填写 PLC/网关 Modbus 表上的惯例号（30001、40120…），再转为 PLC4X 访问串。
 * <p>
 * 线侧 PDU（0 基）与惯例号关系（与多数 PLC 手册一致）：
 * <ul>
 *   <li>3x：{@code PDU = 惯例号 - 30001}</li>
 *   <li>4x：{@code PDU = 惯例号 - 40001}</li>
 * </ul>
 * 轮询/写入时 PLC4X 标签号为 {@code 惯例号 - 区基址}（30000 / 40000 / 10000）。
 */
public final class ModbusAddressHelper {

    private static final Pattern SIMPLE_ADDR = Pattern.compile(
            "^(holding-register|input-register|coil|discrete-input):(\\d+)$",
            Pattern.CASE_INSENSITIVE
    );

    static final Pattern TYPED_ADDR = Pattern.compile(
            "^(holding-register|input-register|coil|discrete-input):(\\d+):([A-Za-z0-9_]+)$",
            Pattern.CASE_INSENSITIVE
    );

    private ModbusAddressHelper() {
    }

    /** 简单 PLC4X 地址（无类型后缀、无区间语法）解析结果。 */
    public record SimplePlcAddress(String areaName, int pduOffset) {
    }

    /**
     * 解析已转换的 PLC4X 简单地址（如 {@code holding-register:1}），返回区名与 0 基 PDU 偏移。
     */
    public static Optional<SimplePlcAddress> parseSimplePlcAddress(String convertedAddress) {
        if (!StringUtils.hasText(convertedAddress)) {
            return Optional.empty();
        }
        String normalized = stripDataTypeSuffix(convertedAddress);
        Matcher m = SIMPLE_ADDR.matcher(normalized);
        if (!m.matches()) {
            return Optional.empty();
        }
        String areaName = m.group(1).toLowerCase();
        ModbusRegisterArea kind = ModbusRegisterArea.fromPlcArea(areaName);
        int pduOffset = kind != null
                ? pduOffsetFromPlc4xAddress(normalized, kind)
                : Integer.parseInt(m.group(2));
        if (pduOffset < 0) {
            return Optional.empty();
        }
        return Optional.of(new SimplePlcAddress(areaName, pduOffset));
    }

    /**
     * 写入用 PLC4X 地址：与轮询块读一致，保持/输入寄存器区用 {@code area:tag[n]}，
     * 数值在应用侧按点位字节序（ABCD/CDAB…）组字，不使用 {@code :REAL} 等类型后缀。
     */
    public static String toPlc4xWriteAddress(
            String address, PointDataType dataType, int registerCount
    ) {
        if (!StringUtils.hasText(address)) {
            return address;
        }
        String base = stripDataTypeSuffix(convertLegacyAddress(address.trim()));
        if (base.contains("[")) {
            return base;
        }
        if (isRegisterArea(base)) {
            int count = Math.max(1, registerCount);
            return base + "[" + count + "]";
        }
        return toPlc4xAddress(address, dataType);
    }

    private static boolean isRegisterArea(String plcBase) {
        return plcBase.startsWith("holding-register:") || plcBase.startsWith("input-register:");
    }

    public static String toPlc4xAddress(String address, PointDataType dataType) {
        if (!StringUtils.hasText(address)) {
            return address;
        }
        String base = stripDataTypeSuffix(convertLegacyAddress(address.trim()));
        if (dataType != null && dataType.isModbusStringType()) {
            return base;
        }
        if (ModbusNumericCodec.isMultiRegister(dataType)) {
            return base;
        }
        return appendPlc4xDataTypeSuffix(base, dataType);
    }

    /**
     * 单点/回退读用的 PLC4X 地址：与 {@link #toPlc4xWriteAddress} 相同，连续读 {@code [n]} 个寄存器后按字节序解码。
     */
    public static String toPlc4xReadAddress(String address, PointDataType dataType) {
        return toPlc4xReadAddress(address, dataType, null);
    }

    public static String toPlc4xReadAddress(
            String address, PointDataType dataType, Integer stringLength
    ) {
        return toPlc4xWriteAddress(
                address, dataType, ModbusNumericCodec.registerCount(dataType, stringLength));
    }

    private static String appendPlc4xDataTypeSuffix(String base, PointDataType dataType) {
        if (!StringUtils.hasText(base) || dataType == null) {
            return base;
        }
        Matcher m = SIMPLE_ADDR.matcher(base);
        if (m.matches()) {
            return base + ":" + plc4xDataType(dataType);
        }
        return base;
    }

    public static String stripDataTypeSuffix(String address) {
        if (!StringUtils.hasText(address)) {
            return address;
        }
        Matcher m = TYPED_ADDR.matcher(address.trim().toLowerCase());
        if (m.matches()) {
            return m.group(1) + ":" + m.group(2);
        }
        return address.trim().toLowerCase();
    }


    /**
     * 从点位/写入地址解析寄存器区（PLC4X 区前缀或惯例号 3xxxx/4xxxx 等）；无法识别时返回 null。
     */
    /** 去掉 PLC4X 连续读写数量后缀，如 {@code holding-register:1[2]} → {@code holding-register:1}。 */
    public static String stripQuantitySuffix(String address) {
        if (!StringUtils.hasText(address)) {
            return address;
        }
        String trimmed = address.trim();
        int bracket = trimmed.indexOf('[');
        if (bracket > 0) {
            return trimmed.substring(0, bracket).trim();
        }
        return trimmed;
    }

    public static ModbusRegisterArea registerAreaFromAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        String trimmed = address.trim();
        String converted = stripQuantitySuffix(stripDataTypeSuffix(convertLegacyAddress(trimmed)));
        Matcher m = SIMPLE_ADDR.matcher(converted);
        if (m.matches()) {
            return ModbusRegisterArea.fromPlcArea(m.group(1));
        }
        for (String candidate : new String[]{trimmed, converted}) {
            try {
                ModbusRegisterArea area = ModbusRegisterArea.fromLegacyNumber(Integer.parseInt(candidate));
                if (area != null) {
                    return area;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * 写入前校验地址区与数据类型是否匹配：线圈/离散量区仅 BOOL；寄存器区不支持按位区语义写入的类型。
     *
     * @return 校验失败原因；空表示通过
     */
    public static Optional<String> validateWriteAddressDataType(
            String address, PointDataType dataType, Integer stringLength
    ) {
        if (!StringUtils.hasText(address) || dataType == null) {
            return Optional.empty();
        }
        ModbusRegisterArea area = registerAreaFromAddress(address);
        if (area == null) {
            return Optional.of("无法识别 Modbus 地址: " + address.trim());
        }
        if (area.isBitArea()) {
            if (!dataType.isBoolType()) {
                return Optional.of(
                        area.getLabel() + " 地址仅支持布尔类型，当前为 " + dataType.getLabel());
            }
            return Optional.empty();
        }
        if (dataType.isModbusStringType()) {
            int regCount = ModbusNumericCodec.registerCount(dataType, stringLength);
            if (regCount > area.getPduMaxQuantity()) {
                return Optional.of("字符串占用 " + regCount + " 个寄存器，超过 "
                        + area.getLabel() + " 单次写入上限 " + area.getPduMaxQuantity());
            }
        }
        return Optional.empty();
    }

    public static String convertLegacyAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return address;
        }
        String s = address.trim();
        if (s.startsWith("holding-register:") || s.startsWith("input-register:")
                || s.startsWith("coil:") || s.startsWith("discrete-input:")) {
            return s.toLowerCase();
        }
        try {
            int num = Integer.parseInt(s);
            ModbusRegisterArea area = ModbusRegisterArea.fromLegacyNumber(num);
            if (area == null) {
                return s;
            }
            int tag = toPlc4xTagNumber(num, area);
            return area.plcAreaPrefix() + ":" + tag;
        } catch (NumberFormatException ignored) {
        }
        return s;
    }

    /** 惯例号 → 区内 0 基 PDU 偏移（{@code PDU = 惯例号 - pduOriginLegacy}，如 40121→120）。 */
    public static int pduOffsetFromLegacyNumber(int legacy, ModbusRegisterArea area) {
        if (area == null) {
            throw new IllegalArgumentException("area");
        }
        return legacy - area.pduOriginLegacy();
    }

    /** PLC4X 地址串（已转换）→ 区内 0 基 PDU 偏移。 */
    public static int pduOffsetFromPlc4xAddress(String plc4xAddress, ModbusRegisterArea area) {
        if (!StringUtils.hasText(plc4xAddress) || area == null) {
            return -1;
        }
        String legacy = toLegacyAddress(stripDataTypeSuffix(plc4xAddress.trim()));
        try {
            return pduOffsetFromLegacyNumber(Integer.parseInt(legacy), area);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * 扫描/配置起始值：若 ≥ 区首惯例号则按惯例号解析，否则视为区内 0 基 PDU 偏移（与点位地址 40001 等一致）。
     */
    public static int resolvePduOffsetStart(ModbusRegisterArea area, int startAddress) {
        if (area == null || startAddress < 0) {
            return startAddress;
        }
        if (startAddress >= area.pduOriginLegacy()) {
            return pduOffsetFromLegacyNumber(startAddress, area);
        }
        return startAddress;
    }

    /**
     * 区内 0 基 PDU 偏移 → Modbus 惯例号（如 40001、30001、10001；离散输入为 1 起）。
     */
    public static int legacyNumberFromPduOffset(ModbusRegisterArea area, int pduOffset) {
        if (area == null) {
            throw new IllegalArgumentException("area");
        }
        return area.pduOriginLegacy() + pduOffset;
    }

    public static String legacyAddressFromPduOffset(ModbusRegisterArea area, int pduOffset) {
        return String.valueOf(legacyNumberFromPduOffset(area, pduOffset));
    }

    /** 区内 0 基 PDU 偏移 → PLC4X 标签号（与 {@link #convertLegacyAddress} 一致）。 */
    public static int plc4xTagFromPduOffset(ModbusRegisterArea area, int pduOffset) {
        return toPlc4xTagNumber(legacyNumberFromPduOffset(area, pduOffset), area);
    }

    /** 区内 0 基 PDU 偏移 → PLC4X 地址串（无数据类型后缀，如 holding-register:1）。 */
    public static String plc4xAddressFromPduOffset(ModbusRegisterArea area, int pduOffset) {
        return convertLegacyAddress(legacyAddressFromPduOffset(area, pduOffset));
    }

    public static String toLegacyAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return address;
        }
        String trimmed = address.trim();
        try {
            Integer.parseInt(trimmed);
            return trimmed;
        } catch (NumberFormatException ignored) {
        }
        String base = stripDataTypeSuffix(trimmed);
        Matcher m = SIMPLE_ADDR.matcher(base);
        if (!m.matches()) {
            return trimmed;
        }
        ModbusRegisterArea area = ModbusRegisterArea.fromPlcArea(m.group(1));
        if (area == null) {
            return trimmed;
        }
        int tag = Integer.parseInt(m.group(2));
        return String.valueOf(toLegacyNumber(tag, area));
    }

    /**
     * 带校准的 PLC4X 标签号：{@code max(1, 惯例号 - 区基址)}。
     */
    public static int toPlc4xTagNumber(int legacyAddress, ModbusRegisterArea area) {
        return Math.max(1, pduOffsetFromLegacyNumber(legacyAddress, area) + 1);
    }

    static int toLegacyNumber(int plc4xTag, ModbusRegisterArea area) {
        return legacyNumberFromPduOffset(area, plc4xTag - 1);
    }

    private static String plc4xDataType(PointDataType dataType) {
        if (dataType == null) {
            return "INT";
        }
        return switch (dataType) {
            case BOOL, BOOL_INV -> "BOOL";
            case INT -> "INT";
            case UINT -> "UINT";
            case LONG -> "DINT";
            case ULONG -> "UDINT";
            case REAL -> "REAL";
            case LREAL -> "LREAL";
            case TEXT8 -> "STRING";
        };
    }
}
