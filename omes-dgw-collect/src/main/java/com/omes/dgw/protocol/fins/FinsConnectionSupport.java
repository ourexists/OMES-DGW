package com.omes.dgw.protocol.fins;

import com.omes.dgw.codec.fins.FinsTcpConnectionUrl;

import com.omes.dgw.codec.fins.FinsConnectParams;

import com.omes.dgw.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * FINS/TCP 连接池：轮询与按需读复用；写入/测试可走 {@link #withEphemeralClient} 短连接。
 * <p>同一 {@code connectionUrl} 串行访问，避免对 PLC 并发建多条 FINS/TCP 会话占满连接表。
 * <p>连接池锁仅保护取/还引用，I/O 在锁外执行，避免 stop/evict 与阻塞读死锁。
 */
@Slf4j
@Component
public class FinsConnectionSupport {

    /** 测试连接最小间隔，避免 PLC 连接表未释放时被反复占满或触发安全拒绝 */
    private static final long TEST_MIN_INTERVAL_MS = 3_000L;

    private final long maxIdleMs;
    private final Map<String, CachedClient> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastEphemeralTestMs = new ConcurrentHashMap<>();

    public FinsConnectionSupport(AppProperties appProperties) {
        int idleSec = Math.max(30, appProperties.getCollect().getConnection().getPlcConnectionMaxIdleSec());
        this.maxIdleMs = idleSec * 1000L;
    }

    public <T> T withClient(String connectionUrl, Function<FinsTcpClient, T> callback) throws Exception {
        CachedClient cached = cache.computeIfAbsent(connectionUrl, key -> new CachedClient());
        FinsTcpClient client;
        synchronized (cached) {
            evictIfIdle(cached, connectionUrl);
            client = cached.client;
            if (client == null || !client.isConnected() || client.getEffectiveLocalNode() == 0) {
                client = openClient(connectionUrl, "pool");
                cached.client = client;
            } else {
                FinsConnectionLog.poolReuse(connectionUrl);
            }
            cached.lastUsedMs = System.currentTimeMillis();
        }
        if (cache.get(connectionUrl) != cached) {
            safeClose(client);
            throw new IOException("FINS/TCP connection evicted");
        }
        try {
            // I/O 在锁外；客户端方法自身 synchronized，保证同连接串行
            return callback.apply(client);
        } catch (Exception e) {
            if (isTransportFailure(e)) {
                synchronized (cached) {
                    if (cached.client == client) {
                        FinsConnectionLog.poolTransportEvict(connectionUrl, rootMessage(e));
                        safeClose(cached.client);
                        cached.client = null;
                    }
                }
            }
            throw e;
        }
    }

    /**
     * @return 剩余冷却毫秒；0 表示可以测试
     */
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

    /**
     * 短连接：与 {@link #withClient} 共用同一把锁取引用；若轮询池已有连接则复用，避免 PLC 上出现第二条 FINS/TCP 会话。
     */
    public <T> T withEphemeralClient(String connectionUrl, Function<FinsTcpClient, T> callback) throws Exception {
        CachedClient cached = cache.computeIfAbsent(connectionUrl, key -> new CachedClient());
        FinsTcpClient pooled;
        synchronized (cached) {
            if (cached.client != null && cached.client.isConnected()
                    && cached.client.getEffectiveLocalNode() != 0) {
                pooled = cached.client;
                FinsConnectionLog.poolReuse(connectionUrl);
            } else {
                pooled = null;
            }
        }
        if (pooled != null) {
            return callback.apply(pooled);
        }
        FinsTcpClient client = openClient(connectionUrl, "ephemeral");
        try {
            return callback.apply(client);
        } finally {
            safeClose(client);
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
        FinsTcpClient toClose;
        synchronized (cached) {
            toClose = cached.client;
            cached.client = null;
        }
        // forceClose 不抢 client 监视器，可打断正在阻塞读的轮询线程
        if (toClose != null) {
            toClose.forceClose();
        }
        FinsConnectionLog.poolEvict(connectionUrl, reason);
    }

    private FinsTcpClient openClient(String connectionUrl, String mode) throws IOException {
        FinsTcpConnectionUrl.Parts parts = FinsTcpConnectionUrl.parse(
                connectionUrl,
                FinsConnectParams.DEFAULT_PORT,
                FinsConnectParams.DEFAULT_LOCAL_NODE,
                0,
                FinsConnectParams.DEFAULT_TIMEOUT_MS);
        FinsTcpClient client = new FinsTcpClient(
                parts.host(),
                parts.port(),
                parts.localNode(),
                parts.remoteNode(),
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
            FinsConnectionLog.poolIdleEvict(connectionUrl, idleMs);
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
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void safeClose(FinsTcpClient client) {
        if (client != null) {
            try {
                client.forceClose();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class CachedClient {
        private FinsTcpClient client;
        private long lastUsedMs = System.currentTimeMillis();
    }
}
