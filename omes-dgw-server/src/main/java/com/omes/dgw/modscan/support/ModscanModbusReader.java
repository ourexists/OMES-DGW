package com.omes.dgw.modscan.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.metrics.ProtocolCollectMetrics;
import com.omes.dgw.modscan.model.ModscanScanResult;
import com.omes.dgw.protocol.PlcTagResponseSupport;
import com.omes.dgw.protocol.ProtocolConnect;
import com.omes.dgw.protocol.modbus.ModbusAddressHelper;
import com.omes.dgw.protocol.modbus.ModbusConnectParams;
import com.omes.dgw.protocol.modbus.ModbusConnectParamsParser;
import com.omes.dgw.protocol.modbus.ModbusNumericCodec;
import com.omes.dgw.protocol.modbus.ModbusRegisterArea;
import com.omes.dgw.protocol.modbus.ModbusTcpConnectionUrl;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ModScan 远程地址区段读取（基于 PLC4X，与设备采集轮询隔离）。
 * 连接由 {@link ModscanSessionManager} 通过 {@link ModscanConnectionSupport} 维护。
 */
@Slf4j
@Component
public class ModscanModbusReader {

    private static final int MAX_REGISTER_SPAN = 125;
    private static final int MAX_BIT_SPAN = 2000;
    private static final int PER_ADDRESS_BATCH_SIZE = 20;

    private final ObjectMapper objectMapper;
    private final ProtocolCollectMetrics collectMetrics;

    public ModscanModbusReader(
            ObjectMapper objectMapper,
            ProtocolCollectMetrics collectMetrics
    ) {
        this.objectMapper = objectMapper;
        this.collectMetrics = collectMetrics;
    }

    public ModscanScanResult scanOnConnection(
            PlcConnection connection,
            ModscanConnectSpec spec,
            ProtocolConnect connect,
            String deviceId,
            ModbusRegisterArea area,
            int startAddress,
            int quantity
    ) {
        int pduStart = ModbusAddressHelper.resolvePduOffsetStart(area, startAddress);
        ModscanScanResult validation = validateScanRequest(connect, area, pduStart, quantity);
        if (validation != null) {
            return validation;
        }
        if (connection == null || !connection.isConnected()) {
            return ModscanScanResult.fail("无法建立连接");
        }
        if (!connection.getMetadata().isReadSupported()) {
            return ModscanScanResult.fail("连接不支持读取");
        }

        long startNanos = System.nanoTime();
        ModscanScanResult result = initScanResult(connect, spec);
        String plcArea = area.toPlc4xArea();
        int pduMax = area.isBitArea() ? MAX_BIT_SPAN : MAX_REGISTER_SPAN;

        try {
            int remaining = quantity;
            int offset = 0;
            int chunkNo = 0;
            while (remaining > 0) {
                int chunkQty = Math.min(remaining, pduMax);
                int chunkStart = pduStart + offset;
                readRangeChunk(connection, spec, deviceId, area, plcArea, chunkStart, chunkQty, chunkNo++, result.getItems());
                offset += chunkQty;
                remaining -= chunkQty;
            }
            copyScanOutcome(result, quantity);
        } catch (Exception e) {
            log.warn("Modscan range scan failed: {}", e.getMessage());
            result.setSuccess(false);
            result.setMessage(e.getMessage() != null ? e.getMessage() : "扫描失败");
        } finally {
            result.setLatencyMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        }
        return result;
    }

    public ModscanConnectSpec buildConnectSpec(ProtocolConnect connect) {
        ModbusConnectParams params = ModbusConnectParamsParser.parse(objectMapper, connect.getParams(), false);
        ModbusTcpConnectionUrl.Parts parts = ModbusTcpConnectionUrl.parse(
                connect.getUri(), params.port(), params.unitId(), params.timeoutMs());
        String connectionUrl = ModbusTcpConnectionUrl.build(
                parts.host(), parts.port(), parts.unitId(), parts.requestTimeoutMs());
        return new ModscanConnectSpec(connectionUrl, parts.requestTimeoutMs());
    }

    private static ModscanScanResult validateScanRequest(
            ProtocolConnect connect,
            ModbusRegisterArea area,
            int startAddress,
            int quantity
    ) {
        if (connect == null || !StringUtils.hasText(connect.getUri())) {
            return ModscanScanResult.fail("连接地址不能为空");
        }
        if (area == null) {
            return ModscanScanResult.fail("寄存器区不能为空");
        }
        if (startAddress < 0) {
            return ModscanScanResult.fail("起始地址不能小于0");
        }
        if (quantity < 1) {
            return ModscanScanResult.fail("数量至少为1");
        }
        if (quantity > area.maxScanQuantity()) {
            return ModscanScanResult.fail(
                    "数量不能超过 " + area.maxScanQuantity() + "（" + area.getLabel() + "）");
        }
        return null;
    }

    private ModscanScanResult initScanResult(ProtocolConnect connect, ModscanConnectSpec spec) {
        ModscanScanResult result = new ModscanScanResult();
        result.setUnitId(resolveUnitId(connect));
        result.setConnectionTarget(resolveConnectionTarget(connect, spec));
        return result;
    }

    private static void copyScanOutcome(ModscanScanResult result, int quantity) {
        int returned = result.getItems().size();
        if (returned == 0) {
            result.setSuccess(false);
            result.setMessage("未读取到数据（请确认从站号、地址区及设备是否支持该范围）");
        } else if (returned < quantity) {
            result.setSuccess(true);
            result.setMessage("部分读取成功，返回 " + returned + "/" + quantity);
        } else {
            result.setSuccess(true);
        }
    }

    private void readRangeChunk(
            PlcConnection connection,
            ModscanConnectSpec spec,
            String deviceId,
            ModbusRegisterArea area,
            String plcArea,
            int start,
            int quantity,
            int chunkNo,
            List<ModscanScanResult.Item> target
    ) {
        int before = target.size();
        if (readRangeBlock(connection, spec, deviceId, area, plcArea, start, quantity, chunkNo, target)) {
            return;
        }
        String typeSuffix = area.isBitArea() ? ":BOOL" : ":UINT";
        for (int batchFrom = 0; batchFrom < quantity; batchFrom += PER_ADDRESS_BATCH_SIZE) {
            int batchLen = Math.min(PER_ADDRESS_BATCH_SIZE, quantity - batchFrom);
            if (readRangeAddressBatch(connection, spec, deviceId, area, typeSuffix,
                    start, batchFrom, batchLen, chunkNo, target)) {
                continue;
            }
            for (int i = 0; i < batchLen; i++) {
                readSingleAddress(connection, spec, deviceId, area, typeSuffix,
                        start + batchFrom + i, chunkNo, target);
            }
        }
        if (target.size() == before) {
            log.warn("Modscan chunk empty start={}, qty={}, chunk={}", start, quantity, chunkNo);
        }
    }

    private boolean readRangeBlock(
            PlcConnection connection,
            ModscanConnectSpec spec,
            String deviceId,
            ModbusRegisterArea area,
            String plcArea,
            int start,
            int quantity,
            int chunkNo,
            List<ModscanScanResult.Item> target
    ) {
        long startNanos = System.nanoTime();
        boolean success = false;
        String alias = "block_" + chunkNo;
        int plcStart = ModbusAddressHelper.plc4xTagFromPduOffset(area, start);
        String query = plcArea + ":" + plcStart + "[" + quantity + "]";
        try {
            PlcReadResponse response = connection.readRequestBuilder()
                    .addTagAddress(alias, query)
                    .build()
                    .execute()
                    .get(spec.requestTimeoutMs(), TimeUnit.MILLISECONDS);
            Object block = PlcTagResponseSupport.getObjectLenient(response, alias);
            if (block == null) {
                log.debug("Modscan block read empty: {}", query);
                return false;
            }
            success = true;
            for (int i = 0; i < quantity; i++) {
                appendItem(target, area, start + i, ModbusNumericCodec.getElement(block, i));
            }
            return true;
        } catch (Exception e) {
            log.debug("Modscan block read failed {}: {}", query, e.getMessage());
            return false;
        } finally {
            recordRead(deviceId, success, startNanos);
        }
    }

    private boolean readRangeAddressBatch(
            PlcConnection connection,
            ModscanConnectSpec spec,
            String deviceId,
            ModbusRegisterArea area,
            String typeSuffix,
            int rangeStart,
            int batchFrom,
            int batchLen,
            int chunkNo,
            List<ModscanScanResult.Item> target
    ) {
        long startNanos = System.nanoTime();
        boolean success = false;
        int before = target.size();
        try {
            PlcReadRequest.Builder builder = connection.readRequestBuilder();
            List<String> tagNames = new ArrayList<>(batchLen);
            for (int i = 0; i < batchLen; i++) {
                int globalOffset = rangeStart + batchFrom + i;
                String tag = "scan" + chunkNo + "_" + batchFrom + "_" + i;
                String address = ModbusAddressHelper.plc4xAddressFromPduOffset(area, globalOffset) + typeSuffix;
                builder.addTagAddress(tag, address);
                tagNames.add(tag);
            }
            PlcReadResponse response = builder.build().execute().get(spec.requestTimeoutMs(), TimeUnit.MILLISECONDS);
            for (int i = 0; i < batchLen; i++) {
                int globalOffset = rangeStart + batchFrom + i;
                String tag = tagNames.get(i);
                Object value = PlcTagResponseSupport.getObjectLenient(response, tag);
                if (value != null || PlcTagResponseSupport.isTagOk(response, tag)) {
                    success = true;
                    appendItem(target, area, globalOffset, value);
                }
            }
            return target.size() > before;
        } catch (Exception e) {
            log.debug("Modscan batch failed chunk={}, from={}, len={}: {}",
                    chunkNo, batchFrom, batchLen, e.getMessage());
            return false;
        } finally {
            recordRead(deviceId, success, startNanos);
        }
    }

    private void readSingleAddress(
            PlcConnection connection,
            ModscanConnectSpec spec,
            String deviceId,
            ModbusRegisterArea area,
            String typeSuffix,
            int globalOffset,
            int chunkNo,
            List<ModscanScanResult.Item> target
    ) {
        long startNanos = System.nanoTime();
        boolean success = false;
        String tag = "s" + chunkNo + "_" + globalOffset;
        String address = ModbusAddressHelper.plc4xAddressFromPduOffset(area, globalOffset) + typeSuffix;
        try {
            PlcReadResponse response = connection.readRequestBuilder()
                    .addTagAddress(tag, address)
                    .build()
                    .execute()
                    .get(spec.requestTimeoutMs(), TimeUnit.MILLISECONDS);
            Object value = PlcTagResponseSupport.getObjectLenient(response, tag);
            if (value != null || PlcTagResponseSupport.isTagOk(response, tag)) {
                success = true;
                appendItem(target, area, globalOffset, value);
            }
        } catch (Exception e) {
            log.trace("Modscan single read failed {}: {}", address, e.getMessage());
        } finally {
            recordRead(deviceId, success, startNanos);
        }
    }

    private void recordRead(String deviceId, boolean success, long startNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        collectMetrics.recordRead(DeviceProtocol.MODBUS_TCP.getCode(), deviceId, success, durationMs);
    }

    private static void appendItem(
            List<ModscanScanResult.Item> target,
            ModbusRegisterArea area,
            int pduOffset,
            Object value
    ) {
        ModscanScanResult.Item item = new ModscanScanResult.Item();
        item.setOffset(pduOffset);
        item.setLegacyAddress(ModbusAddressHelper.legacyAddressFromPduOffset(area, pduOffset));
        item.setAddress(ModbusAddressHelper.stripDataTypeSuffix(
                ModbusAddressHelper.plc4xAddressFromPduOffset(area, pduOffset)));
        item.setBinaryValue(formatBinaryValue(area, value));
        target.add(item);
    }

    private static String formatBinaryValue(ModbusRegisterArea area, Object value) {
        if (value == null) {
            return null;
        }
        if (area.isBitArea()) {
            return toBooleanBit(value) ? "1" : "0";
        }
        if (value instanceof Number number) {
            int raw = number.intValue() & 0xFFFF;
            return String.format("%16s", Integer.toBinaryString(raw)).replace(' ', '0');
        }
        return null;
    }

    private static boolean toBooleanBit(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int resolveUnitId(ProtocolConnect connect) {
        return ModbusConnectParamsParser.parse(objectMapper, connect.getParams(), false).unitId();
    }

    private String resolveConnectionTarget(ProtocolConnect connect, ModscanConnectSpec spec) {
        ModbusConnectParams params = ModbusConnectParamsParser.parse(objectMapper, connect.getParams(), false);
        String host = connect.getUri() != null ? connect.getUri().trim() : "";
        return host + ":" + params.port();
    }
}
