package com.omes.dgw.protocol.slmp;

import lombok.extern.slf4j.Slf4j;

/**
 * SLMP/TCP 连接相关统一日志（便于现场 grep {@code SLMP/TCP}）。
 */
@Slf4j
public final class SlmpConnectionLog {

    private SlmpConnectionLog() {
    }

    public static void tcpConnecting(String mode, String host, int port, int networkNo, int stationNo, int timeoutMs) {
        log.info("SLMP/TCP connecting mode={} host={} port={} networkNo={} stationNo={} timeoutMs={}",
                mode, host, port, networkNo, stationNo, timeoutMs);
    }

    public static void connectOk(String mode, String host, int port, long costMs) {
        log.info("SLMP/TCP connected mode={} host={} port={} costMs={}", mode, host, port, costMs);
    }

    public static void connectFail(String mode, String host, int port, String reason) {
        log.warn("SLMP/TCP connect failed mode={} host={} port={} reason={}", mode, host, port, reason);
    }

    public static void close(String mode, String host, int port) {
        log.info("SLMP/TCP closed mode={} host={} port={}", mode, host, port);
    }

    public static void poolReuse(String connectionUrl) {
        log.info("SLMP/TCP pool reuse url={}", connectionUrl);
    }

    public static void poolEvict(String connectionUrl, String reason) {
        log.info("SLMP/TCP pool evict url={} reason={}", connectionUrl, reason);
    }

    public static void poolIdleEvict(String connectionUrl, long idleMs) {
        log.info("SLMP/TCP pool idle evict url={} idleMs={}", connectionUrl, idleMs);
    }

    public static void poolTransportEvict(String connectionUrl, String reason) {
        log.warn("SLMP/TCP pool transport evict url={} reason={}", connectionUrl, reason);
    }

    public static void testOk(String target, long latencyMs) {
        log.info("SLMP/TCP test ok target={} latencyMs={}", target, latencyMs);
    }

    public static void testFail(String target, String reason) {
        log.warn("SLMP/TCP test fail target={} reason={}", target, reason);
    }

    public static void testFallbackPool(String connectionUrl, String reason) {
        log.info("SLMP/TCP test second session unavailable url={} reason={}; probing pooled connection",
                connectionUrl, reason);
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
                "SLMP/TCP poll read retry deviceId={} connectId={} url={} attempt={}/{} chunkFailure={} reason={}",
                deviceId, connectId, connectionUrl, attempt, maxAttempts, chunkFailure, reason);
    }

    public static void pollEvict(String deviceId, String connectionUrl, String reason) {
        log.info("SLMP/TCP poll evict deviceId={} url={} reason={}", deviceId, connectionUrl, reason);
    }

    public static void frameFallback(String host, int port, String frame) {
        log.info("SLMP/TCP frame fallback host={} port={} -> {}", host, port, frame);
    }

    public static void specFallback(String host, int port, boolean extendedDeviceSpec) {
        log.info("SLMP/TCP device-spec fallback host={} port={} extended={}", host, port, extendedDeviceSpec);
    }

    public static void accessRetry(String host, int port, String profile, String reason) {
        log.info("SLMP/TCP access retry host={} port={} profile={} reason={}", host, port, profile, reason);
    }

    public static void accessProfile(String host, int port, String profile) {
        log.info("SLMP/TCP access profile locked host={} port={} profile={}", host, port, profile);
    }
}
