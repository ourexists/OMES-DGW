package com.omes.dgw.codec.s7;

public record S7ConnectParams(int port, int rack, int slot, int timeoutMs) {

    public static final int DEFAULT_PORT = 102;
    public static final int DEFAULT_RACK = 0;
    public static final int DEFAULT_SLOT = 1;
    public static final int DEFAULT_TIMEOUT_MS = 10_000;

    public S7ConnectParams {
        if (port <= 0 || port > 65535) {
            port = DEFAULT_PORT;
        }
        if (rack < 0 || rack > 7) {
            rack = DEFAULT_RACK;
        }
        if (slot < 0 || slot > 31) {
            slot = DEFAULT_SLOT;
        }
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
    }
}
