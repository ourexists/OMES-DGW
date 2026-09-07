package com.omes.dgw.protocol.slmp;

import com.omes.dgw.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * SLMP/TCP 连接池：轮询与按需读复用；写入可走 {@link #withEphemeralClient}；
 * 连接测试走 {@link #withTestClient} 短连接。
 * <p>连接池锁仅保护取/还引用，I/O 在锁外执行，避免 stop/evict 与阻塞读死锁。
 */
@Slf4j
@Component
public class SlmpConnectionSupport {

    /** 与前端冷却、PLC 释放连接表对齐，避免测完立刻再连被拒 */
    private static final long TEST_MIN_INTERVAL_MS = 3_000L;

    private final long maxIdleMs;
    private final Map<String, CachedClient> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastEphemeralTestMs = new ConcurrentHashMap<>();

    public SlmpConnectionSupport(AppProperties appProperties) {
        int idleSec = Math.max(30, appProperties.getCollect().getConnection().getPlcConnectionMaxIdleSec());
        this.maxIdleMs = idleSec * 1000L;
    }

    public <T> T withClient(String connectionUrl, Function<SlmpTcpClient, T> callback) throws Exception {
        CachedClient cached = cache.computeIfAbsent(connectionUrl, key -> new CachedClient());
        SlmpTcpClient client;
        synchronized (cached) {
            evictIfIdle(cached, connectionUrl);
            client = cached.client;
            if (client == null || !client.isConnected()) {
                client = openClient(connectionUrl, "pool");
                cached.client = client;
            } else {
                SlmpConnectionLog.poolReuse(connectionUrl);
            }
            cached.lastUsedMs = System.currentTimeMillis();
        }
        if (cache.get(connectionUrl) != cached) {
            safeClose(client);
            throw new IOException("SLMP/TCP connection evicted");
        }
        try {
            return callback.apply(client);
        } catch (Exception e) {
            if (isTransportFailure(e)) {
                synchronized (cached) {
                    if (cached.client == client) {
                        SlmpConnectionLog.poolTransportEvict(connectionUrl, rootMessage(e));
                        safeClose(cached.client);
                        cached.client = null;
                    }
                }
            }
            throw e;
        }
    }

    public long ephemeralTestCooldownMs(String connectionUrl) {
        Long last = lastEphemeralTestMs.get(connectionUrl);
        if (last == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - last;
        return elapsed >= TEST_MIN_INTERVAL_MS ? 0 : TEST_MIN_INTERVAL_MS - elapsed;
    }

    public void markEphemeralTestStarted(String connectionUrl) {
        lastEphemeralTestMs.put(connectionUrl, System.currentTimeMillis());
    }

    public <T> T withEphemeralClient(String connectionUrl, Function<SlmpTcpClient, T> callback) throws Exception {
        CachedClient cached = cache.computeIfAbsent(connectionUrl, key -> new CachedClient());
        SlmpTcpClient pooled;
        synchronized (cached) {
            if (cached.client != null && cached.client.isConnected()) {
                pooled = cached.client;
                SlmpConnectionLog.poolReuse(connectionUrl);
            } else {
                pooled = null;
            }
        }
        if (pooled != null) {
            return callback.apply(pooled);
        }
        SlmpTcpClient client = openClient(connectionUrl, "ephemeral");
        try {
            return callback.apply(client);
        } finally {
            safeClose(client);
        }
    }

    /**
     * 连接测试：强制新建短连接；若对端拒绝第二条会话（单连接 PLC 上轮询已占用），
     * 再回退到池连接做协议探测。传输失败时踢掉池连接，避免继续复用半开会话。
     */
    public <T> T withTestClient(String connectionUrl, Function<SlmpTcpClient, T> callback) throws Exception {
        SlmpTcpClient dedicated;
        try {
            dedicated = openClient(connectionUrl, "test");
        } catch (IOException openError) {
            SlmpTcpClient pooled = peekPooled(connectionUrl);
            if (pooled == null) {
                throw openError;
            }
            SlmpConnectionLog.testFallbackPool(connectionUrl, openError.getMessage());
            try {
                return callback.apply(pooled);
            } catch (Exception e) {
                evictIfTransportFailure(connectionUrl, e);
                throw e;
            }
        }
        try {
            return callback.apply(dedicated);
        } catch (Exception e) {
            evictIfTransportFailure(connectionUrl, e);
            throw e;
        } finally {
            safeClose(dedicated);
        }
    }

    public void evict(String connectionUrl) {
        evict(connectionUrl, "manual");
    }

    public void evict(String connectionUrl, String reason) {
        CachedClient cached = cache.remove(connectionUrl);
        if (cached == null) {
            return;
        }
        SlmpTcpClient toClose;
        synchronized (cached) {
            toClose = cached.client;
            cached.client = null;
        }
        if (toClose != null) {
            toClose.forceClose();
        }
        SlmpConnectionLog.poolEvict(connectionUrl, reason);
    }

    private SlmpTcpClient peekPooled(String connectionUrl) {
        CachedClient cached = cache.get(connectionUrl);
        if (cached == null) {
            return null;
        }
        synchronized (cached) {
            if (cached.client != null && cached.client.isConnected()) {
                return cached.client;
            }
        }
        return null;
    }

    private void evictIfTransportFailure(String connectionUrl, Exception error) {
        if (isTransportFailure(error)) {
            evict(connectionUrl, "test-fail");
        }
    }

    private SlmpTcpClient openClient(String connectionUrl, String mode) throws IOException {
        SlmpTcpConnectionUrl.Parts parts = SlmpTcpConnectionUrl.parse(
                connectionUrl,
                SlmpConnectParams.DEFAULT_PORT,
                SlmpConnectParams.DEFAULT_NETWORK_NO,
                SlmpConnectParams.DEFAULT_STATION_NO,
                SlmpConnectParams.DEFAULT_TIMEOUT_MS);
        SlmpTcpClient client = new SlmpTcpClient(
                parts.host(),
                parts.port(),
                parts.networkNo(),
                parts.stationNo(),
                parts.requestTimeoutMs());
        client.setConnectionMode(mode);
        client.connect();
        return client;
    }

    private void evictIfIdle(CachedClient cached, String connectionUrl) {
        if (cached.client != null && System.currentTimeMillis() - cached.lastUsedMs > maxIdleMs) {
            long idleMs = System.currentTimeMillis() - cached.lastUsedMs;
            safeClose(cached.client);
            cached.client = null;
            SlmpConnectionLog.poolIdleEvict(connectionUrl, idleMs);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    private static boolean isTransportFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SlmpEndCodeException) {
                return false;
            }
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void safeClose(SlmpTcpClient client) {
        if (client != null) {
            try {
                client.forceClose();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class CachedClient {
        private SlmpTcpClient client;
        private long lastUsedMs = System.currentTimeMillis();
    }
}
