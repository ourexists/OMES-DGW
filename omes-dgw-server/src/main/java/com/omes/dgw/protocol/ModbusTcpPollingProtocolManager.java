package com.omes.dgw.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.collectlog.PollRoundContext;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.collect.DeviceDataParser;
import com.omes.dgw.device.controller.core.DeviceConnectionFailureHandler;
import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.metrics.ProtocolCollectMetrics;
import com.omes.dgw.metrics.ProtocolPollSkipMetrics;
import com.omes.dgw.protocol.modbus.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Modbus TCP/RTU 轮询（基于 PLC4X），连接参数来自设备台账。
 */
@Slf4j
@Component
public class ModbusTcpPollingProtocolManager extends AbstractPlc4xPollingProtocolManager {

    private final ObjectMapper objectMapper;
    private final Map<String, ModbusPollingContext> modbusContexts = new ConcurrentHashMap<>();
    private final ModbusIsolatedConnectionSupport isolatedConnections;
    private final int maxMergeGapRegisters;
    private final int maxMergeGapBits;
    /** chunk 微批、fallback 批读、降级批读的每请求 tag/块上限 */
    private final int tagBatchSize;
    private final int readMaxRetries;
    private final long readRetryDelayMs;

    public ModbusTcpPollingProtocolManager(
            DeviceRealtimeCache realtimeCacheService,
            DeviceDataParser deviceDataParser,
            ProtocolCollectMetrics collectMetrics,
            ProtocolPollSkipMetrics pollSkipMetrics,
            DeviceConnectionFailureHandler connectionFailureHandler,
            ObjectMapper objectMapper,
            ModbusIsolatedConnectionSupport isolatedConnections,
            AppProperties appProperties) {
        super(realtimeCacheService, deviceDataParser, collectMetrics, pollSkipMetrics,
                connectionFailureHandler, ModbusConnectParams.DEFAULT_TIMEOUT_MS, "modbus-polling-",
                appProperties.getCollect());
        this.objectMapper = objectMapper;
        this.isolatedConnections = isolatedConnections;
        AppProperties.ModbusPolling modbus = appProperties.getCollect().getModbusPolling();
        this.maxMergeGapRegisters = Math.max(0, modbus.getMaxMergeGapRegisters());
        this.maxMergeGapBits = Math.max(0, modbus.getMaxMergeGapBits());
        this.tagBatchSize = Math.max(1, modbus.getTagBatchSize());
        this.readMaxRetries = Math.max(1, modbus.getReadMaxRetries());
        this.readRetryDelayMs = Math.max(0L, modbus.getReadRetryDelayMs());
    }

    @Override
    protected PlcConnectionManager connectionManagerForOnDemandRead() {
        return isolatedConnections.manager();
    }

    /** 块/批读已在 {@link #readOnce} 内按配置重试，连接级不再叠加多次整轮读。 */
    @Override
    protected int maxConnectionReadRetries() {
        return 1;
    }

    @Override
    public String protocol() {
        return DeviceProtocol.MODBUS_TCP.getCode();
    }

    @Override
    public boolean supports(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return false;
        }
        try {
            DeviceProtocol p = DeviceProtocol.fromCode(protocol);
            return p == DeviceProtocol.MODBUS_TCP;
        } catch (IllegalArgumentException e) {
            return protocol.toUpperCase().contains("MODBUS");
        }
    }

    @Override
    protected ConnectSpec buildConnectSpec(ProtocolConnect connect, Map<String, String> tagAddresses) {
        ModbusConnectParams params = ModbusConnectParamsParser.parse(objectMapper, connect.getParams(), true);
        ModbusTcpConnectionUrl.Parts parts = ModbusTcpConnectionUrl.parse(
                connect.getUri(), params.port(), params.unitId(), params.timeoutMs());
        String connectionUrl = ModbusTcpConnectionUrl.build(
                parts.host(), parts.port(), parts.unitId(), parts.requestTimeoutMs(), params.byteOrder());
        return new ConnectSpec(connectionUrl, new LinkedHashMap<>(tagAddresses), parts.requestTimeoutMs());
    }

    @Override
    protected ConnectSpec finalizeConnectSpec(
            ConnectSpec spec,
            String deviceId,
            ProtocolConnect connect,
            DeviceRealtimeObj cached
    ) {
        if (spec != null && StringUtils.hasText(deviceId)) {
            modbusContexts.put(deviceId, new ModbusPollingContext(
                    ModbusReadPlan.build(
                            spec.tagAddresses,
                            spec.pointsByName,
                            maxMergeGapRegisters,
                            maxMergeGapBits),
                    ModbusWritePointIndex.build(spec.writablePointsByName)));
        }
        return spec;
    }

    @Override
    public boolean stopPolling(String connectId, boolean preserveCircuitRecovery) {
        if (StringUtils.hasText(connectId)) {
            modbusContexts.remove(connectId);
        }
        return super.stopPolling(connectId, preserveCircuitRecovery);
    }

    @Override
    protected String convertAddress(String address) {
        return ModbusAddressHelper.convertLegacyAddress(address);
    }

    @Override
    protected Map<String, Object> readOnce(PlcConnection connection, ConnectSpec spec) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> readFailures = new ArrayList<>();
        ModbusReadPlan plan = requireReadPlan(spec);
        ModbusPollRoundStats roundStats = new ModbusPollRoundStats(
                plan.chunks().size(), plan.fallbackTags().size());
        long roundStartNanos = System.nanoTime();
        int totalPoints = spec.tagAddresses != null ? spec.tagAddresses.size() : 0;

        List<ModbusPollingChunk> chunks = plan.chunks();
        for (int i = 0; i < chunks.size(); i += tagBatchSize) {
            int end = Math.min(i + tagBatchSize, chunks.size());
            readChunkBatch(connection, spec, roundStats, chunks.subList(i, end), i + 1, result, readFailures);
        }

        List<ModbusPollingFallbackTag> fallback = plan.fallbackTags();
        for (int i = 0; i < fallback.size(); i += tagBatchSize) {
            int end = Math.min(i + tagBatchSize, fallback.size());
            readFallbackBatch(connection, spec, roundStats, fallback.subList(i, end), result, readFailures);
        }
        PollRoundContext.setModbusStats(roundStats);
        if (!readFailures.isEmpty()) {
            PollRoundContext.markPartialReadFailure();
            if (result.isEmpty()) {
                logPollRoundSummary(spec, roundStats, result.size(), totalPoints, roundStartNanos, true);
                throw new IllegalStateException(String.join("; ", readFailures));
            }
            log.warn("Modbus partial read deviceId={}, readCount={}, failures={}",
                    spec.deviceId, result.size(), String.join("; ", readFailures));
            logPollRoundSummary(spec, roundStats, result.size(), totalPoints, roundStartNanos, true);
        } else {
            logPollRoundSummary(spec, roundStats, result.size(), totalPoints, roundStartNanos, false);
        }
        return result;
    }

    private void logPollRoundSummary(
            ConnectSpec spec,
            ModbusPollRoundStats stats,
            int valuesRead,
            int totalPoints,
            long roundStartNanos,
            boolean partialFailure
    ) {
        if (spec == null || stats == null) {
            return;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - roundStartNanos);
        int plannedChunkBatches = stats.plannedChunkBatches(tagBatchSize);
        int plannedFallbackBatches = stats.plannedFallbackBatches(tagBatchSize);
        if (!log.isDebugEnabled() && !partialFailure && !stats.hasDegradationOrRetry()) {
            return;
        }
        String line = "Modbus poll round deviceId={}, chunks={}, chunkBatches={}/{}, fallbackTags={}, "
                + "fallbackBatches={}, plcTx={}, logicalReads={}, chunkDegraded={}, degradeBatches={}, "
                + "singleTags={}, readRetries={}, values={}/{}, durationMs={}";
        Object[] args = {
                spec.deviceId,
                stats.plannedChunks(),
                stats.chunkBatches(),
                plannedChunkBatches,
                stats.plannedFallbackTags(),
                plannedFallbackBatches,
                stats.plcTransactions(),
                stats.logicalReads(),
                stats.chunkDegraded(),
                stats.degradeBatches(),
                stats.singleTagReads(),
                stats.readRetryAttempts(),
                valuesRead,
                totalPoints,
                durationMs
        };
        if (partialFailure || stats.hasDegradationOrRetry()) {
            log.info(line, args);
        } else if (log.isDebugEnabled()) {
            log.debug(line, args);
        }
    }

    @Override
    public ProtocolWriteResult write(String connectId, ProtocolWriteRequest request) {
        if (!StringUtils.hasText(connectId) || request == null
                || !StringUtils.hasText(request.address()) || request.value() == null) {
            return ProtocolWriteResult.fail("写入参数无效");
        }
        ConnectSpec spec = specMap.get(connectId);
        if (spec == null) {
            log.warn("{} write failed: no spec, connectId={}", protocol(), connectId);
            return ProtocolWriteResult.fail("设备未启动采集或连接未建立");
        }
        if (spec.reusePollingConnection) {
            return super.write(connectId, request);
        }
        long startNanos = System.nanoTime();
        ProtocolWriteResult result = ProtocolWriteResult.fail("协议写入失败");
        try {
            result = isolatedConnections.withEphemeralConnection(spec.connectionUrl, connection ->
                    performPlcWrite(connection, spec, request));
            if (result.success()) {
                onWriteSuccess(connectId);
            }
        } catch (Exception e) {
            log.error("{} write error connectId={}: {}", protocol(), connectId, e.getMessage());
            result = ProtocolWriteResult.fail(e.getMessage());
        } finally {
            recordWrite(spec, result.success(), startNanos);
        }
        return result;
    }

    @Override
    protected ProtocolWriteResult performPlcWrite(
            PlcConnection connection, ConnectSpec spec, ProtocolWriteRequest request
    ) throws Exception {
        String address = request.address();
        Optional<String> reject = rejectWritableModbusAddress(address);
        if (reject.isPresent()) {
            log.warn("Modbus write rejected: {} address={}", reject.get(), address);
            return ProtocolWriteResult.fail(reject.get());
        }
        Object value = request.value();
        PointDataType requestType = request.dataType();
        ModbusByteOrder requestOrder = request.byteOrder();

        DevicePoint point = resolveWritePoint(spec, address);
        Optional<String> validation = validateWriteAddressAndType(address, point, requestType);
        if (validation.isPresent()) {
            log.warn("Modbus write rejected: {} address={}", validation.get(), address);
            return ProtocolWriteResult.fail(validation.get());
        }
        if (point != null && point.getDataType() != null) {
            String writeAddress = StringUtils.hasText(point.getSetpointAddress())
                    ? point.getSetpointAddress()
                    : address;
            ModbusPlcWritePayload payload = ModbusNumericCodec.preparePlcWriteForAddress(
                    writeAddress,
                    point.getDataType(),
                    value,
                    requestOrder,
                    ModbusNumericCodec.resolve(point.getByteOrder(), spec.modbusByteOrder),
                    point.getStringLength());
            if (payload != null) {
                return writePlcTag(connection, spec, payload.plcAddress(), payload.plcValue());
            }
        }
        PointDataType dataType = requestType != null ? requestType : (point != null ? point.getDataType() : null);
        if (dataType != null) {
            ModbusPlcWritePayload payload = ModbusNumericCodec.preparePlcWriteForAddress(
                    address,
                    dataType,
                    value,
                    requestOrder,
                    spec.modbusByteOrder,
                    point != null ? point.getStringLength() : null);
            if (payload != null) {
                return writePlcTag(connection, spec, payload.plcAddress(), payload.plcValue());
            }
            return ProtocolWriteResult.fail("写入值与数据类型不匹配");
        }
        return writePlcTag(connection, spec, convertAddress(address), normalizeCoilWriteValue(address, value));
    }

    private ProtocolWriteResult writePlcTag(
            PlcConnection connection, ConnectSpec spec, String plcAddress, Object plcValue
    ) throws Exception {
        Optional<String> reject = rejectWritableModbusAddress(plcAddress);
        if (reject.isPresent()) {
            return ProtocolWriteResult.fail(reject.get());
        }
        if (!connection.getMetadata().isWriteSupported()) {
            return ProtocolWriteResult.fail("连接不支持写入");
        }
        PlcWriteRequest writeRequest = connection.writeRequestBuilder()
                .addTagAddress("ctrl", plcAddress, ModbusNumericCodec.toPlcWriteVarargs(plcValue))
                .build();
        try {
            PlcWriteResponse response = PlcTagResponseSupport.awaitWriteResponse(
                    writeRequest, spec.requestTimeoutMs);
            return PlcTagResponseSupport.isTagOk(response, "ctrl")
                    ? ProtocolWriteResult.ok()
                    : ProtocolWriteResult.fail("PLC 写入失败");
        } catch (IllegalArgumentException e) {
            log.warn("Modbus write rejected: {} plcAddress={}", e.getMessage(), plcAddress);
            return ProtocolWriteResult.fail(e.getMessage());
        } catch (Exception e) {
            String message = PlcTagResponseSupport.toWriteFailureMessage(e);
            log.warn("Modbus write failed: {} plcAddress={}", message, plcAddress);
            return ProtocolWriteResult.fail(message);
        }
    }

    private static Optional<String> rejectWritableModbusAddress(String address) {
        ModbusRegisterArea area = ModbusAddressHelper.registerAreaFromAddress(address);
        if (area != null && !area.isWritable()) {
            return Optional.of("Modbus 寄存器区不支持写入: " + area.getLabel());
        }
        return Optional.empty();
    }

    private static Optional<String> validateWriteAddressAndType(
            String address, DevicePoint point, PointDataType requestType
    ) {
        PointDataType dataType;
        Integer stringLength = null;
        if (point != null && point.getDataType() != null) {
            dataType = point.getDataType();
            stringLength = point.getStringLength();
        } else if (requestType != null) {
            dataType = requestType;
            if (point != null) {
                stringLength = point.getStringLength();
            }
        } else {
            return Optional.empty();
        }
        return ModbusAddressHelper.validateWriteAddressDataType(address, dataType, stringLength);
    }

    private DevicePoint resolveWritePoint(ConnectSpec spec, String address) {
        if (spec == null) {
            return null;
        }
        ModbusPollingContext ctx = modbusContexts.get(spec.deviceId);
        if (ctx != null) {
            return ctx.writePointIndex().resolve(address);
        }
        return ModbusWritePointIndex.build(spec.writablePointsByName).resolve(address);
    }

    private ModbusReadPlan requireReadPlan(ConnectSpec spec) {
        ModbusPollingContext ctx = spec != null ? modbusContexts.get(spec.deviceId) : null;
        if (ctx != null) {
            return ctx.readPlan();
        }
        return ModbusReadPlan.build(
                spec != null ? spec.tagAddresses : Map.of(),
                spec != null ? spec.pointsByName : Map.of(),
                maxMergeGapRegisters,
                maxMergeGapBits);
    }

    private Object normalizeCoilWriteValue(String address, Object value) {
        String convertedAddr = convertAddress(address);
        if (convertedAddr == null || !convertedAddr.startsWith("coil:")) {
            return value;
        }
        Boolean wire = ModbusNumericCodec.decodeBool(value, PointDataType.BOOL);
        return wire != null ? wire : value;
    }

    /**
     * 将多个预计算块合并为一次 {@link PlcReadRequest}；失败块回退为单块读/降级。
     */
    private void readChunkBatch(PlcConnection connection,
                                ConnectSpec spec,
                                ModbusPollRoundStats roundStats,
                                List<ModbusPollingChunk> batch,
                                int firstChunkNo,
                                Map<String, Object> result,
                                List<String> readFailures) throws Exception {
        if (batch.isEmpty()) {
            return;
        }
        if (batch.size() == 1) {
            ModbusPollingChunk chunk = batch.get(0);
            readChunk(connection, spec, roundStats, chunk.area(), chunk.startPdu(), chunk.endPdu(),
                    chunk.tags(), firstChunkNo, result, readFailures);
            return;
        }
        List<ChunkBlockOp> ops = new ArrayList<>(batch.size());
        int chunkNo = firstChunkNo;
        for (ModbusPollingChunk chunk : batch) {
            ops.add(toChunkBlockOp(chunk, chunkNo++));
        }
        roundStats.onChunkBatch();
        if (readChunkBatchWithRetry(connection, spec, roundStats, ops, result)) {
            return;
        }
        for (ChunkBlockOp op : ops) {
            if (!isChunkComplete(op.chunk(), result)) {
                ModbusPollingChunk chunk = op.chunk();
                readChunk(connection, spec, roundStats, chunk.area(), chunk.startPdu(), chunk.endPdu(),
                        chunk.tags(), op.chunkNo(), result, readFailures);
            }
        }
    }

    private void readChunk(PlcConnection connection,
                           ConnectSpec spec,
                           ModbusPollRoundStats roundStats,
                           String area,
                           int startPdu,
                           int endPdu,
                           List<ModbusPollingSimpleTag> chunkTags,
                           int chunkNo,
                           Map<String, Object> result,
                           List<String> readFailures) throws Exception {
        ModbusPollingChunk chunk = new ModbusPollingChunk(area, startPdu, endPdu, chunkTags);
        if (readChunkRegisterBlockWithRetry(connection, spec, roundStats, chunk, chunkNo, result)) {
            return;
        }
        roundStats.onChunkDegraded();
        readSimpleTagsDegraded(connection, spec, roundStats, chunkTags, result);
        appendUnreadFailures(readFailures, "块读失败 query=" + toChunkBlockOp(chunk, chunkNo).query(), chunkTags, result,
                ModbusPollingSimpleTag::tagName, spec.deviceId);
    }

    private boolean readChunkBatchWithRetry(PlcConnection connection,
                                            ConnectSpec spec,
                                            ModbusPollRoundStats roundStats,
                                            List<ChunkBlockOp> ops,
                                            Map<String, Object> result) throws Exception {
        return ModbusPlcPollingReadTemplate.withRetry(
                readMaxRetries,
                readRetryDelayMs,
                readMetrics(spec, roundStats),
                this::awaitRetryDelay,
                "chunk batch size=" + ops.size(),
                spec.deviceId,
                () -> readChunkBatchOnce(connection, spec, roundStats, ops, result),
                roundStats);
    }

    private static boolean readChunkBatchOnce(PlcConnection connection,
                                              ConnectSpec spec,
                                              ModbusPollRoundStats roundStats,
                                              List<ChunkBlockOp> ops,
                                              Map<String, Object> result) throws Exception {
        PlcReadRequest.Builder builder = connection.readRequestBuilder();
        for (ChunkBlockOp op : ops) {
            builder.addTagAddress(op.alias(), op.query());
        }
        roundStats.onPlcTransaction();
        PlcReadResponse response = builder.build().execute().get(spec.requestTimeoutMs, TimeUnit.MILLISECONDS);
        boolean allDecoded = true;
        for (ChunkBlockOp op : ops) {
            if (!applyChunkBlockResponse(op, response, spec, result)) {
                allDecoded = false;
            }
        }
        return allDecoded;
    }

    private boolean readChunkRegisterBlockWithRetry(PlcConnection connection,
                                                    ConnectSpec spec,
                                                    ModbusPollRoundStats roundStats,
                                                    ModbusPollingChunk chunk,
                                                    int chunkNo,
                                                    Map<String, Object> result) throws Exception {
        ChunkBlockOp op = toChunkBlockOp(chunk, chunkNo);
        return ModbusPlcPollingReadTemplate.withRetry(
                readMaxRetries,
                readRetryDelayMs,
                readMetrics(spec, roundStats),
                this::awaitRetryDelay,
                "chunk query=" + op.query(),
                spec.deviceId,
                () -> readChunkBatchOnce(connection, spec, roundStats, List.of(op), result),
                roundStats);
    }

    private static boolean applyChunkBlockResponse(
            ChunkBlockOp op,
            PlcReadResponse response,
            ConnectSpec spec,
            Map<String, Object> result
    ) {
        Object block = PlcTagResponseSupport.getObjectLenient(response, op.alias());
        if (block == null && !PlcTagResponseSupport.isTagOk(response, op.alias())) {
            return false;
        }
        if (block == null) {
            return false;
        }
        boolean allDecoded = true;
        ModbusPollingChunk chunk = op.chunk();
        for (ModbusPollingSimpleTag tag : chunk.tags()) {
            int index = tag.pduOffset() - op.startPdu();
            Object value = decodeChunkValue(block, index, tag, spec);
            if (value != null) {
                result.put(tag.tagName(), value);
            } else {
                allDecoded = false;
            }
        }
        return allDecoded;
    }

    private static ChunkBlockOp toChunkBlockOp(ModbusPollingChunk chunk, int chunkNo) {
        ModbusRegisterArea kind = ModbusRegisterArea.fromPlcArea(chunk.area());
        int plcStart = kind != null
                ? ModbusAddressHelper.plc4xTagFromPduOffset(kind, chunk.startPdu())
                : chunk.startPdu() + 1;
        int quantity = chunk.endPdu() - chunk.startPdu() + 1;
        String alias = "__chunk_" + chunkNo;
        String query = chunk.area() + ":" + plcStart + "[" + quantity + "]";
        return new ChunkBlockOp(chunkNo, alias, query, chunk.startPdu(), chunk);
    }

    private static boolean isChunkComplete(ModbusPollingChunk chunk, Map<String, Object> result) {
        for (ModbusPollingSimpleTag tag : chunk.tags()) {
            if (!result.containsKey(tag.tagName())) {
                return false;
            }
        }
        return true;
    }

    /** 单块合并读在批请求中的条目。 */
    private record ChunkBlockOp(int chunkNo, String alias, String query, int startPdu, ModbusPollingChunk chunk) {
    }

    private void readFallbackBatch(PlcConnection connection,
                                   ConnectSpec spec,
                                   ModbusPollRoundStats roundStats,
                                   List<ModbusPollingFallbackTag> batch,
                                   Map<String, Object> result,
                                   List<String> readFailures) throws Exception {
        if (batch.isEmpty()) {
            return;
        }
        roundStats.onFallbackBatch();
        if (readFallbackBatchWithRetry(connection, spec, roundStats, batch, result)) {
            return;
        }
        readFallbackTagsDegraded(connection, spec, roundStats, batch, result);
        appendUnreadFailures(readFailures, "点读批次失败", batch, result,
                ModbusPollingFallbackTag::tagName, spec.deviceId);
    }

    private boolean readFallbackBatchWithRetry(PlcConnection connection,
                                               ConnectSpec spec,
                                               ModbusPollRoundStats roundStats,
                                               List<ModbusPollingFallbackTag> batch,
                                               Map<String, Object> result) throws Exception {
        return ModbusPlcPollingReadTemplate.withRetry(
                readMaxRetries,
                readRetryDelayMs,
                readMetrics(spec, roundStats),
                this::awaitRetryDelay,
                "fallback batch",
                spec.deviceId,
                () -> readFallbackTagBatchStrict(connection, spec, roundStats, batch, result),
                roundStats);
    }

    private static boolean readFallbackTagBatchStrict(PlcConnection connection,
                                                      ConnectSpec spec,
                                                      ModbusPollRoundStats roundStats,
                                                      List<ModbusPollingFallbackTag> batch,
                                                      Map<String, Object> result) throws Exception {
        return ModbusPlcPollingReadTemplate.readTagBatch(
                connection,
                spec.requestTimeoutMs,
                spec.deviceId,
                toFallbackTagReadOps(batch, spec),
                fallbackTagDecoder(batch, spec),
                ModbusPlcPollingReadTemplate.BatchSuccessMode.ALL_DECODED,
                false,
                null,
                result,
                false,
                null,
                roundStats);
    }

    /** 块读失败后按小批、单点降级（与 ModScan 策略一致）。 */
    private void readSimpleTagsDegraded(
            PlcConnection connection,
            ConnectSpec spec,
            ModbusPollRoundStats roundStats,
            List<ModbusPollingSimpleTag> chunkTags,
            Map<String, Object> result
    ) throws Exception {
        for (int from = 0; from < chunkTags.size(); from += tagBatchSize) {
            int to = Math.min(from + tagBatchSize, chunkTags.size());
            List<ModbusPollingSimpleTag> sub = pendingSimpleTags(chunkTags.subList(from, to), result);
            if (sub.isEmpty()) {
                continue;
            }
            roundStats.onDegradeBatch();
            if (readSimpleTagBatch(connection, spec, roundStats, sub, result)) {
                continue;
            }
            for (ModbusPollingSimpleTag tag : sub) {
                if (!result.containsKey(tag.tagName())) {
                    readSingleSimpleTag(connection, spec, roundStats, tag, result);
                }
            }
        }
    }

    private boolean readSimpleTagBatch(
            PlcConnection connection,
            ConnectSpec spec,
            ModbusPollRoundStats roundStats,
            List<ModbusPollingSimpleTag> batch,
            Map<String, Object> result
    ) throws Exception {
        return ModbusPlcPollingReadTemplate.readTagBatch(
                connection,
                spec.requestTimeoutMs,
                spec.deviceId,
                toSimpleTagReadOps(batch),
                simpleTagDecoder(batch, spec),
                ModbusPlcPollingReadTemplate.BatchSuccessMode.ANY_NEW_VALUE,
                true,
                "Modbus simple tag batch read failed",
                result,
                true,
                readMetrics(spec, roundStats),
                roundStats);
    }

    private boolean readSingleSimpleTag(
            PlcConnection connection,
            ConnectSpec spec,
            ModbusPollRoundStats roundStats,
            ModbusPollingSimpleTag tag,
            Map<String, Object> result
    ) throws Exception {
        roundStats.onSingleTagRead();
        ModbusPlcPollingReadTemplate.TagReadOp op =
                new ModbusPlcPollingReadTemplate.TagReadOp(tag.tagName(), simpleTagReadQuery(tag));
        return ModbusPlcPollingReadTemplate.readSingleTag(
                connection,
                spec.requestTimeoutMs,
                spec.deviceId,
                op,
                (ignored, block) -> decodeChunkValue(block, 0, tag, spec),
                "Modbus single simple tag read failed",
                result,
                readMetrics(spec, roundStats),
                roundStats);
    }

    private static String simpleTagReadQuery(ModbusPollingSimpleTag tag) {
        ModbusRegisterArea kind = ModbusRegisterArea.fromPlcArea(tag.area());
        int plcStart = kind != null
                ? ModbusAddressHelper.plc4xTagFromPduOffset(kind, tag.pduOffset())
                : tag.pduOffset() + 1;
        int quantity = registerSpan(tag);
        return tag.area() + ":" + plcStart + "[" + quantity + "]";
    }

    private void readFallbackTagsDegraded(
            PlcConnection connection,
            ConnectSpec spec,
            ModbusPollRoundStats roundStats,
            List<ModbusPollingFallbackTag> batch,
            Map<String, Object> result
    ) throws Exception {
        for (int from = 0; from < batch.size(); from += tagBatchSize) {
            int to = Math.min(from + tagBatchSize, batch.size());
            List<ModbusPollingFallbackTag> sub = pendingFallbackTags(batch.subList(from, to), result);
            if (sub.isEmpty()) {
                continue;
            }
            roundStats.onDegradeBatch();
            if (readFallbackTagBatchLenient(connection, spec, roundStats, sub, result)) {
                continue;
            }
            for (ModbusPollingFallbackTag tag : sub) {
                if (!result.containsKey(tag.tagName())) {
                    readSingleFallbackTag(connection, spec, roundStats, tag, result);
                }
            }
        }
    }

    private boolean readFallbackTagBatchLenient(
            PlcConnection connection,
            ConnectSpec spec,
            ModbusPollRoundStats roundStats,
            List<ModbusPollingFallbackTag> batch,
            Map<String, Object> result
    ) throws Exception {
        return ModbusPlcPollingReadTemplate.readTagBatch(
                connection,
                spec.requestTimeoutMs,
                spec.deviceId,
                toFallbackTagReadOps(batch, spec),
                fallbackTagDecoder(batch, spec),
                ModbusPlcPollingReadTemplate.BatchSuccessMode.ANY_NEW_VALUE,
                true,
                "Modbus fallback degrade batch failed",
                result,
                true,
                readMetrics(spec, roundStats),
                roundStats);
    }

    private boolean readSingleFallbackTag(
            PlcConnection connection,
            ConnectSpec spec,
            ModbusPollRoundStats roundStats,
            ModbusPollingFallbackTag tag,
            Map<String, Object> result
    ) throws Exception {
        roundStats.onSingleTagRead();
        ModbusPlcPollingReadTemplate.TagReadOp op = new ModbusPlcPollingReadTemplate.TagReadOp(
                tag.tagName(), toFallbackReadAddress(tag, spec));
        return ModbusPlcPollingReadTemplate.readSingleTag(
                connection,
                spec.requestTimeoutMs,
                spec.deviceId,
                op,
                (ignored, block) -> decodeFallbackValue(block, tag, spec),
                "Modbus single fallback read failed",
                result,
                readMetrics(spec, roundStats),
                roundStats);
    }

    private ModbusPlcPollingReadTemplate.ReadMetricsHook readMetrics(
            ConnectSpec spec, ModbusPollRoundStats roundStats
    ) {
        return (success, startNanos) -> {
            roundStats.onLogicalRead();
            recordRead(spec, success, startNanos);
        };
    }

    private static List<ModbusPlcPollingReadTemplate.TagReadOp> toSimpleTagReadOps(List<ModbusPollingSimpleTag> batch) {
        List<ModbusPlcPollingReadTemplate.TagReadOp> ops = new ArrayList<>(batch.size());
        for (ModbusPollingSimpleTag tag : batch) {
            ops.add(new ModbusPlcPollingReadTemplate.TagReadOp(tag.tagName(), simpleTagReadQuery(tag)));
        }
        return ops;
    }

    private static List<ModbusPlcPollingReadTemplate.TagReadOp> toFallbackTagReadOps(
            List<ModbusPollingFallbackTag> batch,
            ConnectSpec spec
    ) {
        List<ModbusPlcPollingReadTemplate.TagReadOp> ops = new ArrayList<>(batch.size());
        for (ModbusPollingFallbackTag tag : batch) {
            ops.add(new ModbusPlcPollingReadTemplate.TagReadOp(tag.tagName(), toFallbackReadAddress(tag, spec)));
        }
        return ops;
    }

    private static ModbusPlcPollingReadTemplate.TagBlockDecoder simpleTagDecoder(
            List<ModbusPollingSimpleTag> batch,
            ConnectSpec spec
    ) {
        Map<String, ModbusPollingSimpleTag> byName = new HashMap<>(batch.size());
        for (ModbusPollingSimpleTag tag : batch) {
            byName.put(tag.tagName(), tag);
        }
        return (op, block) -> {
            ModbusPollingSimpleTag tag = byName.get(op.tagName());
            return tag != null ? decodeChunkValue(block, 0, tag, spec) : null;
        };
    }

    private static ModbusPlcPollingReadTemplate.TagBlockDecoder fallbackTagDecoder(
            List<ModbusPollingFallbackTag> batch,
            ConnectSpec spec
    ) {
        Map<String, ModbusPollingFallbackTag> byName = new HashMap<>(batch.size());
        for (ModbusPollingFallbackTag tag : batch) {
            byName.put(tag.tagName(), tag);
        }
        return (op, block) -> {
            ModbusPollingFallbackTag tag = byName.get(op.tagName());
            return tag != null ? decodeFallbackValue(block, tag, spec) : null;
        };
    }

    private static <T> void appendUnreadFailures(
            List<String> readFailures,
            String prefix,
            List<T> tags,
            Map<String, Object> result,
            Function<T, String> tagNameFn,
            String deviceId
    ) {
        List<String> unread = new ArrayList<>();
        for (T tag : tags) {
            String name = tagNameFn.apply(tag);
            if (!result.containsKey(name)) {
                unread.add(name);
            }
        }
        if (!unread.isEmpty()) {
            String msg = prefix + " 未读到点位=" + unread;
            readFailures.add(msg);
            log.warn("Modbus partial read failure deviceId={}: {}", deviceId, msg);
        }
    }

    /**
     * 块/tag 已由 {@link PlcTagResponseSupport#getObject} 转为 Java，此处不再调用 {@link PlcValueConverter#toJava}。
     */
    private static Object decodeChunkValue(Object block, int index, ModbusPollingSimpleTag tag, ConnectSpec spec) {
        DevicePoint point = tag.point();
        if (point != null && point.getDataType() != null && usesRegisterCodec(tag)) {
            ModbusByteOrder order = ModbusNumericCodec.resolve(
                    point.getByteOrder(),
                    spec != null ? spec.modbusByteOrder : null);
            return ModbusNumericCodec.decodeFromBlock(block, index, point, order);
        }
        return decodeWireValue(ModbusNumericCodec.getElement(block, index), point);
    }

    /** 保持寄存器区按点位数据类型解码；线圈/离散量仍用原始位值。 */
    private static boolean usesRegisterCodec(ModbusPollingSimpleTag tag) {
        if (tag == null || tag.point() == null || tag.point().getDataType() == null) {
            return false;
        }
        String area = tag.area();
        return "holding-register".equals(area) || "input-register".equals(area);
    }

    private static int registerSpan(ModbusPollingSimpleTag tag) {
        if (tag.point() == null) {
            return 1;
        }
        return ModbusNumericCodec.registerCount(tag.point());
    }

    private static String toFallbackReadAddress(ModbusPollingFallbackTag tag, ConnectSpec spec) {
        if (tag.point() == null || !StringUtils.hasText(tag.point().getAddress())) {
            return tag.address();
        }
        return ModbusAddressHelper.toPlc4xReadAddress(
                tag.point().getAddress(),
                tag.point().getDataType(),
                tag.point().getStringLength());
    }

    private static Object decodeFallbackValue(Object block, ModbusPollingFallbackTag tag, ConnectSpec spec) {
        if (block == null) {
            return null;
        }
        DevicePoint point = tag.point();
        if (point != null && point.getDataType() != null && isRegisterAreaAddress(tag.address())) {
            ModbusByteOrder order = ModbusNumericCodec.resolve(
                    point.getByteOrder(),
                    spec != null ? spec.modbusByteOrder : null);
            return ModbusNumericCodec.decodeFromBlock(block, 0, point, order);
        }
        return decodeWireValue(block, point);
    }

    /** 线圈/离散量及无寄存器 codec 的标量：bool 语义解码，其余原样返回。 */
    private static Object decodeWireValue(Object wire, DevicePoint point) {
        if (wire == null) {
            return null;
        }
        if (point != null && point.getDataType() != null && point.getDataType().isBoolType()) {
            Boolean decoded = ModbusNumericCodec.decodeBool(wire, point.getDataType());
            return decoded != null ? decoded : wire;
        }
        return wire;
    }

    private static boolean isRegisterAreaAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return false;
        }
        String normalized = ModbusAddressHelper.stripDataTypeSuffix(address.trim().toLowerCase());
        return normalized.startsWith("holding-register:") || normalized.startsWith("input-register:");
    }

    private static List<ModbusPollingSimpleTag> pendingSimpleTags(
            List<ModbusPollingSimpleTag> tags,
            Map<String, Object> result
    ) {
        List<ModbusPollingSimpleTag> pending = new ArrayList<>();
        for (ModbusPollingSimpleTag tag : tags) {
            if (!result.containsKey(tag.tagName())) {
                pending.add(tag);
            }
        }
        return pending;
    }

    private static List<ModbusPollingFallbackTag> pendingFallbackTags(
            List<ModbusPollingFallbackTag> tags,
            Map<String, Object> result
    ) {
        List<ModbusPollingFallbackTag> pending = new ArrayList<>();
        for (ModbusPollingFallbackTag tag : tags) {
            if (!result.containsKey(tag.tagName())) {
                pending.add(tag);
            }
        }
        return pending;
    }

    private record ModbusPollingContext(ModbusReadPlan readPlan, ModbusWritePointIndex writePointIndex) {
    }
}
