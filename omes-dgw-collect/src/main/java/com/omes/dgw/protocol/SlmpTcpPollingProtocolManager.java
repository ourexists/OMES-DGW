package com.omes.dgw.protocol;

import com.omes.dgw.device.connect.ProtocolConnect;
import com.omes.dgw.device.connect.ProtocolWriteRequest;
import com.omes.dgw.device.connect.ProtocolWriteResult;

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
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import com.omes.dgw.codec.modbus.ModbusNumericCodec;
import com.omes.dgw.codec.slmp.SlmpTcpConnectionUrl;
import com.omes.dgw.codec.slmp.SlmpConnectParams;
import com.omes.dgw.codec.slmp.SlmpConnectParamsParser;
import com.omes.dgw.protocol.slmp.*;
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
 * 三菱 SLMP/MC 协议 3E 帧 TCP 轮询（原生客户端，默认端口 5000）。
 */
@Slf4j
@Component
public class SlmpTcpPollingProtocolManager extends AbstractPlc4xPollingProtocolManager {

    private static final long CONNECTION_RETRY_DELAY_MS = 300L;

    private final ObjectMapper objectMapper;
    private final SlmpConnectionSupport slmpConnections;
    private final int maxMergeGapWords;
    private final int maxMergeGapBits;
    private final int maxWordsPerRequest;
    private final int maxBitsPerRequest;
    private final Map<String, SlmpReadPlan> readPlans = new ConcurrentHashMap<>();

    public SlmpTcpPollingProtocolManager(
            DeviceRealtimeCache realtimeCacheService,
            DeviceDataParser deviceDataParser,
            ProtocolCollectMetrics collectMetrics,
            ProtocolPollSkipMetrics pollSkipMetrics,
            DeviceConnectionFailureHandler connectionFailureHandler,
            ObjectMapper objectMapper,
            SlmpConnectionSupport slmpConnections,
            AppProperties appProperties) {
        super(realtimeCacheService, deviceDataParser, collectMetrics, pollSkipMetrics,
                connectionFailureHandler, SlmpConnectParams.DEFAULT_TIMEOUT_MS, "slmp-polling-",
                appProperties.getCollect());
        this.objectMapper = objectMapper;
        this.slmpConnections = slmpConnections;
        AppProperties.SlmpPolling slmp = appProperties.getCollect().getSlmpPolling();
        this.maxMergeGapWords = Math.max(0, slmp.getMaxMergeGapWords());
        this.maxMergeGapBits = Math.max(0, slmp.getMaxMergeGapBits());
        this.maxWordsPerRequest = SlmpLimits.effectiveMaxWords(slmp.getMaxWordsPerRequest());
        this.maxBitsPerRequest = SlmpLimits.effectiveMaxBits(slmp.getMaxBitsPerRequest());
    }

    @Override
    public String protocol() {
        return DeviceProtocol.SLMP_TCP.getCode();
    }

    @Override
    public boolean supports(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return false;
        }
        try {
            DeviceProtocol p = DeviceProtocol.fromCode(protocol);
            return p == DeviceProtocol.SLMP_TCP;
        } catch (IllegalArgumentException e) {
            String upper = protocol.toUpperCase();
            return upper.contains("SLMP") || upper.contains("MELSEC") || upper.contains("MC");
        }
    }

    @Override
    protected ConnectSpec buildConnectSpec(ProtocolConnect connect, Map<String, String> tagAddresses) {
        SlmpConnectParams params = SlmpConnectParamsParser.parse(objectMapper, connect.getParams());
        SlmpTcpConnectionUrl.Parts parts = SlmpTcpConnectionUrl.fromDevice(
                connect.getUri(), params.port(), params.timeoutMs(), params.networkNo(), params.stationNo());
        String connectionUrl = SlmpTcpConnectionUrl.build(
                parts.host(), parts.port(), parts.networkNo(), parts.stationNo(), parts.requestTimeoutMs());
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
            readPlans.put(deviceId, SlmpReadPlan.build(
                    spec.tagAddresses,
                    spec.pointsByName,
                    maxMergeGapWords,
                    maxMergeGapBits,
                    maxWordsPerRequest,
                    maxBitsPerRequest));
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
        return SlmpAddressHelper.normalizeAddress(point.getAddress(), point.getDataType());
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
        long cooldownMs = slmpConnections.ephemeralTestCooldownMs(spec.connectionUrl);
        if (cooldownMs > 0) {
            return DeviceConnectionTestVo.fail(
                    "请勿频繁测试，请 " + (cooldownMs / 1000 + (cooldownMs % 1000 > 0 ? 1 : 0)) + " 秒后再试");
        }
        slmpConnections.markEphemeralTestStarted(spec.connectionUrl);
        String testTarget = connect.getUri() + ":" + spec.connectionUrl;
        try {
            slmpConnections.withTestClient(spec.connectionUrl, client -> {
                try {
                    verifyReadableAfterConnect(client, connect);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            SlmpConnectionLog.testOk(testTarget, latencyMs);
            return DeviceConnectionTestVo.ok("连接成功", latencyMs);
        } catch (Exception e) {
            String message = formatTestFailureMessage(unwrapException(e));
            SlmpConnectionLog.testFail(testTarget, message);
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
            return raw + "；请检查 PLC 是否启用 SLMP/MC（TCP，常见端口 5000，不是 MELSOFT 编程口）";
        }
        return raw;
    }

    private void verifyReadableAfterConnect(SlmpTcpClient client, ProtocolConnect connect) throws Exception {
        if (StringUtils.hasText(connect.getId())) {
            DeviceRealtimeObj cached = getDeviceCache(connect.getId());
            if (cached != null) {
                Map<String, DevicePoint> points = DevicePointConfigSupport.toPointsByName(cached);
                if (!points.isEmpty()) {
                    DevicePoint first = points.values().iterator().next();
                    DeviceCollectConfig config = DevicePointConfigSupport.normalize(cached.getCollectConfig());
                    try {
                        readTag(client, resolveTagAddress(first), first, config.getModbusByteOrder());
                    } catch (SlmpEndCodeException e) {
                        if (e.isCommandOrDataError()) {
                            throw e;
                        }
                        log.info("SLMP 协议已通，点位 {} 返回 {}", first.getAddress(), e.getMessage());
                    }
                    return;
                }
            }
        }
        try {
            client.readWords(0xA8, 0, 1);
        } catch (SlmpEndCodeException e) {
            if (e.isCommandOrDataError()) {
                throw e;
            }
            log.info("SLMP 协议已通，探测 D0 返回 {}", e.getMessage());
        } catch (IOException e) {
            throw new IOException("读验证失败(D0): " + e.getMessage()
                    + "；请检查端口是否为 SLMP/MC、帧 3E/4E Binary、站号是否为本站(255)", e);
        }
    }

    @Override
    protected void evictPollingConnection(ConnectSpec spec) {
        if (spec != null && StringUtils.hasText(spec.connectionUrl)) {
            SlmpConnectionLog.pollEvict(spec.deviceId, spec.connectionUrl, "poll-evict");
            slmpConnections.evict(spec.connectionUrl, "poll-evict");
        }
    }

    @Override
    protected boolean shouldEvictConnectionOnReadFailure(Exception e) {
        if (isSlmpEndCodeFailure(e)) {
            return false;
        }
        if (e instanceof IOException) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            if (isSlmpEndCodeFailure(cause)) {
                return false;
            }
            if (cause instanceof IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return !(e instanceof IllegalArgumentException);
    }

    private static boolean isSlmpEndCodeFailure(Throwable error) {
        if (error instanceof SlmpEndCodeException) {
            return true;
        }
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
                Map<String, Object> values = slmpConnections.withClient(
                        spec.connectionUrl, client -> {
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
                SlmpConnectionLog.pollReadRetry(
                        spec.deviceId,
                        connectId,
                        spec.connectionUrl,
                        attempt,
                        maxAttempts,
                        false,
                        e.getMessage());
                if (attempt >= maxAttempts) {
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
                result = slmpConnections.withClient(spec.connectionUrl,
                        client -> {
                            try {
                                return performSlmpWrite(client, spec, request);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } else {
                result = slmpConnections.withEphemeralClient(spec.connectionUrl,
                        client -> {
                            try {
                                return performSlmpWrite(client, spec, request);
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

    private Map<String, Object> readAllTags(SlmpTcpClient client, ConnectSpec spec) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        SlmpReadPlan plan = readPlans.get(spec.deviceId);
        if (plan == null) {
            plan = SlmpReadPlan.build(
                    spec.tagAddresses,
                    spec.pointsByName,
                    maxMergeGapWords,
                    maxMergeGapBits,
                    maxWordsPerRequest,
                    maxBitsPerRequest);
        }
        for (SlmpPollingChunk chunk : plan.wordChunks()) {
            readWordChunk(client, spec, chunk, result);
        }
        for (SlmpPollingChunk chunk : plan.bitChunks()) {
            readBitChunk(client, spec, chunk, result);
        }
        for (SlmpPollingTag tag : plan.fallbackTags()) {
            readSingleTag(client, spec, tag.tagName(), spec.tagAddresses.get(tag.tagName()), tag.point(), result);
        }
        return result;
    }

    private void readWordChunk(
            SlmpTcpClient client, ConnectSpec spec, SlmpPollingChunk chunk, Map<String, Object> result
    ) throws Exception {
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            int[] block = client.readWords(chunk.deviceCode(), chunk.startDeviceNumber(), chunk.count());
            for (SlmpPollingTag tag : chunk.tags()) {
                readTagFromWordBlock(spec, tag, block, chunk.startDeviceNumber(), result);
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

    private void readBitChunk(
            SlmpTcpClient client, ConnectSpec spec, SlmpPollingChunk chunk, Map<String, Object> result
    ) throws Exception {
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            boolean[] block = client.readBits(chunk.deviceCode(), chunk.startDeviceNumber(), chunk.count());
            for (SlmpPollingTag tag : chunk.tags()) {
                int index = tag.deviceNumber() - chunk.startDeviceNumber();
                if (index < 0 || index >= block.length) {
                    continue;
                }
                readTagValue(spec, tag.tagName(), tag.point(), block[index], result);
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
            SlmpTcpClient client,
            ConnectSpec spec,
            SlmpPollingChunk chunk,
            Map<String, Object> result,
            Exception chunkError
    ) throws Exception {
        log.warn("{} read chunk failed connectId={} deviceCode=0x{} count={}: {}",
                protocol(), spec.deviceId, Integer.toHexString(chunk.deviceCode()), chunk.count(),
                chunkError.getMessage());
        PollRoundContext.markPartialReadFailure();
        for (SlmpPollingTag tag : chunk.tags()) {
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

    private void readTagFromWordBlock(
            ConnectSpec spec,
            SlmpPollingTag tag,
            int[] block,
            int blockStart,
            Map<String, Object> result
    ) {
        int offset = tag.deviceNumber() - blockStart;
        if (offset < 0 || offset + tag.spanUnits() > block.length) {
            recordTagReadError(spec, tag.tagName(), spec.tagAddresses.get(tag.tagName()),
                    new IOException("SLMP 合并读块内偏移越界"));
            PollRoundContext.markPartialReadFailure();
            return;
        }
        int[] words = Arrays.copyOfRange(block, offset, offset + tag.spanUnits());
        if (tag.bitInWord() >= 0) {
            boolean bit = ((words[0] >> tag.bitInWord()) & 1) == 1;
            readTagValue(spec, tag.tagName(), tag.point(), bit, result);
            return;
        }
        Object value = tag.point() != null
                ? SlmpNumericCodec.decodeWords(words, tag.point(), spec.modbusByteOrder)
                : words[0];
        if (value != null && result != null) {
            result.put(tag.tagName(), value);
        }
    }

    private void readSingleTag(
            SlmpTcpClient client,
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
            if (value != null && result != null) {
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

    private void readTagValue(
            ConnectSpec spec,
            String tagName,
            DevicePoint point,
            boolean bitValue,
            Map<String, Object> result
    ) {
        Object value = point != null && point.getDataType() != null
                ? ModbusNumericCodec.decodeBool(bitValue, point.getDataType())
                : bitValue;
        if (value != null && result != null) {
            result.put(tagName, value);
        }
    }

    private void recordTagReadError(ConnectSpec spec, String tagName, String address, Exception e) {
        log.warn("{} read tag failed connectId={} tag={} address={}: {}",
                protocol(), spec.deviceId, tagName, address, e.getMessage());
        if (spec.lastTagReadErrors != null) {
            spec.lastTagReadErrors.put(tagName, e.getMessage());
        }
    }

    private Object readTag(SlmpTcpClient client, String address, DevicePoint point, ModbusByteOrder deviceOrder)
            throws Exception {
        SlmpAddressHelper.Parsed parsed = SlmpAddressHelper.parse(address);
        if (parsed.wordDeviceBit()) {
            int[] words = client.readWords(parsed.deviceCode(), parsed.deviceNumber(), 1);
            boolean bitValue = ((words[0] >> parsed.bitInWord()) & 1) == 1;
            if (point != null && point.getDataType() != null) {
                return ModbusNumericCodec.decodeBool(bitValue, point.getDataType());
            }
            return bitValue;
        }
        boolean bitAccess = SlmpAddressHelper.isBitAccess(address, point != null ? point.getDataType() : null);
        if (bitAccess) {
            boolean bitValue = client.readBit(parsed.deviceCode(), parsed.deviceNumber());
            if (point != null && point.getDataType() != null) {
                return ModbusNumericCodec.decodeBool(bitValue, point.getDataType());
            }
            return bitValue;
        }
        int wordCount = SlmpAddressHelper.wordCount(address, point != null ? point.getDataType() : null,
                point != null ? point.getStringLength() : null);
        int[] words = client.readWords(parsed.deviceCode(), parsed.deviceNumber(), wordCount);
        return point != null ? SlmpNumericCodec.decodeWords(words, point, deviceOrder) : words[0];
    }

    private ProtocolWriteResult performSlmpWrite(
            SlmpTcpClient client, ConnectSpec spec, ProtocolWriteRequest request
    ) throws Exception {
        DevicePoint point = resolveWritePoint(spec, request.address());
        SlmpAddressHelper.Parsed parsed = SlmpAddressHelper.parse(request.address());
        PointDataType dataType = request.dataType() != null
                ? request.dataType()
                : (point != null ? point.getDataType() : null);
        ModbusByteOrder byteOrder = request.byteOrder() != null
                ? request.byteOrder()
                : (point != null ? point.getByteOrder() : null);
        if (parsed.wordDeviceBit()) {
            boolean wire = SlmpNumericCodec.encodeWriteBit(
                    request.value(),
                    dataType != null ? dataType : PointDataType.BOOL);
            int[] words = client.readWords(parsed.deviceCode(), parsed.deviceNumber(), 1);
            int next = wire
                    ? (words[0] | (1 << parsed.bitInWord()))
                    : (words[0] & ~(1 << parsed.bitInWord()));
            client.writeWords(parsed.deviceCode(), parsed.deviceNumber(), new int[] {next & 0xFFFF});
            return ProtocolWriteResult.ok();
        }
        boolean bitAccess = SlmpAddressHelper.isBitAccess(request.address(), dataType);
        if (bitAccess) {
            boolean wire = SlmpNumericCodec.encodeWriteBit(
                    request.value(),
                    dataType != null ? dataType : PointDataType.BOOL);
            client.writeBit(parsed.deviceCode(), parsed.deviceNumber(), wire);
            return ProtocolWriteResult.ok();
        }
        if (dataType == null) {
            return ProtocolWriteResult.fail("写入缺少数据类型");
        }
        Integer stringLength = point != null ? point.getStringLength() : null;
        int[] words = SlmpNumericCodec.encodeWriteWords(
                request.value(), dataType, byteOrder, stringLength, spec.modbusByteOrder);
        client.writeWords(parsed.deviceCode(), parsed.deviceNumber(), words);
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
                    && address.equalsIgnoreCase(
                    SlmpAddressHelper.normalizeAddress(point.getSetpointAddress(), point.getDataType()))) {
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
