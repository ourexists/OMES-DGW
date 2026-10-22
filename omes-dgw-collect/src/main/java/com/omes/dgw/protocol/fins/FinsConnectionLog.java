package com.omes.dgw.protocol.fins;

import lombok.extern.slf4j.Slf4j;

/**
 * FINS/TCP 连接相关统一日志（便于现场 grep {@code FINS/TCP}）。
 */
@Slf4j
public final class FinsConnectionLog {

    private FinsConnectionLog() {
    }

    public static void tcpConnecting(String mode, String host, int port, int localNode, int remoteNode, int timeoutMs) {
        log.info("FINS/TCP connecting mode={} host={} port={} cfgLocalNode={} cfgRemoteNode={} timeoutMs={}",
                mode, host, port, localNode, remoteNode, timeoutMs);
    }

    public static void handshakeOk(
            String mode,
            String host,
            int port,
            int cfgLocalNode,
            int cfgRemoteNode,
            int effectiveSa1,
            int effectiveDa1,
            long costMs
    ) {
        log.info(
                "FINS/TCP connected mode={} host={} port={} cfgLocalNode={} cfgRemoteNode={} SA1={} DA1={} costMs={}",
                mode, host, port, cfgLocalNode, cfgRemoteNode, effectiveSa1, effectiveDa1, costMs);
    }

    public static void connectFail(String mode, String host, int port, String reason) {
        log.warn("FINS/TCP connect failed mode={} host={} port={} reason={}", mode, host, port, reason);
    }

    public static void close(String mode, String host, int port) {
        log.info("FINS/TCP closed mode={} host={} port={}", mode, host, port);
    }

    public static void poolReuse(String connectionUrl) {
        log.info("FINS/TCP pool reuse url={}", connectionUrl);
    }

    public static void poolEvict(String connectionUrl, String reason) {
        log.info("FINS/TCP pool evict url={} reason={}", connectionUrl, reason);
    }

    public static void poolIdleEvict(String connectionUrl, long idleMs) {
        log.info("FINS/TCP pool idle evict url={} idleMs={}", connectionUrl, idleMs);
    }

    public static void poolTransportEvict(String connectionUrl, String reason) {
        log.warn("FINS/TCP pool transport evict url={} reason={}", connectionUrl, reason);
    }

    public static void handshakeRetry(String mode, String host, int port, int retryLocalNode) {
        log.warn("FINS/TCP handshake SA1=0, retry mode={} host={} port={} localNode={}",
                mode, host, port, retryLocalNode);
    }

    public static void testOk(String target, long latencyMs, int sa1, int da1) {
        log.info("FINS/TCP test ok target={} latencyMs={} SA1={} DA1={}", target, latencyMs, sa1, da1);
    }

    public static void testFail(String target, String reason) {
        log.warn("FINS/TCP test fail target={} reason={}", target, reason);
    }

    public static void pollReadRetry(
            String deviceId,
            String connectId,
            String connectionUrl,
            int attempt,
            int maxAttempts,
            boolean chunkFailure,
            String reason
    ) {
        log.warn(
                "FINS/TCP poll read retry deviceId={} connectId={} url={} attempt={}/{} chunkFailure={} reason={}",
                deviceId, connectId, connectionUrl, attempt, maxAttempts, chunkFailure, reason);
    }

    public static void pollEvict(String deviceId, String connectionUrl, String reason) {
        log.info("FINS/TCP poll evict deviceId={} url={} reason={}", deviceId, connectionUrl, reason);
    }
}
