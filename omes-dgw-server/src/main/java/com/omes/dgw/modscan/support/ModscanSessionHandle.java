package com.omes.dgw.modscan.support;

import com.omes.dgw.protocol.ProtocolConnect;
import org.apache.plc4x.java.api.PlcConnection;

import java.util.concurrent.locks.ReentrantLock;

/**
 * ModScan 会话：由 {@link ModscanSessionManager} 管理 {@link DefaultPlcDriverManager} 建立的持久连接。
 */
public final class ModscanSessionHandle {

    private final String sessionId;
    private final String deviceId;
    private final ProtocolConnect connect;
    private final ModscanConnectSpec connectSpec;
    private volatile PlcConnection connection;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastAccessEpochMs;

    public ModscanSessionHandle(
            String sessionId,
            String deviceId,
            ProtocolConnect connect,
            ModscanConnectSpec connectSpec,
            PlcConnection connection
    ) {
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.connect = connect;
        this.connectSpec = connectSpec;
        this.connection = connection;
        this.lastAccessEpochMs = System.currentTimeMillis();
    }

    public String sessionId() {
        return sessionId;
    }

    public String deviceId() {
        return deviceId;
    }

    public ProtocolConnect connect() {
        return connect;
    }

    public ModscanConnectSpec connectSpec() {
        return connectSpec;
    }

    public PlcConnection connection() {
        return connection;
    }

    void setConnection(PlcConnection connection) {
        this.connection = connection;
    }

    public ReentrantLock lock() {
        return lock;
    }

    public long lastAccessEpochMs() {
        return lastAccessEpochMs;
    }

    public void touch() {
        lastAccessEpochMs = System.currentTimeMillis();
    }
}
