package com.omes.dgw.protocol;

import org.apache.plc4x.java.api.value.PlcValue;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 PLC4X {@link PlcValue} 转为 Jackson / Redis 可序列化的 Java 类型。
 */
public final class PlcValueConverter {

    private PlcValueConverter() {
    }

    /**
     * 是否仍需 PLC4X 转换（Modbus 轮询等路径在协议层已转为 Java 时可跳过 {@link #toJava}）。
     */
    public static boolean needsConversion(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof PlcValue) {
            return true;
        }
        if (value instanceof Short || value instanceof Byte) {
            return true;
        }
        return value instanceof List<?> || value.getClass().isArray();
    }

    public static Object toJava(Object value) {
        if (value == null) {
            return null;
        }
        if (!needsConversion(value)) {
            return value;
        }
        if (value instanceof PlcValue plcValue) {
            return toJava(plcValue.getObject());
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(normalizeRegisterElement(toJava(item)));
            }
            return out;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] out = new Object[length];
            for (int i = 0; i < length; i++) {
                out[i] = normalizeRegisterElement(toJava(Array.get(value, i)));
            }
            return out;
        }
        return normalizeRegisterElement(value);
    }

    /** PLC4X 块读常返回 signed short，转为无符号 16 位 int 供 Modbus 组字。 */
    private static Object normalizeRegisterElement(Object value) {
        if (value instanceof Short s) {
            return s.intValue() & 0xFFFF;
        }
        if (value instanceof Byte b) {
            return b.intValue() & 0xFF;
        }
        return value;
    }
}
