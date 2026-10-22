package com.omes.dgw.device.connect;

import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.dto.DeviceConnectionTestVo;
import com.omes.dgw.device.entity.Device;

import java.time.Duration;

/**
 * 协议采集网关 SPI：实现位于 {@code omes-dgw-collect}。
 * 盒子门户无 collect 时 {@link org.springframework.beans.factory.ObjectProvider} 为空。
 */
public interface DeviceProtocolGateway {

    boolean start(ProtocolConnect connect, Duration initialDelay);

    boolean stop(String protocol, String connectId);

    boolean stopPollingPreserveCircuit(String protocol, String connectId);

    void pollOnce(String protocol, String connectId);

    DeviceRealtimeObj readAllPoints(String protocol, String deviceId);

    ProtocolWriteResult write(String protocol, String connectId, ProtocolWriteRequest request);

    boolean isConnectionOpen(String protocol, String deviceId);

    boolean isConnectionOpen(Device device);

    DeviceConnectionTestVo testConnection(ProtocolConnect connect);
}
