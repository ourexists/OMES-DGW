package com.omes.dgw.protocol.slmp;

public record SlmpConnectParams(
        int port,
        int networkNo,
        int stationNo,
        int timeoutMs
) {

    public static final int DEFAULT_PORT = 5000;
    public static final int DEFAULT_NETWORK_NO = 0;
    /** MC 协议 PC 号，0xFF 表示本站 */
    public static final int DEFAULT_STATION_NO = 0xFF;
    public static final int DEFAULT_TIMEOUT_MS = 10_000;
    public static final int MIN_TIMEOUT_MS = 1_000;

    public SlmpConnectParams {
        if (port <= 0) {
            port = DEFAULT_PORT;
        }
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        } else if (timeoutMs < MIN_TIMEOUT_MS) {
            timeoutMs = MIN_TIMEOUT_MS;
        }
        if (networkNo < 0 || networkNo > 255) {
            networkNo = DEFAULT_NETWORK_NO;
        }
        if (stationNo < 0 || stationNo > 255) {
            stationNo = DEFAULT_STATION_NO;
        }
    }
}
