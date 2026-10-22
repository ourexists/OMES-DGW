package com.omes.dgw.protocol.slmp;

/**
 * SLMP/MC 协议 3E 帧批量读写上限（参考三菱 MELSEC 通信协议参考手册 SLMP 0401/1401）。
 * <p>
 * Q/L/iQ-R/iQ-L 系列：字单位批量读/写 1–960 字（响应数据最大 1920 字节）；
 * 位单位批量读/写 Q/L 系列 1–3584 点，iQ-R 可达 7168 点。本驱动默认按 Q/L 保守值配置。
 */
public final class SlmpLimits {

    /** 单次字单位批量读/写最大字数（960 字 × 2 字节 = 1920 字节响应数据上限） */
    public static final int MAX_WORDS_PER_REQUEST = 960;

    /** 单次位单位批量读/写最大点数（Q/L/iQ-L 系列保守值） */
    public static final int MAX_BITS_PER_REQUEST = 3584;

    /** 单次通信响应/写入数据区最大字节数 */
    public static final int MAX_DATA_BYTES = 1920;

    private SlmpLimits() {
    }

    public static int clampWords(int wordCount) {
        if (wordCount < 1) {
            throw new IllegalArgumentException("SLMP 字点数须 >= 1");
        }
        if (wordCount > MAX_WORDS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "SLMP 单次字读/写不能超过 " + MAX_WORDS_PER_REQUEST + " 字，当前: " + wordCount);
        }
        return wordCount;
    }

    public static int clampBits(int bitCount) {
        if (bitCount < 1) {
            throw new IllegalArgumentException("SLMP 位点数须 >= 1");
        }
        if (bitCount > MAX_BITS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "SLMP 单次位读/写不能超过 " + MAX_BITS_PER_REQUEST + " 点，当前: " + bitCount);
        }
        return bitCount;
    }

    public static int effectiveMaxWords(int configuredMax) {
        if (configuredMax <= 0) {
            return MAX_WORDS_PER_REQUEST;
        }
        return Math.min(configuredMax, MAX_WORDS_PER_REQUEST);
    }

    public static int effectiveMaxBits(int configuredMax) {
        if (configuredMax <= 0) {
            return MAX_BITS_PER_REQUEST;
        }
        return Math.min(configuredMax, MAX_BITS_PER_REQUEST);
    }
}
