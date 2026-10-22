package com.omes.dgw.protocol;

import com.omes.dgw.device.connect.ProtocolConnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.collect.DeviceDataParser;
import com.omes.dgw.device.controller.core.DeviceConnectionFailureHandler;
import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.metrics.ProtocolCollectMetrics;
import com.omes.dgw.metrics.ProtocolPollSkipMetrics;
import com.omes.dgw.codec.opcua.OpcUaConnectionUrl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OPC UA 轮询（Apache PLC4X），节点地址原样下发，例如 {@code ns=2;s=Channel.Device.Tag}。
 */
@Component
public class OpcUaPollingProtocolManager extends AbstractPlc4xPollingProtocolManager {

    private final ObjectMapper objectMapper;

    public OpcUaPollingProtocolManager(
            DeviceRealtimeCache realtimeCacheService,
            DeviceDataParser deviceDataParser,
            ProtocolCollectMetrics collectMetrics,
            ProtocolPollSkipMetrics pollSkipMetrics,
            DeviceConnectionFailureHandler connectionFailureHandler,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        super(realtimeCacheService, deviceDataParser, collectMetrics, pollSkipMetrics,
                connectionFailureHandler, 30_000, "opcua-polling-",
                appProperties.getCollect());
        this.objectMapper = objectMapper;
    }

    @Override
    public String protocol() {
        return DeviceProtocol.OPC_UA.getCode();
    }

    @Override
    public boolean supports(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return false;
        }
        try {
            return DeviceProtocol.fromCode(protocol) == DeviceProtocol.OPC_UA;
        } catch (IllegalArgumentException e) {
            String upper = protocol.toUpperCase();
            return upper.contains("OPC");
        }
    }

    @Override
    protected String resolveTagAddress(DevicePoint point) {
        if (point == null || !StringUtils.hasText(point.getAddress())) {
            return "";
        }
        return point.getAddress().trim();
    }

    @Override
    protected ConnectSpec buildConnectSpec(ProtocolConnect connect, Map<String, String> tagAddresses) {
        OpcUaConnectionUrl.Parts parts = OpcUaConnectionUrl.parse(
                objectMapper, connect.getUri(), connect.getParams());
        return new ConnectSpec(
                OpcUaConnectionUrl.build(parts),
                new LinkedHashMap<>(tagAddresses),
                parts.timeoutMs());
    }
}
