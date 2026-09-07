package com.omes.dgw.protocol.slmp;

/**
 * SLMP/MC 二进制通信帧。3E 与 4E 报文主体相同，4E 仅多序列号。
 */
public enum SlmpFrameFormat {
    BINARY_3E,
    BINARY_4E
}
