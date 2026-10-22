package com.omes.dgw.collectlog;

import com.omes.dgw.protocol.PlcTagResponseSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 PLC4X 响应码映射为 Modbus 异常语义说明，并输出结构化诊断日志。
 */
@Slf4j
public final class PlcResponseDiagnostics {

    private PlcResponseDiagnostics() {
    }

    /**
     * 常见 Modbus 异常功能码对照（PLC4X 多映射为 {@link PlcResponseCode}，非逐字节透传）。
     */
    public static String modbusHint(PlcResponseCode code) {
        if (code == null) {
            return "unknown";
        }
        return switch (code) {
            case INVALID_ADDRESS -> "可能对应 Modbus 0x02 Illegal Data Address 或 0x03 Illegal Data Value";
            case INVALID_DATA -> "可能对应 Modbus 0x03 Illegal Data Value";
            case INVALID_DATATYPE -> "可能对应 Modbus 0x03 Illegal Data Value（数据类型/长度不匹配）";
            case ACCESS_DENIED -> "可能对应 Modbus 0x03 Illegal Data Value 或设备拒绝访问";
            case NOT_FOUND -> "地址/区不存在或未配置";
            case REMOTE_ERROR -> "Modbus 设备异常响应（REMOTE_ERROR，含非法功能码 0x01 等）";
            case REMOTE_BUSY -> "Modbus 设备忙（REMOTE_BUSY）";
            case INTERNAL_ERROR -> "驱动/协议内部错误（INTERNAL_ERROR）";
            case UNSUPPORTED -> "功能码或数据类型不支持（UNSUPPORTED）";
            case RESPONSE_PENDING -> "响应未完成（RESPONSE_PENDING）";
            case OK -> "OK";
        };
    }

    public static void logNonOkTags(
            String deviceId,
            String protocol,
            PlcReadResponse response,
            Collection<String> tagNames,
            Map<String, String> tagToAddress
    ) {
        if (response == null || tagNames == null || tagNames.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String tag : tagNames) {
            PlcResponseCode code = PlcTagResponseSupport.safeResponseCode(response, tag);
            if (code == null || code == PlcResponseCode.OK) {
                continue;
            }
            String address = tagToAddress != null ? tagToAddress.get(tag) : null;
            parts.add(tag + "{" + code.name() + (StringUtils.hasText(address) ? ",addr=" + address : "")
                    + "," + modbusHint(code) + "}");
        }
        if (parts.isEmpty()) {
            return;
        }
        log.warn(StructuredCollectLog.format(
                "event=plc_tag_response",
                "deviceId", deviceId,
                "protocol", protocol,
                "tags", String.join("; ", parts)
        ));
    }

    public static void logConnectionFailure(
            String deviceId,
            String protocol,
            String connectId,
            int attempt,
            int maxAttempts,
            Throwable error,
            PlcReadResponse lastResponse,
            Collection<String> lastTagNames
    ) {
        String err = error != null && StringUtils.hasText(error.getMessage())
                ? error.getMessage()
                : (error != null ? error.getClass().getSimpleName() : "unknown");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("event", "plc_connection_read_fail");
        fields.put("deviceId", deviceId);
        fields.put("protocol", protocol);
        fields.put("connectId", connectId);
        fields.put("attempt", String.valueOf(attempt));
        fields.put("maxAttempts", String.valueOf(maxAttempts));
        fields.put("error", err);
        if (error != null) {
            fields.put("transportFault", String.valueOf(PlcTagResponseSupport.isTransportOrConnectionFailure(error)));
        }
        if (lastResponse != null && lastTagNames != null && !lastTagNames.isEmpty()) {
            fields.put("lastResponseTags", summarizeResponseCodes(lastResponse, lastTagNames));
        }
        log.warn(StructuredCollectLog.format(fields));
    }

    private static String summarizeResponseCodes(PlcReadResponse response, Collection<String> tagNames) {
        return tagNames.stream()
                .map(tag -> {
                    PlcResponseCode code = PlcTagResponseSupport.safeResponseCode(response, tag);
                    return tag + "=" + (code != null ? code.name() : "null");
                })
                .collect(Collectors.joining(","));
    }

    public static void stashLastReadResponse(PlcReadResponse response) {
        LastReadResponseHolder.set(response);
    }

    public static PlcReadResponse lastReadResponse() {
        return LastReadResponseHolder.get();
    }

    public static void clearLastReadResponse() {
        LastReadResponseHolder.clear();
    }

    private static final class LastReadResponseHolder {
        private static final ThreadLocal<PlcReadResponse> LAST = new ThreadLocal<>();

        private LastReadResponseHolder() {
        }

        static void set(PlcReadResponse response) {
            LAST.set(response);
        }

        static PlcReadResponse get() {
            return LAST.get();
        }

        static void clear() {
            LAST.remove();
        }
    }
}
