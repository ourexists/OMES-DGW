package com.omes.dgw.protocol.modbus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Modbus 文本字符集，对齐 WinCC「文本变量 8/16 位字符集」。
 */
@Getter
public enum ModbusStringCharset {

    /** WinCC：文本变量 8 位字符集（每字符 1 字节，每寄存器 2 字符） */
    CHAR8("8", "8位字符集"),

    /** WinCC：文本变量 16 位字符集（每字符 1 寄存器） */
    CHAR16("16", "16位字符集");

    @JsonValue
    private final String code;

    private final String label;

    ModbusStringCharset(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonCreator
    public static ModbusStringCharset fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (ModbusStringCharset charset : values()) {
            if (charset.code.equals(trimmed) || charset.name().equalsIgnoreCase(trimmed)) {
                return charset;
            }
        }
        return fromLabel(trimmed);
    }

    public static ModbusStringCharset fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (ModbusStringCharset charset : values()) {
            if (charset.label.equals(trimmed)) {
                return charset;
            }
        }
        return null;
    }
}
