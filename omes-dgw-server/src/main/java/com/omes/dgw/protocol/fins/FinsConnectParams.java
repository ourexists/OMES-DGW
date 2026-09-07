package com.omes.dgw.protocol.fins;

public record FinsConnectParams(
        int port,
        int localNode,
        int remoteNode,
        int timeoutMs
) {

    public static final int DEFAULT_PORT = 9600;
    public static final int DEFAULT_LOCAL_NODE = 0;
    public static final int DEFAULT_TIMEOUT_MS = 10_000;
    /** 低于该值的超时在工业现场几乎必然失败（如前端的误填 30ms） */
    public static final int MIN_TIMEOUT_MS = 1_000;

    public FinsConnectParams {
        if (port <= 0) {
            port = DEFAULT_PORT;
        }
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        } else if (timeoutMs < MIN_TIMEOUT_MS) {
            timeoutMs = MIN_TIMEOUT_MS;
        }
        if (localNode < 0 || localNode > 254) {
            localNode = DEFAULT_LOCAL_NODE;
        }
        if (remoteNode < 0 || remoteNode > 254) {
            throw new IllegalArgumentException("FINS 目标节点号无效: " + remoteNode);
        }
    }
}
