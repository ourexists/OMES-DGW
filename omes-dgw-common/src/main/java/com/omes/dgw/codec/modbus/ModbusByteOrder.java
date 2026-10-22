package com.omes.dgw.codec.modbus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * Modbus 多寄存器数值字节序（32 位浮点/整型常用 ABCD/BADC 等命名）。
 */
@Getter
public enum ModbusByteOrder {

    ABCD("ABCD", "大端(ABCD)"),
    DCBA("DCBA", "小端(DCBA)"),
    CDAB("CDAB", "字交换(CDAB)"),
    BADC("BADC", "字节交换(BADC)");

    @JsonValue
    private final String code;

    private final String label;

    ModbusByteOrder(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static List<ModbusByteOrder> all() {
        return Arrays.asList(values());
    }

    /**
     * PLC4X {@code default-payload-byte-order} 连接参数；字交换需应用层组字。
     */
    public String toPlc4xPayloadParam() {
        return switch (this) {
            case ABCD -> "BIG_ENDIAN";
            case DCBA -> "LITTLE_ENDIAN_BYTE_SWAP";
            case CDAB -> "LITTLE_ENDIAN";
            case BADC -> "BIG_ENDIAN_BYTE_SWAP";
        };
    }

    @JsonCreator
    public static ModbusByteOrder fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (ModbusByteOrder order : values()) {
            if (order.code.equalsIgnoreCase(trimmed) || order.name().equalsIgnoreCase(trimmed)) {
                return order;
            }
        }
        throw new IllegalArgumentException("不支持的字节序: " + value);
    }

    public static ModbusByteOrder fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (ModbusByteOrder order : values()) {
            if (order.label.equals(trimmed)) {
                return order;
            }
        }
        return fromCode(trimmed);
    }
}
