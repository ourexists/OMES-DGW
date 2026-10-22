package com.omes.dgw.protocol;

import com.omes.dgw.device.connect.ProtocolConnect;
import com.omes.dgw.device.connect.ProtocolWriteRequest;
import com.omes.dgw.device.connect.ProtocolWriteResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.s7connector.api.S7Connector;
import com.omes.dgw.collectlog.PollRoundContext;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.collect.DeviceDataParser;
import com.omes.dgw.device.controller.core.DeviceConnectionFailureHandler;
import com.omes.dgw.device.dto.DeviceConnectionTestVo;
import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.metrics.ProtocolCollectMetrics;
import com.omes.dgw.metrics.ProtocolPollSkipMetrics;
import com.omes.dgw.protocol.s7.S7AddressHelper;
import com.omes.dgw.protocol.s7.S7AddressHelper.S7Address;
import com.omes.dgw.protocol.s7.S7AddressHelper.S7ReadSpan;
import com.omes.dgw.codec.s7.S7ConnectParamsParser;
import com.omes.dgw.protocol.s7.S7ConnectionSupport;
import com.omes.dgw.protocol.s7.S7NumericCodec;
import com.omes.dgw.codec.s7.S7TcpConnectionUrl;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 西门子 S7 轮询（s7connector / nodave），接入现有采集调度。
 */
@Slf4j
@Component
public class S7PollingProtocolManager extends AbstractPlc4xPollingProtocolManager {

    private static final long CONNECTION_RETRY_DELAY_MS = 300L;

    private final ObjectMapper objectMapper;
    private final S7ConnectionSupport connections;

    public S7PollingProtocolManager(
            DeviceRealtimeCache realtimeCacheService,
            DeviceDataParser deviceDataParser,
            ProtocolCollectMetrics collectMetrics,
            ProtocolPollSkipMetrics pollSkipMetrics,
            DeviceConnectionFailureHandler connectionFailureHandler,
            ObjectMapper objectMapper,
            S7ConnectionSupport connections,
            AppProperties appProperties) {
        super(realtimeCacheService, deviceDataParser, collectMetrics, pollSkipMetrics,
                connectionFailureHandler, 5_000, "s7-polling-",
                appProperties.getCollect());
        this.objectMapper = objectMapper;
        this.connections = connections;
    }

    @Override
    public String protocol() {
        return DeviceProtocol.S7_TCP.getCode();
    }

    @Override
    public boolean supports(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return false;
        }
        try {
            return DeviceProtocol.fromCode(protocol) == DeviceProtocol.S7_TCP;
        } catch (IllegalArgumentException e) {
            String upper = protocol.toUpperCase();
            return "S7".equals(upper) || upper.contains("S7");
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
        var params = S7ConnectParamsParser.parse(objectMapper, connect.getParams());
        S7TcpConnectionUrl.Parts parts = S7TcpConnectionUrl.fromDevice(
                connect.getUri(), params.port(), params.timeoutMs(), params.rack(), params.slot());
        return new ConnectSpec(
                S7TcpConnectionUrl.build(parts),
                new LinkedHashMap<>(tagAddresses),
                parts.requestTimeoutMs());
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
        long cooldownMs = connections.testCooldownMs(spec.connectionUrl);
        if (cooldownMs > 0) {
            long seconds = cooldownMs / 1000 + (cooldownMs % 1000 > 0 ? 1 : 0);
            return DeviceConnectionTestVo.fail("请勿频繁测试，请 " + seconds + " 秒后再试");
        }
        connections.markTestStarted(spec.connectionUrl);
        long startNanos = System.nanoTime();
        try {
            connections.withEphemeral(spec.connectionUrl, client -> client);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            return DeviceConnectionTestVo.ok("连接成功", latencyMs);
        } catch (Exception e) {
            return DeviceConnectionTestVo.fail(formatTestFailure(e));
        }
    }

    @Override
    protected void evictPollingConnection(ConnectSpec spec) {
        if (spec != null && StringUtils.hasText(spec.connectionUrl)) {
            connections.evict(spec.connectionUrl);
        }
    }

    @Override
    protected boolean shouldEvictConnectionOnReadFailure(Exception e) {
        return !(e instanceof IllegalArgumentException) && PollTransportFailures.isTransportFailure(e);
    }

    @Override
    protected Map<String, Object> doRead(String connectId, ConnectSpec spec, PlcConnectionManager manager)
            throws Exception {
        if (spec != null && spec.lastTagReadErrors != null) {
            spec.lastTagReadErrors.clear();
        }
        int maxAttempts = maxConnectionReadRetries();
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            abortReadIfInterrupted();
            long connStartNanos = System.nanoTime();
            try {
                Map<String, Object> values = connections.withClient(spec.connectionUrl, client -> {
                    try {
                        return readAll(client, spec);
                    } catch (Exception e) {
                        if (e instanceof RuntimeException runtime) {
                            throw runtime;
                        }
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
                lastException = unwrap(e);
                if (shouldEvictConnectionOnReadFailure(lastException)) {
                    evictPollingConnection(spec);
                }
                log.warn("{} connection read {}/{} failed connectId={}: {}",
                        protocol(), attempt, maxAttempts, connectId, lastException.getMessage());
                if (!shouldEvictConnectionOnReadFailure(lastException) || attempt >= maxAttempts) {
                    break;
                }
                abortReadIfInterrupted();
                awaitRetryDelay(CONNECTION_RETRY_DELAY_MS);
            }
        }
        throw lastException != null ? lastException : new IllegalStateException("S7 read failed");
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
            result = connections.withClient(spec.connectionUrl, client -> {
                try {
                    return performWrite(client, spec, request);
                } catch (Exception e) {
                    if (e instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new RuntimeException(e);
                }
            });
            if (result.success()) {
                onWriteSuccess(connectId);
            }
        } catch (Exception e) {
            log.error("{} write error connectId={}: {}", protocol(), connectId, e.getMessage());
            Exception cause = unwrap(e);
            if (shouldEvictConnectionOnReadFailure(cause)) {
                evictPollingConnection(spec);
            }
            result = ProtocolWriteResult.fail(cause.getMessage() != null ? cause.getMessage() : "协议写入失败");
        } finally {
            recordWrite(spec, result.success(), startNanos);
        }
        return result;
    }

    private Map<String, Object> readAll(S7Connector connector, ConnectSpec spec) throws Exception {
        List<S7ReadSpan> spans = new ArrayList<>();
        for (Map.Entry<String, String> entry : spec.tagAddresses.entrySet()) {
            DevicePoint point = spec.pointsByName.get(entry.getKey());
            try {
                S7Address address = S7AddressHelper.parse(entry.getValue());
                spans.add(new S7ReadSpan(entry.getKey(), address, S7AddressHelper.byteCount(address, point)));
            } catch (IllegalArgumentException e) {
                recordTagError(spec, entry.getKey(), entry.getValue(), e);
                PollRoundContext.markPartialReadFailure();
            }
        }
        Map<String, List<S7ReadSpan>> groups = new LinkedHashMap<>();
        for (S7ReadSpan span : spans) {
            groups.computeIfAbsent(span.address().groupKey(), key -> new ArrayList<>()).add(span);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (List<S7ReadSpan> group : groups.values()) {
            group.sort(Comparator.comparingInt(span -> span.address().offset()));
            for (List<S7ReadSpan> chunk : S7AddressHelper.splitChunks(group, S7AddressHelper.MAX_READ_BYTES)) {
                readChunk(connector, spec, chunk, result);
            }
        }
        return result;
    }

    private void readChunk(
            S7Connector connector, ConnectSpec spec, List<S7ReadSpan> chunk, Map<String, Object> result
    ) throws Exception {
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            readBlock(connector, spec, chunk, result);
            success = true;
        } catch (Exception e) {
            if (PollTransportFailures.isTransportFailure(e) || chunk.size() == 1) {
                if (chunk.size() == 1 && !PollTransportFailures.isTransportFailure(e)) {
                    S7ReadSpan span = chunk.get(0);
                    recordTagError(spec, span.tag(), spec.tagAddresses.get(span.tag()), e);
                    PollRoundContext.markPartialReadFailure();
                    return;
                }
                throw e;
            }
            for (S7ReadSpan span : chunk) {
                try {
                    readBlock(connector, spec, List.of(span), result);
                } catch (Exception one) {
                    if (PollTransportFailures.isTransportFailure(one)) {
                        throw one;
                    }
                    recordTagError(spec, span.tag(), spec.tagAddresses.get(span.tag()), one);
                    PollRoundContext.markPartialReadFailure();
                }
            }
        } finally {
            recordRead(spec, success, startNanos);
        }
    }

    private void readBlock(
            S7Connector connector, ConnectSpec spec, List<S7ReadSpan> chunk, Map<String, Object> result
    ) {
        S7Address first = chunk.get(0).address();
        int minOffset = first.offset();
        int maxEnd = minOffset;
        for (S7ReadSpan span : chunk) {
            maxEnd = Math.max(maxEnd, span.address().offset() + span.byteCount());
        }
        int totalBytes = maxEnd - minOffset;
        byte[] block = connector.read(first.area(), first.areaNumber(), totalBytes, minOffset);
        if (block == null || block.length < totalBytes) {
            throw new IllegalStateException("S7 响应长度不足");
        }
        for (S7ReadSpan span : chunk) {
            int offset = span.address().offset() - minOffset;
            byte[] slice = Arrays.copyOfRange(block, offset, offset + span.byteCount());
            DevicePoint point = spec.pointsByName.get(span.tag());
            result.put(span.tag(), S7NumericCodec.decode(slice, span.address(), point));
        }
    }

    private ProtocolWriteResult performWrite(S7Connector connector, ConnectSpec spec, ProtocolWriteRequest request) {
        DevicePoint point = resolveWritePoint(spec, request.address());
        PointDataType dataType = request.dataType() != null
                ? request.dataType()
                : (point != null ? point.getDataType() : null);
        S7Address address = S7AddressHelper.parse(request.address());
        if (address.bit() || (dataType != null && dataType.isBoolType())) {
            int bit = address.bitNumber() >= 0 ? address.bitNumber() : 0;
            byte[] current = connector.read(address.area(), address.areaNumber(), 1, address.offset());
            if (current == null || current.length < 1) {
                return ProtocolWriteResult.fail("S7 读取长度不足");
            }
            byte merged = S7NumericCodec.mergeBit(current[0], bit, S7NumericCodec.toWireBit(request.value()));
            connector.write(address.area(), address.areaNumber(), address.offset(), new byte[]{merged});
            return ProtocolWriteResult.ok();
        }
        if (dataType == null) {
            return ProtocolWriteResult.fail("写入缺少数据类型");
        }
        Integer stringLength = point != null ? point.getStringLength() : null;
        int byteCount = S7AddressHelper.byteCount(address, dataType, stringLength);
        byte[] data = S7NumericCodec.encode(request.value(), dataType, byteCount);
        connector.write(address.area(), address.areaNumber(), address.offset(), data);
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
        }
        return null;
    }

    private void recordTagError(ConnectSpec spec, String tagName, String address, Exception e) {
        log.warn("{} read tag failed connectId={} tag={} address={}: {}",
                protocol(), spec.deviceId, tagName, address, e.getMessage());
        if (spec.lastTagReadErrors != null) {
            spec.lastTagReadErrors.put(tagName, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static String formatTestFailure(Exception e) {
        Exception cause = unwrap(e);
        String raw = cause.getMessage() != null ? cause.getMessage() : "连接失败";
        if (raw.contains("Connection refused") || raw.toLowerCase().contains("timed out")) {
            return raw + "；请确认 PLC 已开放 102 端口，S7-1200/1500 需在 CPU 属性中允许 PUT/GET";
        }
        return raw;
    }

    private static Exception unwrap(Throwable error) {
        if (error instanceof Exception exception) {
            Throwable cause = exception.getCause();
            if (exception instanceof RuntimeException && cause instanceof Exception nested && nested != exception) {
                return nested;
            }
            return exception;
        }
        return new Exception(error);
    }
}
