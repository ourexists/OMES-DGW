package com.omes.dgw.protocol.s7;

import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.protocol.s7.S7AddressHelper.S7Address;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class S7NumericCodec {

    private S7NumericCodec() {
    }

    public static Object decode(byte[] data, S7Address address, DevicePoint point) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("S7 读取长度不足");
        }
        PointDataType dataType = point != null ? point.getDataType() : null;
        if (address.bit() || (dataType != null && dataType.isBoolType())) {
            int bit = address.bitNumber() >= 0 ? address.bitNumber() : 0;
            return (data[0] >> bit) & 1;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        if (dataType == null) {
            return switch (address.suffix()) {
                case 'B' -> buffer.get() & 0xFF;
                case 'W' -> buffer.getShort() & 0xFFFF;
                case 'D' -> buffer.getInt() & 0xFFFFFFFFL;
                default -> buffer.getInt();
            };
        }
        return switch (dataType) {
            case INT -> (int) buffer.getShort();
            case UINT -> buffer.getShort() & 0xFFFF;
            case LONG -> buffer.getInt();
            case ULONG -> buffer.getInt() & 0xFFFFFFFFL;
            case REAL -> (double) buffer.getFloat();
            case LREAL -> buffer.getDouble();
            case TEXT8 -> decodeText(data);
            case BOOL, BOOL_INV -> (data[0] & 1);
        };
    }

    public static byte[] encode(Object value, PointDataType dataType, int byteCount) {
        if (dataType == null) {
            throw new IllegalArgumentException("写入缺少数据类型");
        }
        if (dataType == PointDataType.TEXT8) {
            byte[] out = new byte[Math.max(1, byteCount)];
            byte[] src = value == null ? new byte[0] : value.toString().getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(src, 0, out, 0, Math.min(src.length, out.length));
            return out;
        }
        long bits = toLong(value);
        ByteBuffer buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.BIG_ENDIAN);
        switch (dataType) {
            case INT -> buffer.putShort((short) bits);
            case UINT -> buffer.putShort((short) (bits & 0xFFFF));
            case LONG, ULONG -> buffer.putInt((int) bits);
            case REAL -> buffer.putFloat(toNumber(value).floatValue());
            case LREAL -> buffer.putDouble(toNumber(value).doubleValue());
            case BOOL, BOOL_INV -> buffer.put((byte) (toWireBit(value) ? 1 : 0));
            default -> throw new IllegalArgumentException("写入缺少数据类型");
        }
        return buffer.array();
    }

    /**
     * 控制接口传入的布尔已经过点位缩放换算，这里按线侧 0/1 写入，不再取反。
     */
    public static boolean toWireBit(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String text = value.toString().trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "on".equals(text);
    }

    public static byte mergeBit(byte current, int bit, boolean on) {
        if (bit < 0 || bit > 7) {
            throw new IllegalArgumentException("S7 位号超出 0-7: " + bit);
        }
        int value = current & 0xFF;
        if (on) {
            value |= 1 << bit;
        } else {
            value &= ~(1 << bit);
        }
        return (byte) value;
    }

    private static String decodeText(byte[] data) {
        int end = data.length;
        while (end > 0 && (data[end - 1] == 0 || data[end - 1] == ' ')) {
            end--;
        }
        return new String(data, 0, end, StandardCharsets.ISO_8859_1);
    }

    private static long toLong(Object value) {
        return toNumber(value).longValue();
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            throw new IllegalArgumentException("写入值无效");
        }
        return Long.parseLong(value.toString().trim());
    }
}
