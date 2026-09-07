package com.omes.dgw.modscan.support;

import com.omes.dgw.protocol.modbus.ModbusRegisterArea;
import com.omes.dgw.modscan.model.ModscanScanResult;
import com.omes.dgw.protocol.ProtocolConnect;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 */
@Slf4j
@Component
public class ModscanSessionManager {

    public static final Duration SESSION_IDLE_TIMEOUT = Duration.ofHours(1);

    private final ModscanConnectionSupport connectionSupport;
    private final ModscanModbusReader modbusReader;
    private final Map<String, ModscanSessionHandle> sessions = new ConcurrentHashMap<>();

    public ModscanSessionManager(ModscanConnectionSupport connectionSupport, ModscanModbusReader modbusReader) {
        this.connectionSupport = connectionSupport;
        this.modbusReader = modbusReader;
    }

    public ModscanSessionHandle open(ProtocolConnect connect, String deviceId) throws Exception {
        ModscanConnectSpec spec = modbusReader.buildConnectSpec(connect);
        PlcConnection connection = connectionSupport.open(spec.connectionUrl());
        ModscanSessionHandle handle = new ModscanSessionHandle(
                newSessionId(), deviceId, connect, spec, connection);
        sessions.put(handle.sessionId(), handle);
        log.info("Modscan session opened sessionId={} deviceId={}", handle.sessionId(), deviceId);
        return handle;
    }

    public ModscanSessionHandle requireSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        ModscanSessionHandle handle = sessions.get(sessionId.trim());
        if (handle == null) {
            return null;
        }
        if (isIdleExpired(handle)) {
            closeQuietly(handle);
            return null;
        }
        return handle;
    }

    public ModscanScanResult scan(
            ModscanSessionHandle handle,
            ModbusRegisterArea area,
            int startAddress,
            int quantity
    ) {
        handle.lock().lock();
        try {
            if (isIdleExpired(handle)) {
                closeQuietly(handle);
                return ModscanScanResult.fail("扫描会话已过期，请重新打开连接");
            }
            ensureConnected(handle);
            handle.touch();
            return modbusReader.scanOnConnection(
                    handle.connection(),
                    handle.connectSpec(),
                    handle.connect(),
                    handle.deviceId(),
                    area,
                    startAddress,
                    quantity
            );
        } catch (Exception e) {
            log.warn("Modscan session scan failed sessionId={}: {}", handle.sessionId(), e.getMessage());
            return ModscanScanResult.fail(e.getMessage() != null ? e.getMessage() : "扫描失败");
        } finally {
            handle.lock().unlock();
        }
    }

    public boolean close(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        ModscanSessionHandle handle = sessions.remove(sessionId.trim());
        if (handle == null) {
            return false;
        }
        closeQuietly(handle);
        log.info("Modscan session closed sessionId={}", sessionId);
        return true;
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictIdleSessions() {
        long deadline = System.currentTimeMillis() - SESSION_IDLE_TIMEOUT.toMillis();
        for (Map.Entry<String, ModscanSessionHandle> entry : sessions.entrySet()) {
            ModscanSessionHandle handle = entry.getValue();
            if (handle.lastAccessEpochMs() < deadline) {
                if (sessions.remove(entry.getKey(), handle)) {
                    closeQuietly(handle);
                    log.info("Modscan session evicted (idle > 1h) sessionId={}", entry.getKey());
                }
            }
        }
    }

    @PreDestroy
    public void closeAll() {
        for (ModscanSessionHandle handle : sessions.values()) {
            closeQuietly(handle);
        }
        sessions.clear();
    }

    private void ensureConnected(ModscanSessionHandle handle) throws Exception {
        PlcConnection connection = handle.connection();
        if (connection != null && connection.isConnected()) {
            return;
        }
        if (connection != null) {
            connectionSupport.close(connection);
        }
        PlcConnection reopened = connectionSupport.open(handle.connectSpec().connectionUrl());
        handle.setConnection(reopened);
        log.info("Modscan session reconnected sessionId={}", handle.sessionId());
    }

    private boolean isIdleExpired(ModscanSessionHandle handle) {
        return handle.lastAccessEpochMs()
                < System.currentTimeMillis() - SESSION_IDLE_TIMEOUT.toMillis();
    }

    private void closeQuietly(ModscanSessionHandle handle) {
        sessions.remove(handle.sessionId(), handle);
        connectionSupport.close(handle.connection());
        handle.setConnection(null);
    }

    public static String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
