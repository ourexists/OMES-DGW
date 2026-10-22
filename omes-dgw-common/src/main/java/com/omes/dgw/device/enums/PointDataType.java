package com.omes.dgw.device.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 点位数据类型：{@link #label} 为 PLC/TIA 类型名，{@link #description} 为中文说明。
 * <p>
 * {@link #supportsEngRange} 等字段及默认值供前端表单渲染；采集逻辑仍以运行时配置为准。
 * <p>
 * 整型位宽：INT/UINT=16、LONG/ULONG=32；另含 REAL/LREAL/TEXT8。
 */
@Getter
public enum PointDataType {

    BOOL("Bool", "布尔型", 1,
            false, false, false, false,
            null, null, null, BigDecimal.ONE, 0,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA),

    /** 与 {@link #BOOL} 寄存器/线圈语义相同，采集与写入时对位值取反（现场常开/常闭反解）。 */
    BOOL_INV("BoolInv", "布尔型(反相)", 1,
            false, false, false, false,
            null, null, null, BigDecimal.ONE, 0,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA),

    INT("Int", "有符号16位整数", 16,
            true, false, true, true,
            BigDecimal.valueOf(-32768), BigDecimal.valueOf(32767), null, BigDecimal.ONE, 0,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA),

    UINT("UInt", "无符号16位整数", 16,
            true, false, true, true,
            BigDecimal.ZERO, BigDecimal.valueOf(65535), null, BigDecimal.ONE, 0,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA),

    LONG("Long", "有符号32位整数", 32,
            true, true, true, true,
            BigDecimal.valueOf(-2147483648L), BigDecimal.valueOf(2147483647L),
            ModbusByteOrder.CDAB, BigDecimal.ONE, 0,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA),

    ULONG("ULong", "无符号32位整数", 32,
            true, true, true, true,
            BigDecimal.ZERO, BigDecimal.valueOf(4294967295L),
            ModbusByteOrder.CDAB, BigDecimal.ONE, 0,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA),

    REAL("Real", "32位浮点数", 32,
            true, true, true, true,
            null, null, ModbusByteOrder.CDAB, BigDecimal.ONE, 2,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA),

    LREAL("LReal", "64位浮点数", 64,
            true, true, true, true,
            null, null, ModbusByteOrder.CDAB, BigDecimal.ONE, 4,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA),

    TEXT8("Text8", "8位文本", 8,
            false, false, false, false,
            null, null, null, BigDecimal.ONE, 0,
            DeviceProtocol.MODBUS_TCP, DeviceProtocol.FINS_TCP, DeviceProtocol.SLMP_TCP, DeviceProtocol.S7_TCP, DeviceProtocol.OPC_UA);

    @EnumValue
    @JsonValue
    private final String code;

    /** PLC/TIA 类型名（Excel 导入、下拉选项） */
    private final String label;

    /** 中文说明 */
    private final String description;

    /** 标称位宽 */
    private final int bitWidth;

    /** 是否支持配置工程值下限、工程值上限 */
    private final boolean supportsEngRange;

    /** 是否支持配置 Modbus 多寄存器字节序 */
    private final boolean supportsByteOrder;

    /** 是否支持配置缩放系数 */
    private final boolean supportsScaleFactor;

    /** 是否支持配置小数位数（工程值展示精度；整型缩放后也可有小数） */
    private final boolean supportsDecimalPlaces;

    /** 默认工程值下限（仅 {@link #supportsEngRange} 为 true 时有意义） */
    private final BigDecimal defaultEngValueMin;

    /** 默认工程值上限（仅 {@link #supportsEngRange} 为 true 时有意义） */
    private final BigDecimal defaultEngValueMax;

    /** 默认字节序（仅 {@link #supportsByteOrder} 为 true 时有意义） */
    private final ModbusByteOrder defaultByteOrder;

    /** 默认缩放系数 */
    private final BigDecimal defaultScaleFactor;

    /** 默认小数位数 */
    private final int defaultDecimalPlaces;

    private final Set<DeviceProtocol> protocols;

    PointDataType(
            String label,
            String description,
            int bitWidth,
            boolean supportsEngRange,
            boolean supportsByteOrder,
            boolean supportsScaleFactor,
            boolean supportsDecimalPlaces,
            BigDecimal defaultEngValueMin,
            BigDecimal defaultEngValueMax,
            ModbusByteOrder defaultByteOrder,
            BigDecimal defaultScaleFactor,
            int defaultDecimalPlaces,
            DeviceProtocol... protocols
    ) {
        this.code = name();
        this.label = label;
        this.description = description;
        this.bitWidth = bitWidth;
        this.supportsEngRange = supportsEngRange;
        this.supportsByteOrder = supportsByteOrder;
        this.supportsScaleFactor = supportsScaleFactor;
        this.supportsDecimalPlaces = supportsDecimalPlaces;
        this.defaultEngValueMin = defaultEngValueMin;
        this.defaultEngValueMax = defaultEngValueMax;
        this.defaultByteOrder = defaultByteOrder;
        this.defaultScaleFactor = defaultScaleFactor;
        this.defaultDecimalPlaces = defaultDecimalPlaces;
        this.protocols = EnumSet.copyOf(Arrays.asList(protocols));
    }

    public boolean supports(DeviceProtocol protocol) {
        return protocol != null && protocols.contains(protocol);
    }

    public static List<PointDataType> forProtocol(DeviceProtocol protocol) {
        if (protocol == null) {
            return List.of();
        }
        return Arrays.stream(values())
                .filter(type -> type.supports(protocol))
                .toList();
    }

    @JsonCreator
    public static PointDataType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (PointDataType type : values()) {
            if (type.code.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        PointDataType fromLabel = fromLabel(trimmed);
        if (fromLabel != null) {
            return fromLabel;
        }
        PointDataType fromAlias = fromAlias(trimmed);
        if (fromAlias != null) {
            return fromAlias;
        }
        throw new IllegalArgumentException("不支持的数据类型: " + value);
    }

    /**
     * Excel/粘贴等常见别名。
     * <p>
     * 位宽：INT=16、LONG=32。西门子 {@code INT}/{@code UINT}→本枚举 INT/UINT；
     * {@code DINT}/{@code UDINT}→LONG/ULONG。
     */
    private static PointDataType fromAlias(String value) {
        return switch (value.toLowerCase()) {
            case "sint", "int8", "short", "int16", "integer16", "integer" -> INT;
            case "usint", "uint8", "byte", "ushort", "uint16", "word" -> UINT;
            case "dint", "int32", "long" -> LONG;
            case "udint", "uint32", "dword", "ulong" -> ULONG;
            case "real16", "16位浮点数", "16位浮点", "浮点",
                 "float", "float32", "fp32", "f32", "32位浮点数" -> REAL;
            case "double", "float64", "fp64", "f64", "64位浮点数" -> LREAL;
            case "string", "str", "字符串" -> TEXT8;
            default -> null;
        };
    }

    public static PointDataType fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (PointDataType type : values()) {
            if (type.label.equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        return null;
    }

    /** Modbus 文本类（须配置 {@code stringLength}） */
    public boolean isModbusStringType() {
        return this == TEXT8;
    }

    public boolean isBoolType() {
        return this == BOOL || this == BOOL_INV;
    }

    /** 采集时对原始位/寄存器值取反后再作为工程布尔值。 */
    public boolean isBoolInverted() {
        return this == BOOL_INV;
    }

    /**
     * 无符号整型支持 BCD 转码（Packed BCD：0x1234→1234）。
     * 有符号整型、浮点、布尔、文本等不适用。
     */
    public boolean isSupportsBcdConvert() {
        return isUnsignedIntegerType();
    }

    /**
     * 整型点位支持位域拆分（bitField：A/B/C/D/BA/DC）。
     */
    public boolean isSupportsBitField() {
        return isIntegerType();
    }

    private boolean isIntegerType() {
        return switch (this) {
            case INT, UINT, LONG, ULONG -> true;
            default -> false;
        };
    }

    private boolean isUnsignedIntegerType() {
        return switch (this) {
            case UINT, ULONG -> true;
            default -> false;
        };
    }

    public boolean getDefaultBcdConvert() {
        return false;
    }
}
