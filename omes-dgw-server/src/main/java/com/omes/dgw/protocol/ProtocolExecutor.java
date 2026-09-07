package com.omes.dgw.protocol;

import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.dto.DeviceConnectionTestVo;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.enums.DeviceProtocol;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Component
public class ProtocolExecutor {

    private final List<ProtocolManager> protocolManagers;

    public ProtocolExecutor(List<ProtocolManager> protocolManagers) {
        this.protocolManagers = protocolManagers;
    }

    public boolean start(ProtocolConnect connect) {
        return start(connect, Duration.ZERO);
    }

    /**
     * 启动协议轮询；{@code initialDelay} 大于 0 时推迟首次建连，避免批量启用瞬间打满连接。
     */
    public boolean start(ProtocolConnect connect, Duration initialDelay) {
        ProtocolManager protocolManager = protocolManager(connect.getProtocol());
        if (protocolManager == null) {
            return false;
        }
        if (protocolManager instanceof AbstractPlc4xPollingProtocolManager polling) {
            return polling.start(connect, initialDelay);
        }
        return protocolManager.start(connect);
    }

    public boolean stop(String protocol, String connectId) {
        ProtocolManager preferred = protocolManager(protocol);
        boolean stopped = preferred != null && preferred.stop(connectId);
        // 防止 supports() 误匹配导致启停落在不同 Manager，残留 native socket
        for (ProtocolManager manager : protocolManagers) {
            if (manager != preferred && manager.stop(connectId)) {
                stopped = true;
            }
        }
        return preferred == null || stopped;
    }

    /**
     * 停止轮询但保留熔断退避状态（供半开恢复前清理残留任务）。
     */
    public boolean stopPollingPreserveCircuit(String protocol, String connectId) {
        ProtocolManager manager = protocolManager(protocol);
        if (manager instanceof AbstractPlc4xPollingProtocolManager polling) {
            return polling.stopPolling(connectId, true);
        }
        return stop(protocol, connectId);
    }

    public void pollOnce(String protocol, String connectId) {
        ProtocolManager protocolManager = protocolManager(protocol);
        if (protocolManager instanceof AbstractPlc4xPollingProtocolManager polling) {
            polling.pollOnce(connectId);
        }
    }

    /**
     * 一次性读取设备下全部配置点位（仅 PLC4X 轮询协议支持）。
     */
    public DeviceRealtimeObj readAllPoints(String protocol, String deviceId) {
        ProtocolManager protocolManager = protocolManager(protocol);
        if (protocolManager instanceof AbstractPlc4xPollingProtocolManager polling) {
            return polling.readAllPoints(deviceId);
        }
        return null;
    }

    public ProtocolWriteResult write(String protocol, String connectId, ProtocolWriteRequest request) {
        ProtocolManager protocolManager = protocolManager(protocol);
        if (protocolManager == null) {
            return ProtocolWriteResult.fail("未找到协议实现: " + protocol);
        }
        if (request == null) {
            return ProtocolWriteResult.fail("写入参数无效");
        }
        return protocolManager.write(connectId, request);
    }

    /**
     * 采集协议连接是否已开启（轮询任务是否在运行）。
     */
    public boolean isConnectionOpen(String protocol, String deviceId) {
        if (!StringUtils.hasText(protocol) || !StringUtils.hasText(deviceId)) {
            return false;
        }
        ProtocolManager manager = protocolManager(protocol);
        if (manager instanceof AbstractPlc4xPollingProtocolManager polling) {
            return polling.isConnectionOpen(deviceId);
        }
        return false;
    }

    public boolean isConnectionOpen(Device device) {
        if (device == null || !Boolean.TRUE.equals(device.getEnabled())) {
            return false;
        }
        DeviceProtocol protocol = device.getProtocol();
        if (!DeviceProtocol.isSupported(protocol)) {
            return false;
        }
        return isConnectionOpen(protocol.getCode(), device.getId());
    }

    public DeviceConnectionTestVo testConnection(ProtocolConnect connect) {
        if (connect == null || !StringUtils.hasText(connect.getProtocol())) {
            return DeviceConnectionTestVo.fail("协议不能为空");
        }
        ProtocolManager manager = protocolManager(connect.getProtocol());
        if (manager == null) {
            return DeviceConnectionTestVo.fail("未找到协议实现: " + connect.getProtocol());
        }
        return manager.testConnection(connect);
    }

    public ProtocolManager protocolManager(String name) {
        if (!org.springframework.util.StringUtils.hasText(name)) {
            return null;
        }
        ProtocolManager bySupports = null;
        for (ProtocolManager protocolManager : protocolManagers) {
            if (name.equalsIgnoreCase(protocolManager.protocol())) {
                return protocolManager;
            }
            if (bySupports == null && protocolManager.supports(name)) {
                bySupports = protocolManager;
            }
        }
        return bySupports;
    }
}
