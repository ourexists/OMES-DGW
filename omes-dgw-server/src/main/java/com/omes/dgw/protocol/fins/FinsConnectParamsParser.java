package com.omes.dgw.protocol.fins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public final class FinsConnectParamsParser {

    private FinsConnectParamsParser() {
    }

    public static FinsConnectParams parse(ObjectMapper objectMapper, String paramsJson) {
        int port = FinsConnectParams.DEFAULT_PORT;
        int localNode = FinsConnectParams.DEFAULT_LOCAL_NODE;
        int remoteNode = 0;
        int timeoutMs = FinsConnectParams.DEFAULT_TIMEOUT_MS;
        if (StringUtils.hasText(paramsJson) && objectMapper != null) {
            try {
                JsonNode node = objectMapper.readTree(paramsJson);
                if (node.has("port")) {
                    port = node.get("port").asInt(FinsConnectParams.DEFAULT_PORT);
                }
                if (node.has("localNode")) {
                    localNode = node.get("localNode").asInt(FinsConnectParams.DEFAULT_LOCAL_NODE);
                }
                if (node.has("remoteNode")) {
                    remoteNode = node.get("remoteNode").asInt(0);
                }
                if (node.has("timeout")) {
                    timeoutMs = node.get("timeout").asInt(FinsConnectParams.DEFAULT_TIMEOUT_MS);
                }
            } catch (Exception e) {
                log.debug("Parse FINS params failed: {}", e.getMessage());
            }
        }
        return new FinsConnectParams(port, localNode, remoteNode, timeoutMs);
    }
}
