package com.omes.dgw.protocol.s7;

import com.omes.dgw.codec.s7.S7TcpConnectionUrl;

import com.omes.dgw.codec.s7.S7ConnectParams;

import com.github.s7connector.api.S7Connector;
import com.github.s7connector.api.factory.S7ConnectorFactory;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.protocol.PollTransportFailures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * S7 ISO-on-TCP 连接池。同一连接串串行读写，停轮询时若仍有读在进行则等读结束后再关闭。
 */
@Slf4j
@Component
public class S7ConnectionSupport {

    private static final long TEST_MIN_INTERVAL_MS = 3_000L;

    private final long maxIdleMs;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastTestMs = new ConcurrentHashMap<>();

    public S7ConnectionSupport(AppProperties appProperties) {
        int idleSec = Math.max(30, appProperties.getCollect().getConnection().getPlcConnectionMaxIdleSec());
        this.maxIdleMs = idleSec * 1000L;
    }

    public <T> T withClient(String connectionUrl, Function<S7Connector, T> callback) throws Exception {
        Cached cached = cache.computeIfAbsent(connectionUrl, key -> new Cached());
        S7Connector client;
        synchronized (cached) {
            closeIfIdle(cached);
            if (cached.client == null) {
                cached.client = open(connectionUrl);
                log.info("S7 connection established, url={}", connectionUrl);
            }
            client = cached.client;
            cached.inUse++;
            cached.lastUsedMs = System.currentTimeMillis();
        }
        try {
            synchronized (client) {
                return callback.apply(client);
            }
        } catch (Exception e) {
            if (PollTransportFailures.isTransportFailure(e)) {
                synchronized (cached) {
                    cached.broken = true;
                }
            }
            throw e;
        } finally {
            synchronized (cached) {
                cached.inUse--;
                cached.lastUsedMs = System.currentTimeMillis();
                if ((cached.broken || cached.evicted) && cached.inUse <= 0) {
                    closeQuietly(cached.client);
                    cached.client = null;
                    cached.broken = false;
                    if (cached.evicted) {
                        cache.remove(connectionUrl, cached);
                    }
                }
            }
        }
    }

    public <T> T withEphemeral(String connectionUrl, Function<S7Connector, T> callback) throws Exception {
        Cached cached = cache.get(connectionUrl);
        if (cached != null) {
            synchronized (cached) {
                if (cached.client != null && !cached.broken && !cached.evicted) {
                    return withClient(connectionUrl, callback);
                }
            }
        }
        S7Connector client = open(connectionUrl);
        try {
            return callback.apply(client);
        } finally {
            closeQuietly(client);
        }
    }

    public long testCooldownMs(String connectionUrl) {
        Long last = lastTestMs.get(connectionUrl);
        if (last == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - last;
        return elapsed >= TEST_MIN_INTERVAL_MS ? 0 : TEST_MIN_INTERVAL_MS - elapsed;
    }

    public void markTestStarted(String connectionUrl) {
        lastTestMs.put(connectionUrl, System.currentTimeMillis());
    }

    public void evict(String connectionUrl) {
        Cached cached = cache.get(connectionUrl);
        if (cached == null) {
            return;
        }
        synchronized (cached) {
            cached.evicted = true;
            if (cached.inUse <= 0) {
                closeQuietly(cached.client);
                cached.client = null;
                cache.remove(connectionUrl, cached);
            }
        }
    }

    private void closeIfIdle(Cached cached) {
        if (cached.client != null && cached.inUse == 0
                && System.currentTimeMillis() - cached.lastUsedMs > maxIdleMs) {
            closeQuietly(cached.client);
            cached.client = null;
        }
    }

    private static S7Connector open(String connectionUrl) {
        S7TcpConnectionUrl.Parts parts = S7TcpConnectionUrl.parse(
                connectionUrl,
                S7ConnectParams.DEFAULT_PORT,
                S7ConnectParams.DEFAULT_RACK,
                S7ConnectParams.DEFAULT_SLOT,
                S7ConnectParams.DEFAULT_TIMEOUT_MS);
        if (parts.host() == null || parts.host().isBlank()) {
            throw new IllegalArgumentException("S7 主机地址为空");
        }
        return S7ConnectorFactory
                .buildTCPConnector()
                .withHost(parts.host())
                .withPort(parts.port())
                .withRack(parts.rack())
                .withSlot(parts.slot())
                .withTimeout(parts.requestTimeoutMs())
                .build();
    }

    private static void closeQuietly(S7Connector connector) {
        if (connector == null) {
            return;
        }
        try {
            connector.close();
        } catch (Exception ignored) {
        }
    }

    private static final class Cached {
        private S7Connector client;
        private int inUse;
        private long lastUsedMs = System.currentTimeMillis();
        private boolean broken;
        private boolean evicted;
    }
}
