package com.omes.dgw.protocol.fins;

/**
 * FINS/TCP 单次读写工程上限（参考欧姆龙 FINS 命令 0101/0102 及现场经验）。
 * <p>
 * 协议硬上限：字读写约 999~1000 word/帧（endCode 0x0105 response too large）；UDP 单帧有效载荷约 1.5KB。
 * 默认读/写采用更保守的工程值，避免超时、partial response 与 PLC 拒绝。
 */
public final class FinsLimits {

    /** 协议层字读写硬上限（约 999~1000 words/command） */
    public static final int MAX_WORDS_ABSOLUTE = 999;

    /** 协议层位读硬上限（视 PLC/区而定，取保守值） */
    public static final int MAX_BITS_ABSOLUTE = 1000;

    /** 默认 DM/字区单次读上限（工程推荐 100~300） */
    public static final int DEFAULT_WORDS_PER_READ = 250;

    /** 默认 DM/字区单次写上限（工程推荐 20~100） */
    public static final int DEFAULT_WORDS_PER_WRITE = 80;

    /** 默认 CIO/HR 等位区批量读上限（工程推荐 200~500） */
    public static final int DEFAULT_BITS_PER_READ = 500;

    private FinsLimits() {
    }

    public static int effectiveMaxWords(int configured, int absoluteCap) {
        if (configured <= 0) {
            configured = absoluteCap;
        }
        return Math.min(Math.min(configured, absoluteCap), MAX_WORDS_ABSOLUTE);
    }

    public static int effectiveMaxBits(int configured) {
        if (configured <= 0) {
            configured = DEFAULT_BITS_PER_READ;
        }
        return Math.min(configured, MAX_BITS_ABSOLUTE);
    }

    public static int clampWords(int wordCount, int maxPerRequest) {
        if (wordCount < 1) {
            throw new IllegalArgumentException("FINS 字点数须 >= 1");
        }
        int cap = effectiveMaxWords(maxPerRequest, MAX_WORDS_ABSOLUTE);
        if (wordCount > cap) {
            throw new IllegalArgumentException(
                    "FINS 单次字读/写不能超过 " + cap + " 字，当前: " + wordCount);
        }
        return wordCount;
    }

    public static int clampBits(int bitCount, int maxPerRequest) {
        if (bitCount < 1) {
            throw new IllegalArgumentException("FINS 位点数须 >= 1");
        }
        int cap = effectiveMaxBits(maxPerRequest);
        if (bitCount > cap) {
            throw new IllegalArgumentException(
                    "FINS 单次位读不能超过 " + cap + " 点，当前: " + bitCount);
        }
        return bitCount;
    }
}
