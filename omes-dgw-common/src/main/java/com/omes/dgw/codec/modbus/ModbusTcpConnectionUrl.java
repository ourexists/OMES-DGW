package com.omes.dgw.codec.modbus;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PLC4X Modbus TCP 连接串：{@code modbus-tcp://host:port?request-timeout=ms&default-unit-identifier=n}
 */
public final class ModbusTcpConnectionUrl {

    private static final int DEFAULT_PORT = 502;
    private static final int DEFAULT_UNIT_ID = 1;
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 10_000;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^modbus-tcp://([^:/?#]+):(\\d+)(?:\\?(.*))?$",
            Pattern.CASE_INSENSITIVE);

    private ModbusTcpConnectionUrl() {
    }

    public record Parts(String host, int port, int unitId, int requestTimeoutMs) {
    }

    public static String build(String host, int port, int unitId, int requestTimeoutMs) {
        return build(host, port, unitId, requestTimeoutMs, null);
    }

    public static String build(
            String host,
            int port,
            int unitId,
            int requestTimeoutMs,
            ModbusByteOrder payloadByteOrder
    ) {
        String normalizedHost = normalizeHost(host);
        StringBuilder url = new StringBuilder("modbus-tcp://")
                .append(normalizedHost)
                .append(':')
                .append(port)
                .append("?request-timeout=")
                .append(requestTimeoutMs);
        if (unitId != DEFAULT_UNIT_ID) {
            url.append("&default-unit-identifier=").append(unitId);
        }
        if (payloadByteOrder != null) {
            String plc4xOrder = payloadByteOrder.toPlc4xPayloadParam();
            if (StringUtils.hasText(plc4xOrder)) {
                url.append("&default-payload-byte-order=").append(plc4xOrder);
            }
        }
        return url.toString();
    }

    /**
     * 解析完整连接串，或仅 host（配合默认 port / 超时）。
     */
    public static Parts parse(String uriOrHost, int defaultPort, int defaultUnitId, int defaultTimeoutMs) {
        if (!StringUtils.hasText(uriOrHost)) {
            return new Parts("", defaultPort, defaultUnitId, defaultTimeoutMs);
        }
        String input = uriOrHost.trim();
        Matcher matcher = URL_PATTERN.matcher(input);
        if (matcher.matches()) {
            int port = Integer.parseInt(matcher.group(2));
            int unitId = defaultUnitId;
            int timeoutMs = defaultTimeoutMs;
            String query = matcher.group(3);
            if (StringUtils.hasText(query)) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length != 2) {
                        continue;
                    }
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    switch (key) {
                        case "request-timeout" -> timeoutMs = Integer.parseInt(value);
                        case "default-unit-identifier", "unit-identifier" -> unitId = Integer.parseInt(value);
                        default -> {
                        }
                    }
                }
            }
            return new Parts(matcher.group(1), port, unitId, timeoutMs);
        }
        return new Parts(normalizeHost(input), defaultPort, defaultUnitId, defaultTimeoutMs);
    }

    public static Parts fromDevice(String ipAddress, Integer port, Integer connectTimeoutMs) {
        int resolvedPort = port != null ? port : DEFAULT_PORT;
        int resolvedTimeout = connectTimeoutMs != null ? connectTimeoutMs : DEFAULT_REQUEST_TIMEOUT_MS;
        return parse(ipAddress, resolvedPort, DEFAULT_UNIT_ID, resolvedTimeout);
    }

    private static String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String value = host.trim();
        if (value.regionMatches(true, 0, "modbus-tcp://", 0, "modbus-tcp://".length())) {
            return parse(value, DEFAULT_PORT, DEFAULT_UNIT_ID, DEFAULT_REQUEST_TIMEOUT_MS).host();
        }
        return value;
    }
}
