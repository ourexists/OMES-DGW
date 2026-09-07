package com.omes.dgw.protocol.slmp;

/**
 * SLMP/MC 协议 3E 二进制位单位编解码。
 * <p>
 * 手册：1 点占 4 bit，从首点起先填高半字节；奇数点时低半字节补 0。
 * 示例 M10–M14 = ON OFF ON OFF ON → {@code 10H 10H 10H}。
 */
public final class SlmpBitCodec {

    private SlmpBitCodec() {
    }

    public static int byteCount(int bitCount) {
        if (bitCount < 1) {
            throw new IllegalArgumentException("SLMP 位点数须 >= 1");
        }
        return (bitCount + 1) / 2;
    }

    public static byte[] pack(boolean[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("SLMP 写入位数据不能为空");
        }
        byte[] packed = new byte[byteCount(values.length)];
        for (int i = 0; i < values.length; i++) {
            if (!values[i]) {
                continue;
            }
            packed[i / 2] |= (i & 1) == 0 ? (byte) 0x10 : (byte) 0x01;
        }
        return packed;
    }

    public static boolean[] unpack(byte[] data, int bitCount) {
        int expected = byteCount(bitCount);
        if (data == null || data.length < expected) {
            throw new IllegalArgumentException("SLMP 位读响应长度异常");
        }
        boolean[] bits = new boolean[bitCount];
        for (int i = 0; i < bitCount; i++) {
            int b = data[i / 2] & 0xFF;
            bits[i] = ((i & 1) == 0) ? (b & 0xF0) != 0 : (b & 0x0F) != 0;
        }
        return bits;
    }
}
