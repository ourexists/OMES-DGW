package com.omes.dgw.codec.slmp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public final class SlmpConnectParamsParser {

    private SlmpConnectParamsParser() {
    }

    public static SlmpConnectParams parse(ObjectMapper objectMapper, String paramsJson) {
        int port = SlmpConnectParams.DEFAULT_PORT;
        int networkNo = SlmpConnectParams.DEFAULT_NETWORK_NO;
        int stationNo = SlmpConnectParams.DEFAULT_STATION_NO;
        int timeoutMs = SlmpConnectParams.DEFAULT_TIMEOUT_MS;
        if (StringUtils.hasText(paramsJson) && objectMapper != null) {
            try {
                JsonNode node = objectMapper.readTree(paramsJson);
                if (node.has("port")) {
                    port = node.get("port").asInt(SlmpConnectParams.DEFAULT_PORT);
                }
                if (node.has("networkNo")) {
                    networkNo = node.get("networkNo").asInt(SlmpConnectParams.DEFAULT_NETWORK_NO);
                }
                if (node.has("stationNo")) {
                    stationNo = node.get("stationNo").asInt(SlmpConnectParams.DEFAULT_STATION_NO);
                }
                if (node.has("timeout")) {
                    timeoutMs = node.get("timeout").asInt(SlmpConnectParams.DEFAULT_TIMEOUT_MS);
                }
            } catch (Exception e) {
                log.debug("Parse SLMP params failed: {}", e.getMessage());
            }
        }
        return new SlmpConnectParams(port, networkNo, stationNo, timeoutMs);
    }
}
