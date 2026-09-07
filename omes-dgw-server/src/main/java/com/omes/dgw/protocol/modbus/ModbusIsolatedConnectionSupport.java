package com.omes.dgw.protocol.modbus;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.protocol.PlcConnectionCacheFactory;
import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.springframework.stereotype.Component;

/**
 * Modbus 按需读（配置点位一次性读取等）专用连接池，
 * 与 {@link com.omes.dgw.protocol.AbstractPlc4xPollingProtocolManager} 采集轮询连接池完全隔离。
 * 控制写入使用 {@link #withEphemeralConnection} 短连接（建连 → 写入 → 关闭），不经缓存池。
 * ModScan 远程扫描使用 {@link com.omes.dgw.modscan.support.ModscanConnectionSupport}。
 */
@Component
public class ModbusIsolatedConnectionSupport {

    private final PlcConnectionManager connectionManager;

    /** 控制写入专用，与采集/按需读缓存池隔离，每次操作独立建连并关闭。 */
    private final PlcDriverManager writeDriverManager = new DefaultPlcDriverManager();

    public ModbusIsolatedConnectionSupport(AppProperties appProperties) {
        AppProperties.Connection cache = appProperties.getCollect().getConnection();
        this.connectionManager = PlcConnectionCacheFactory.create(
                cache.getPlcConnectionMaxLeaseSec(),
                cache.getPlcConnectionMaxWaitSec(),
                cache.getPlcConnectionMaxIdleSec());
    }

    public PlcConnectionManager manager() {
        return connectionManager;
    }

    public <T> T withConnection(String connectionUrl, ConnectionCallback<T> callback) throws Exception {
        try (PlcConnection connection = connectionManager.getConnection(connectionUrl)) {
            return callback.execute(connection);
        }
    }

    /**
     * 一次性短连接：打开后执行回调，结束时关闭并释放（不进入 {@link CachedPlcConnectionManager}）。
     */
    public <T> T withEphemeralConnection(String connectionUrl, ConnectionCallback<T> callback) throws Exception {
        PlcConnection connection = writeDriverManager.getConnectionManager().getConnection(connectionUrl);
        try {
            return callback.execute(connection);
        } finally {
            safeClose(connection);
        }
    }

    private static void safeClose(PlcConnection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception ignored) {
        }
    }

    @FunctionalInterface
    public interface ConnectionCallback<T> {
        T execute(PlcConnection connection) throws Exception;
    }
}
