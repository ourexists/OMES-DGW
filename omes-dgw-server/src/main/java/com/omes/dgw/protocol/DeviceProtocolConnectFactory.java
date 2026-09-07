package com.omes.dgw.protocol;

import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.controller.core.DeviceLifecycleManager;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.protocol.fins.FinsConnectParams;
import com.omes.dgw.protocol.fins.FinsTcpConnectionUrl;
import com.omes.dgw.protocol.slmp.SlmpConnectParams;
import com.omes.dgw.protocol.slmp.SlmpTcpConnectionUrl;
import com.omes.dgw.protocol.modbus.ModbusByteOrder;
import com.omes.dgw.protocol.modbus.ModbusTcpConnectionUrl;

public final class DeviceProtocolConnectFactory {

    private DeviceProtocolConnectFactory() {
    }

    public static ProtocolConnect from(Device device) {
        ProtocolConnect connect = new ProtocolConnect();
        connect.setId(device.getId());
        connect.setProtocol(device.getProtocol() != null ? device.getProtocol().getCode() : null);
        connect.setEnabled(DeviceLifecycleManager.isEnabled(device));
        connect.setCollectIntervalSec(device.getCollectIntervalSec());
        int port = device.getPort() != null ? device.getPort() : defaultPort(device.getProtocol());
        int timeout = device.getConnectTimeoutMs() != null ? device.getConnectTimeoutMs() : 10_000;
        if (device.getProtocol() == DeviceProtocol.MODBUS_TCP) {
            ModbusTcpConnectionUrl.Parts parts = ModbusTcpConnectionUrl.fromDevice(
                    device.getIpAddress(), device.getPort(), device.getConnectTimeoutMs());
            connect.setUri(parts.host());
            connect.setParams(modbusParamsJson(parts.port(), parts.unitId(), parts.requestTimeoutMs(), device));
        } else if (device.getProtocol() == DeviceProtocol.FINS_TCP) {
            FinsTcpConnectionUrl.Parts parts = finsPartsFromDevice(device);
            connect.setUri(parts.host());
            connect.setParams(finsParamsJson(parts));
        } else if (device.getProtocol() == DeviceProtocol.SLMP_TCP) {
            SlmpTcpConnectionUrl.Parts parts = slmpPartsFromDevice(device);
            connect.setUri(parts.host());
            connect.setParams(slmpParamsJson(parts));
        } else {
            connect.setUri(device.getIpAddress());
            connect.setParams("{\"port\":" + port + ",\"timeout\":" + timeout + "}");
        }
        return connect;
    }

    public static ProtocolConnect from(DeviceRealtimeObj cached) {
        if (cached == null || cached.getDeviceId() == null) {
            return null;
        }
        ProtocolConnect connect = new ProtocolConnect();
        connect.setId(cached.getDeviceId());
        connect.setProtocol(cached.getProtocol() != null ? cached.getProtocol().getCode() : null);
        connect.setEnabled(true);
        connect.setCollectIntervalSec(cached.getCollectIntervalSec());
        int port = cached.getPort() != null ? cached.getPort() : defaultPort(cached.getProtocol());
        int timeout = cached.getConnectTimeoutMs() != null ? cached.getConnectTimeoutMs() : 10_000;
        if (cached.getProtocol() == DeviceProtocol.MODBUS_TCP) {
            ModbusTcpConnectionUrl.Parts parts = ModbusTcpConnectionUrl.fromDevice(
                    cached.getIpAddress(), cached.getPort(), cached.getConnectTimeoutMs());
            connect.setUri(parts.host());
            connect.setParams(modbusParamsJson(parts.port(), parts.unitId(), parts.requestTimeoutMs(), cached));
        } else if (cached.getProtocol() == DeviceProtocol.FINS_TCP) {
            FinsTcpConnectionUrl.Parts parts = finsPartsFromRealtime(cached);
            connect.setUri(parts.host());
            connect.setParams(finsParamsJson(parts));
        } else if (cached.getProtocol() == DeviceProtocol.SLMP_TCP) {
            SlmpTcpConnectionUrl.Parts parts = slmpPartsFromRealtime(cached);
            connect.setUri(parts.host());
            connect.setParams(slmpParamsJson(parts));
        } else {
            connect.setUri(cached.getIpAddress());
            connect.setParams("{\"port\":" + port + ",\"timeout\":" + timeout + "}");
        }
        return connect;
    }

    private static String modbusParamsJson(int port, int unitId, int timeoutMs, Device device) {
        ModbusByteOrder byteOrder = resolveModbusByteOrder(device != null ? device.getCollectConfig() : null);
        return modbusParamsJson(port, unitId, timeoutMs, byteOrder);
    }

    private static String modbusParamsJson(int port, int unitId, int timeoutMs, DeviceRealtimeObj cached) {
        DeviceCollectConfig config = cached != null
                ? DevicePointConfigSupport.normalize(cached.getCollectConfig())
                : null;
        return modbusParamsJson(port, unitId, timeoutMs, resolveModbusByteOrder(config));
    }

    private static ModbusByteOrder resolveModbusByteOrder(DeviceCollectConfig config) {
        return config != null ? config.getModbusByteOrder() : null;
    }

    private static String modbusParamsJson(int port, int unitId, int timeoutMs, ModbusByteOrder byteOrder) {
        StringBuilder json = new StringBuilder("{\"port\":")
                .append(port)
                .append(",\"unitId\":")
                .append(unitId)
                .append(",\"timeout\":")
                .append(timeoutMs);
        if (byteOrder != null) {
            json.append(",\"modbusByteOrder\":\"").append(byteOrder.getCode()).append('"');
        }
        json.append('}');
        return json.toString();
    }

    private static int defaultPort(DeviceProtocol protocol) {
        if (protocol == DeviceProtocol.MODBUS_TCP) {
            return 502;
        }
        if (protocol == DeviceProtocol.FINS_TCP) {
            return FinsConnectParams.DEFAULT_PORT;
        }
        if (protocol == DeviceProtocol.SLMP_TCP) {
            return SlmpConnectParams.DEFAULT_PORT;
        }
        return 502;
    }

    private static FinsTcpConnectionUrl.Parts finsPartsFromDevice(Device device) {
        DeviceCollectConfig config = device != null
                ? DevicePointConfigSupport.normalize(device.getCollectConfig())
                : null;
        return FinsTcpConnectionUrl.fromDevice(
                device.getIpAddress(),
                device.getPort(),
                device.getConnectTimeoutMs(),
                config != null ? config.getFinsLocalNode() : null,
                config != null ? config.getFinsRemoteNode() : null);
    }

    private static FinsTcpConnectionUrl.Parts finsPartsFromRealtime(DeviceRealtimeObj cached) {
        DeviceCollectConfig config = cached != null
                ? DevicePointConfigSupport.normalize(cached.getCollectConfig())
                : null;
        return FinsTcpConnectionUrl.fromDevice(
                cached.getIpAddress(),
                cached.getPort(),
                cached.getConnectTimeoutMs(),
                config != null ? config.getFinsLocalNode() : null,
                config != null ? config.getFinsRemoteNode() : null);
    }

    private static String finsParamsJson(FinsTcpConnectionUrl.Parts parts) {
        return "{\"port\":"
                + parts.port()
                + ",\"localNode\":"
                + parts.localNode()
                + ",\"remoteNode\":"
                + parts.remoteNode()
                + ",\"timeout\":"
                + parts.requestTimeoutMs()
                + '}';
    }

    private static SlmpTcpConnectionUrl.Parts slmpPartsFromDevice(Device device) {
        DeviceCollectConfig config = device != null
                ? DevicePointConfigSupport.normalize(device.getCollectConfig())
                : null;
        return SlmpTcpConnectionUrl.fromDevice(
                device.getIpAddress(),
                device.getPort(),
                device.getConnectTimeoutMs(),
                config != null ? config.getSlmpNetworkNo() : null,
                config != null ? config.getSlmpStationNo() : null);
    }

    private static SlmpTcpConnectionUrl.Parts slmpPartsFromRealtime(DeviceRealtimeObj cached) {
        DeviceCollectConfig config = cached != null
                ? DevicePointConfigSupport.normalize(cached.getCollectConfig())
                : null;
        return SlmpTcpConnectionUrl.fromDevice(
                cached.getIpAddress(),
                cached.getPort(),
                cached.getConnectTimeoutMs(),
                config != null ? config.getSlmpNetworkNo() : null,
                config != null ? config.getSlmpStationNo() : null);
    }

    private static String slmpParamsJson(SlmpTcpConnectionUrl.Parts parts) {
        return "{\"port\":"
                + parts.port()
                + ",\"networkNo\":"
                + parts.networkNo()
                + ",\"stationNo\":"
                + parts.stationNo()
                + ",\"timeout\":"
                + parts.requestTimeoutMs()
                + '}';
    }
}
