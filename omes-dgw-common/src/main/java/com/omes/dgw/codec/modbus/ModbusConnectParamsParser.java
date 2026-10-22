package com.omes.dgw.codec.modbus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 解析设备连接 {@code params} JSON（轮询、ModScan、按需读共用）。
 */
@Slf4j
public final class ModbusConnectParamsParser {

    private ModbusConnectParamsParser() {
    }

    public static ModbusConnectParams parse(ObjectMapper objectMapper, String paramsJson) {
        return parse(objectMapper, paramsJson, true);
    }

    /**
     * @param includeByteOrder 轮询连接 URL 需字节序；ModScan 等可设为 false
     */
    public static ModbusConnectParams parse(
            ObjectMapper objectMapper,
            String paramsJson,
            boolean includeByteOrder
    ) {
        int port = ModbusConnectParams.DEFAULT_PORT;
        int unitId = ModbusConnectParams.DEFAULT_UNIT_ID;
        int timeoutMs = ModbusConnectParams.DEFAULT_TIMEOUT_MS;
        ModbusByteOrder byteOrder = null;
        if (!StringUtils.hasText(paramsJson) || objectMapper == null) {
            return new ModbusConnectParams(port, unitId, timeoutMs, byteOrder);
        }
        try {
            JsonNode node = objectMapper.readTree(paramsJson);
            if (node.has("port")) {
                port = node.get("port").asInt(ModbusConnectParams.DEFAULT_PORT);
            }
            if (node.has("unitId")) {
                unitId = node.get("unitId").asInt(ModbusConnectParams.DEFAULT_UNIT_ID);
            }
            if (node.has("timeout")) {
                timeoutMs = node.get("timeout").asInt(ModbusConnectParams.DEFAULT_TIMEOUT_MS);
            }
            if (includeByteOrder && node.has("modbusByteOrder")) {
                byteOrder = ModbusByteOrder.fromCode(node.get("modbusByteOrder").asText());
            }
        } catch (Exception e) {
            log.debug("Parse Modbus params failed: {}", e.getMessage());
        }
        return new ModbusConnectParams(port, unitId, timeoutMs, byteOrder);
    }
}
