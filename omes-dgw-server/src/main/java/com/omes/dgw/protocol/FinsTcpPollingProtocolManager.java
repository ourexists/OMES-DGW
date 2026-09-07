package com.omes.dgw.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.collectlog.PollRoundContext;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.collect.DeviceDataParser;
import com.omes.dgw.device.controller.core.DeviceConnectionFailureHandler;
import com.omes.dgw.device.dto.DeviceConnectionTestVo;
import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.metrics.ProtocolCollectMetrics;
import com.omes.dgw.metrics.ProtocolPollSkipMetrics;
import com.omes.dgw.protocol.fins.*;
import com.omes.dgw.protocol.modbus.ModbusByteOrder;
import com.omes.dgw.protocol.modbus.ModbusNumericCodec;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Omron FINS/TCP 轮询（原生 FINS 客户端，不依赖 PLC4X FINS 驱动）。
 */
@Slf4j
@Component
public class FinsTcpPollingProtocolManager extends AbstractPlc4xPollingProtocolManager {

    private static final long CONNECTION_RETRY_DELAY_MS = 300L;

    private final ObjectMapper objectMapper;
    private final FinsConnectionSupport finsConnections;
    private final int maxMergeGapWords;
    private final int maxWordsPerRead;
    private final int maxWordsPerWrite;
    private final Map<String, FinsReadPlan> readPlans = new ConcurrentHashMap<>();

    public FinsTcpPollingProtocolManager(
            DeviceRealtimeCache realtimeCacheService,
            DeviceDataParser deviceDataParser,
            ProtocolCollectMetrics collectMetrics,
            ProtocolPollSkipMetrics pollSkipMetrics,
            DeviceConnectionFailureHandler connectionFailureHandler,
            ObjectMapper objectMapper,
            FinsConnectionSupport finsConnections,
            AppProperties appProperties) {
        super(realtimeCacheService, deviceDataParser, collectMetrics, pollSkipMetrics,
                connectionFailureHandler, FinsConnectParams.DEFAULT_TIMEOUT_MS, "fins-polling-",
                appProperties.getCollect());
        this.objectMapper = objectMapper;
        this.finsConnections = finsConnections;
        AppProperties.FinsPolling fins = appProperties.getCollect().getFinsPolling();
        this.maxMergeGapWords = Math.max(0, fins.getMaxMergeGapWords());
        this.maxWordsPerRead = FinsLimits.effectiveMaxWords(fins.getMaxWordsPerRead(), FinsLimits.MAX_WORDS_ABSOLUTE);
        this.maxWordsPerWrite = FinsLimits.effectiveMaxWords(fins.getMaxWordsPerWrite(), FinsLimits.MAX_WORDS_ABSOLUTE);
    }

    @Override
    public String protocol() {
        return DeviceProtocol.FINS_TCP.getCode();
    }

    @Override
    public boolean supports(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return false;
        }
        try {
            DeviceProtocol p = DeviceProtocol.fromCode(protocol);
            return p == DeviceProtocol.FINS_TCP;
        } catch (IllegalArgumentException e) {
            return protocol.toUpperCase().contains("FINS");
        }
    }

    @Override
    protected ConnectSpec buildConnectSpec(ProtocolConnect connect, Map<String, String> tagAddresses) {
        FinsConnectParams params = FinsConnectParamsParser.parse(objectMapper, connect.getParams());
        FinsTcpConnectionUrl.Parts parts = FinsTcpConnectionUrl.fromDevice(
                connect.getUri(), params.port(), params.timeoutMs(), params.localNode(), params.remoteNode());
        String connectionUrl = FinsTcpConnectionUrl.build(
                parts.host(), parts.port(), parts.localNode(), parts.remoteNode(), parts.requestTimeoutMs());
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
            readPlans.put(deviceId, FinsReadPlan.build(
                    spec.tagAddresses, spec.pointsByName, maxMergeGapWords, maxWordsPerRead));
        }
        return spec;
    }

    @Override
    public boolean stopPolling(String connectId, boolean preserveCircuitRecovery) {
        if (StringUtils.hasText(connectId)) {
            readPlans.remove(connectId);
        }
        return super.stopPolling(connectId, preserveCircuitRecovery);
    }

    @Override
    protected String resolveTagAddress(DevicePoint point) {
        return FinsAddressHelper.normalizeAddress(point.getAddress(), point.getDataType());
    }

    @Override
    public DeviceConnectionTestVo testConnection(ProtocolConnect connect) {
        if (connect == null || !StringUtils.hasText(connect.getUri())) {
            return DeviceConnectionTestVo.fail("连接地址不能为空");
        }
        ConnectSpec spec = buildConnectSpec(connect, Map.of());
        if (spec == null || !StringUtils.hasText(spec.connectionUrl)) {
            return DeviceConnectionTestVo.fail("无法解析连接参数");
        }
        long startNanos = System.nanoTime();
        long cooldownMs = finsConnections.ephemeralTestCooldownMs(spec.connectionUrl);
        if (cooldownMs > 0) {
            return DeviceConnectionTestVo.fail(
                    "请勿频繁测试，请 " + (cooldownMs / 1000 + (cooldownMs % 1000 > 0 ? 1 : 0))
                            + " 秒后再试（避免占满 PLC FINS/TCP 连接表）");
        }
        finsConnections.markEphemeralTestStarted(spec.connectionUrl);
        String testTarget = connect.getUri() + ":" + spec.connectionUrl;
        try {
            FinsTcpClient client = finsConnections.withEphemeralClient(spec.connectionUrl, c -> {
                c.setConnectionMode("test");
                try {
                    verifyReadableAfterHandshake(c, connect);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
                return c;
            });
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            FinsConnectionLog.testOk(testTarget, latencyMs, client.getEffectiveLocalNode(), client.getEffectiveRemoteNode());
            String message = String.format(
                    "连接成功 SA1=%d DA1=%d",
                    client.getEffectiveLocalNode(),
                    client.getEffectiveRemoteNode());
            return DeviceConnectionTestVo.ok(message, latencyMs);
        } catch (Exception e) {
            String message = formatTestFailureMessage(unwrapException(e));
            FinsConnectionLog.testFail(testTarget, message);
            return DeviceConnectionTestVo.fail(message);
        }
    }

    private static Exception unwrapException(Throwable error) {
        if (error instanceof Exception exception) {
            Throwable cause = exception.getCause();
            if (exception instanceof RuntimeException && cause instanceof Exception nested) {
                return nested;
            }
            return exception;
        }
        return new Exception(error);
    }

    private static String formatTestFailureMessage(Exception e) {
        String raw = e.getMessage() != null ? e.getMessage() : "连接失败";
        if (raw.contains("读验证失败")) {
            return raw;
        }
        if (raw.contains("Connection refused") || raw.contains("connect timed out")) {
            return raw + "；请检查 PLC 是否启用 FINS/TCP(9600)，并在 Sysmac/CX 中将采集机 IP 加入 FINS/包过滤允许列表（勿频繁测试以免被加入拒绝）";
        }
        return raw;
    }

    /**
     * 握手后读一个点验证：与轮询一致，避免「测试成功但采集读点全失败」。
     */
    private void verifyReadableAfterHandshake(FinsTcpClient client, ProtocolConnect connect) throws Exception {
        if (StringUtils.hasText(connect.getId())) {
            DeviceRealtimeObj cached = getDeviceCache(connect.getId());
            if (cached != null) {
                Map<String, DevicePoint> points = DevicePointConfigSupport.toPointsByName(cached);
                if (!points.isEmpty()) {
                    DevicePoint first = points.values().iterator().next();
                    DeviceCollectConfig config = DevicePointConfigSupport.normalize(cached.getCollectConfig());
                    readTag(client, resolveTagAddress(first), first, config.getModbusByteOrder());
                    return;
                }
            }
        }
        try {
            client.readWords(0x82, 0, 1);
        } catch (IOException e) {
            throw new IOException("读验证失败(DM0): " + e.getMessage() + "；请检查点位地址或数据区权限", e);
        }
    }

    @Override
    protected void evictPollingConnection(ConnectSpec spec) {
        if (spec != null && StringUtils.hasText(spec.connectionUrl)) {
            FinsConnectionLog.pollEvict(spec.deviceId, spec.connectionUrl, "poll-evict");
            finsConnections.evict(spec.connectionUrl, "poll-evict");
        }
    }

    @Override
    protected boolean isChunkLevelReadFailure(Exception e) {
        if (super.isChunkLevelReadFailure(e)) {
            return true;
        }
        String msg = e.getMessage();
        return StringUtils.hasText(msg) && msg.contains("全部 FINS 点位读取失败");
    }

    @Override
    protected boolean shouldEvictConnectionOnReadFailure(Exception e) {
        if (isChunkLevelReadFailure(e)) {
            return false;
        }
        if (isFinsEndCodeFailure(e)) {
            return false;
        }
        if (e instanceof IOException) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            if (isFinsEndCodeFailure(cause)) {
                return false;
            }
            if (cause instanceof IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return !(e instanceof IllegalArgumentException);
    }

    private static boolean isFinsEndCodeFailure(Throwable error) {
        String msg = error.getMessage();
        return StringUtils.hasText(msg) && msg.contains("endCode=0x");
    }

    @Override
    protected Map<String, Object> doRead(String connectId, ConnectSpec spec, PlcConnectionManager manager)
            throws Exception {
        if (spec != null) {
            spec.lastTagReadErrors.clear();
        }
        int maxAttempts = maxConnectionReadRetries();
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            abortReadIfInterrupted();
            long connStartNanos = System.nanoTime();
            try {
                Map<String, Object> values = finsConnections.withClient(
                        spec.connectionUrl, client -> {
                            client.setChunkLimits(maxWordsPerRead, maxWordsPerWrite);
                            try {
                                return readAllTags(client, spec);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                PollRoundContext.timing().addConnectMs(
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connStartNanos));
                return values;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("polling interrupted");
                }
                PollRoundContext.timing().addConnectMs(
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connStartNanos));
                lastException = e;
                if (shouldEvictConnectionOnReadFailure(e)) {
                    evictPollingConnection(spec);
                }
                boolean chunkFailure = isChunkLevelReadFailure(e);
                FinsConnectionLog.pollReadRetry(
                        spec.deviceId,
                        connectId,
                        spec.connectionUrl,
                        attempt,
                        maxAttempts,
                        chunkFailure,
                        e.getMessage());
                if (chunkFailure || attempt >= maxAttempts) {
                    break;
                }
                abortReadIfInterrupted();
                awaitRetryDelay(CONNECTION_RETRY_DELAY_MS);
            }
        }
        throw lastException;
    }

    @Override
    public ProtocolWriteResult write(String connectId, ProtocolWriteRequest request) {
        if (!StringUtils.hasText(connectId) || request == null
                || !StringUtils.hasText(request.address()) || request.value() == null) {
            return ProtocolWriteResult.fail("写入参数无效");
        }
        ConnectSpec spec = specMap.get(connectId);
        if (spec == null) {
            return ProtocolWriteResult.fail("设备未启动采集或连接未建立");
        }
        long startNanos = System.nanoTime();
        ProtocolWriteResult result = ProtocolWriteResult.fail("协议写入失败");
        try {
            if (spec.reusePollingConnection) {
                result = finsConnections.withClient(spec.connectionUrl,
                        client -> {
                            client.setChunkLimits(maxWordsPerRead, maxWordsPerWrite);
                            try {
                                return performFinsWrite(client, spec, request);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } else {
                result = finsConnections.withEphemeralClient(spec.connectionUrl,
                        client -> {
                            client.setChunkLimits(maxWordsPerRead, maxWordsPerWrite);
                            try {
                                return performFinsWrite(client, spec, request);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            if (result.success()) {
                onWriteSuccess(connectId);
            }
        } catch (Exception e) {
            log.error("{} write error connectId={}: {}", protocol(), connectId, e.getMessage());
            if (spec.reusePollingConnection && shouldEvictConnectionOnReadFailure(toException(e))) {
                evictPollingConnection(spec);
            }
            result = ProtocolWriteResult.fail(e.getMessage());
        } finally {
            recordWrite(spec, result.success(), startNanos);
        }
        return result;
    }

    private Map<String, Object> readAllTags(FinsTcpClient client, ConnectSpec spec) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        FinsReadPlan plan = readPlans.get(spec.deviceId);
        if (plan == null) {
            plan = FinsReadPlan.build(spec.tagAddresses, spec.pointsByName, maxMergeGapWords, maxWordsPerRead);
        }
        for (FinsPollingChunk chunk : plan.wordChunks()) {
            readWordChunk(client, spec, chunk, result);
        }
        for (FinsPollingChunk chunk : plan.bitWordChunks()) {
            readBitWordChunk(client, spec, chunk, result);
        }
        for (FinsPollingTag tag : plan.fallbackTags()) {
            readSingleTag(client, spec, tag.tagName(), spec.tagAddresses.get(tag.tagName()), tag.point(), result);
        }
        return result;
    }

    private void readWordChunk(
            FinsTcpClient client, ConnectSpec spec, FinsPollingChunk chunk, Map<String, Object> result
    ) throws Exception {
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            int[] block = client.readWords(chunk.memoryAreaCode(), chunk.startWordAddress(), chunk.wordCount());
            for (FinsPollingTag tag : chunk.tags()) {
                int offset = tag.wordAddress() - chunk.startWordAddress();
                if (offset < 0 || offset + tag.spanWords() > block.length) {
                    recordTagReadError(spec, tag.tagName(), spec.tagAddresses.get(tag.tagName()),
                            new IOException("FINS 合并读块内偏移越界"));
                    PollRoundContext.markPartialReadFailure();
                    continue;
                }
                int[] words = Arrays.copyOfRange(block, offset, offset + tag.spanWords());
                Object value = tag.point() != null
                        ? FinsNumericCodec.decodeWords(words, tag.point(), spec.modbusByteOrder)
                        : words[0];
                if (value != null) {
                    result.put(tag.tagName(), value);
                }
            }
            success = true;
        } catch (Exception e) {
            handleChunkFailure(client, spec, chunk, result, e);
            if (PollTransportFailures.isTransportFailure(e)) {
                throw e;
            }
        } finally {
            recordRead(spec, success, startNanos);
        }
    }

    private void readBitWordChunk(
            FinsTcpClient client, ConnectSpec spec, FinsPollingChunk chunk, Map<String, Object> result
    ) throws Exception {
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            int[] words = client.readWords(chunk.memoryAreaCode(), chunk.startWordAddress(), 1);
            int word = words[0];
            for (FinsPollingTag tag : chunk.tags()) {
                int bit = tag.bitOffset() != null ? tag.bitOffset() : 0;
                boolean bitValue = ((word >> bit) & 0x01) == 1;
                Object value = tag.point() != null && tag.point().getDataType() != null
                        ? ModbusNumericCodec.decodeBool(bitValue, tag.point().getDataType())
                        : bitValue;
                result.put(tag.tagName(), value);
            }
            success = true;
        } catch (Exception e) {
            handleChunkFailure(client, spec, chunk, result, e);
            if (PollTransportFailures.isTransportFailure(e)) {
                throw e;
            }
        } finally {
            recordRead(spec, success, startNanos);
        }
    }

    private void handleChunkFailure(
            FinsTcpClient client,
            ConnectSpec spec,
            FinsPollingChunk chunk,
            Map<String, Object> result,
            Exception chunkError
    ) throws Exception {
        log.warn("{} read chunk failed connectId={} area=0x{} start={} count={}: {}",
                protocol(), spec.deviceId, Integer.toHexString(chunk.memoryAreaCode()),
                chunk.startWordAddress(), chunk.wordCount(), chunkError.getMessage());
        PollRoundContext.markPartialReadFailure();
        for (FinsPollingTag tag : chunk.tags()) {
            String address = spec.tagAddresses.get(tag.tagName());
            try {
                readSingleTag(client, spec, tag.tagName(), address, tag.point(), result);
            } catch (Exception e) {
                recordTagReadError(spec, tag.tagName(), address, e);
                if (PollTransportFailures.isTransportFailure(e)) {
                    throw e;
                }
            }
        }
    }

    private void readSingleTag(
            FinsTcpClient client,
            ConnectSpec spec,
            String tagName,
            String address,
            DevicePoint point,
            Map<String, Object> result
    ) throws Exception {
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            Object value = readTag(client, address, point, spec.modbusByteOrder);
            if (value != null) {
                result.put(tagName, value);
                success = true;
            }
        } catch (Exception e) {
            recordTagReadError(spec, tagName, address, e);
            PollRoundContext.markPartialReadFailure();
            if (PollTransportFailures.isTransportFailure(e)) {
                throw e;
            }
        } finally {
            recordRead(spec, success, startNanos);
        }
    }

    private void recordTagReadError(ConnectSpec spec, String tagName, String address, Exception e) {
        log.warn("{} read tag failed connectId={} tag={} address={}: {}",
                protocol(), spec.deviceId, tagName, address, e.getMessage());
        if (spec.lastTagReadErrors != null) {
            spec.lastTagReadErrors.put(tagName, e.getMessage());
        }
    }

    private Object readTag(FinsTcpClient client, String address, DevicePoint point, ModbusByteOrder deviceOrder)
            throws Exception {
        FinsAddressHelper.Parsed parsed = FinsAddressHelper.parse(address);
        if (parsed.bitAccess() || (point != null && point.getDataType() != null && point.getDataType().isBoolType())) {
            int bit = parsed.bitOffset() != null ? parsed.bitOffset() : 0;
            boolean bitValue = client.readBit(parsed.memoryAreaCode(), parsed.wordAddress(), bit);
            if (point != null && point.getDataType() != null) {
                return ModbusNumericCodec.decodeBool(bitValue, point.getDataType());
            }
            return bitValue;
        }
        int wordCount = FinsAddressHelper.wordCount(address, point != null ? point.getDataType() : null,
                point != null ? point.getStringLength() : null);
        int[] words = client.readWords(parsed.memoryAreaCode(), parsed.wordAddress(), wordCount);
        return point != null ? FinsNumericCodec.decodeWords(words, point, deviceOrder) : words[0];
    }

    private ProtocolWriteResult performFinsWrite(
            FinsTcpClient client, ConnectSpec spec, ProtocolWriteRequest request
    ) throws Exception {
        DevicePoint point = resolveWritePoint(spec, request.address());
        FinsAddressHelper.Parsed parsed = FinsAddressHelper.parse(request.address());
        // 请求中的类型/字节序优先：自由写 REAL+ABCD 时，不能被点位默认 CDAB 或设备 Modbus 字序覆盖
        PointDataType dataType = request.dataType() != null
                ? request.dataType()
                : (point != null ? point.getDataType() : null);
        ModbusByteOrder byteOrder = request.byteOrder() != null
                ? request.byteOrder()
                : (point != null ? point.getByteOrder() : null);
        if (parsed.bitAccess() || (dataType != null && dataType.isBoolType())) {
            int bit = parsed.bitOffset() != null ? parsed.bitOffset() : 0;
            boolean wire = FinsNumericCodec.encodeWriteBit(
                    request.value(),
                    dataType != null ? dataType : PointDataType.BOOL);
            client.writeBit(parsed.memoryAreaCode(), parsed.wordAddress(), bit, wire);
            return ProtocolWriteResult.ok();
        }
        if (dataType == null) {
            return ProtocolWriteResult.fail("写入缺少数据类型");
        }
        Integer stringLength = point != null ? point.getStringLength() : null;
        // 点位/请求字节序为空时按 CDAB 解析
        ModbusByteOrder deviceFallback = byteOrder != null
                ? spec.modbusByteOrder
                : ModbusByteOrder.CDAB;
        int[] words = FinsNumericCodec.encodeWriteWords(
                request.value(), dataType, byteOrder, stringLength, deviceFallback);
        client.writeWords(parsed.memoryAreaCode(), parsed.wordAddress(), words);
        return ProtocolWriteResult.ok();
    }

    private DevicePoint resolveWritePoint(ConnectSpec spec, String address) {
        if (spec == null || spec.writablePointsByName == null || !StringUtils.hasText(address)) {
            return null;
        }
        for (DevicePoint point : spec.writablePointsByName.values()) {
            if (point != null && address.equalsIgnoreCase(point.getSetpointAddress())) {
                return point;
            }
            if (point != null && StringUtils.hasText(point.getSetpointAddress())
                    && address.equalsIgnoreCase(FinsAddressHelper.normalizeAddress(point.getSetpointAddress(), point.getDataType()))) {
                return point;
            }
        }
        return null;
    }

    private static Exception toException(Throwable error) {
        if (error instanceof Exception exception) {
            return exception;
        }
        return new Exception(error);
    }
}
