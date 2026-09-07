package com.omes.dgw.modscan.support;

import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.springframework.stereotype.Component;

/**
 * ModScan 专用 {@link DefaultPlcDriverManager}，与采集轮询、{@code CachedPlcConnectionManager} 完全隔离。
 * 会话打开时 {@link #open(String)} 建连，关闭时 {@link #close(PlcConnection)} 释放；不经 PLC4X 连接缓存池。
 */
@Component
public class ModscanConnectionSupport {

    private final PlcDriverManager driverManager = new DefaultPlcDriverManager();

    /**
     * 打开并保持连接（由 ModScan 会话持有，须配对 {@link #close}）。
     */
    public PlcConnection open(String connectionUrl) throws Exception {
        PlcConnection connection = driverManager.getConnectionManager().getConnection(connectionUrl);
        if (!connection.isConnected()) {
            safeClose(connection);
            throw new IllegalStateException("无法建立连接");
        }
        if (!connection.getMetadata().isReadSupported()) {
            safeClose(connection);
            throw new IllegalStateException("连接不支持读取");
        }
        return connection;
    }

    public void close(PlcConnection connection) {
        safeClose(connection);
    }

    /**
     * 一次性探测（不纳入会话）。
     */
    public void validateConnection(String connectionUrl) throws Exception {
        PlcConnection connection = open(connectionUrl);
        safeClose(connection);
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
}
