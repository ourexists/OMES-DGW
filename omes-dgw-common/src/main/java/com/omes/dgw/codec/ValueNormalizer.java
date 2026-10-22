package com.omes.dgw.codec;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 将协议层返回值转为 Jackson / Redis 可序列化的 Java 类型。
 * <p>
 * 不依赖 PLC4X：若 classpath 上存在 {@code PlcValue}，通过反射解包；盒子门户无 PLC4X 时仅处理 Short/Byte/List/数组。
 */
public final class ValueNormalizer {

    private static final String PLC_VALUE = "org.apache.plc4x.java.api.value.PlcValue";

    private ValueNormalizer() {
    }

    public static boolean needsConversion(Object value) {
        if (value == null) {
            return false;
        }
        if (isPlcValue(value)) {
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
        if (isPlcValue(value)) {
            return toJava(unwrapPlcValue(value));
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

    private static boolean isPlcValue(Object value) {
        for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                if (PLC_VALUE.equals(iface.getName())) {
                    return true;
                }
            }
            if (PLC_VALUE.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private static Object unwrapPlcValue(Object value) {
        try {
            Method getObject = value.getClass().getMethod("getObject");
            return getObject.invoke(value);
        } catch (ReflectiveOperationException e) {
            return value;
        }
    }
}
