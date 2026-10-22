package com.omes.dgw.codec.s7;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public final class S7ConnectParamsParser {

    private S7ConnectParamsParser() {
    }

    public static S7ConnectParams parse(ObjectMapper objectMapper, String paramsJson) {
        int port = S7ConnectParams.DEFAULT_PORT;
        int rack = S7ConnectParams.DEFAULT_RACK;
        int slot = S7ConnectParams.DEFAULT_SLOT;
        int timeoutMs = S7ConnectParams.DEFAULT_TIMEOUT_MS;
        if (StringUtils.hasText(paramsJson) && objectMapper != null) {
            try {
                JsonNode node = objectMapper.readTree(paramsJson);
                if (node.has("port")) {
                    port = node.get("port").asInt(port);
                }
                if (node.has("remoteRack")) {
                    rack = node.get("remoteRack").asInt(rack);
                } else if (node.has("rack")) {
                    rack = node.get("rack").asInt(rack);
                }
                if (node.has("remoteSlot")) {
                    slot = node.get("remoteSlot").asInt(slot);
                } else if (node.has("slot")) {
                    slot = node.get("slot").asInt(slot);
                }
                if (node.has("timeout")) {
                    timeoutMs = node.get("timeout").asInt(timeoutMs);
                }
            } catch (Exception e) {
                log.debug("Parse S7 params failed: {}", e.getMessage());
            }
        }
        return new S7ConnectParams(port, rack, slot, timeoutMs);
    }
}
