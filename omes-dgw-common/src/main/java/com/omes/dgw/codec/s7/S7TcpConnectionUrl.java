package com.omes.dgw.codec.s7;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S7 连接标识：{@code s7://host:port?rack=0&slot=1&request-timeout=ms}
 */
public final class S7TcpConnectionUrl {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^s7://([^:/?#]+):(\\d+)(?:\\?(.*))?$",
            Pattern.CASE_INSENSITIVE);

    private S7TcpConnectionUrl() {
    }

    public record Parts(String host, int port, int rack, int slot, int requestTimeoutMs) {
    }

    public static String build(Parts parts) {
        return "s7://"
                + parts.host()
                + ':'
                + parts.port()
                + "?rack="
                + parts.rack()
                + "&slot="
                + parts.slot()
                + "&request-timeout="
                + parts.requestTimeoutMs();
    }

    public static Parts fromDevice(String ipAddress, Integer port, Integer connectTimeoutMs, Integer rack, Integer slot) {
        int resolvedPort = port != null && port > 0 ? port : S7ConnectParams.DEFAULT_PORT;
        int resolvedTimeout = connectTimeoutMs != null && connectTimeoutMs > 0
                ? connectTimeoutMs
                : S7ConnectParams.DEFAULT_TIMEOUT_MS;
        int resolvedRack = rack != null ? rack : S7ConnectParams.DEFAULT_RACK;
        int resolvedSlot = slot != null ? slot : S7ConnectParams.DEFAULT_SLOT;
        return parse(ipAddress, resolvedPort, resolvedRack, resolvedSlot, resolvedTimeout);
    }

    public static Parts parse(String uriOrHost, int defaultPort, int defaultRack, int defaultSlot, int defaultTimeoutMs) {
        if (!StringUtils.hasText(uriOrHost)) {
            return new Parts("", defaultPort, defaultRack, defaultSlot, defaultTimeoutMs);
        }
        String input = uriOrHost.trim();
        Matcher matcher = URL_PATTERN.matcher(input);
        if (matcher.matches()) {
            int port = Integer.parseInt(matcher.group(2));
            int rack = defaultRack;
            int slot = defaultSlot;
            int timeoutMs = defaultTimeoutMs;
            String query = matcher.group(3);
            if (StringUtils.hasText(query)) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length != 2) {
                        continue;
                    }
                    switch (kv[0].trim()) {
                        case "rack" -> rack = Integer.parseInt(kv[1].trim());
                        case "slot" -> slot = Integer.parseInt(kv[1].trim());
                        case "request-timeout", "timeout" -> timeoutMs = Integer.parseInt(kv[1].trim());
                        default -> {
                        }
                    }
                }
            }
            return new Parts(matcher.group(1), port, rack, slot, timeoutMs);
        }
        String host = input;
        int port = defaultPort;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(host.substring(colon + 1).trim());
                host = host.substring(0, colon);
            } catch (NumberFormatException ignored) {
                port = defaultPort;
            }
        }
        return new Parts(host.trim(), port, defaultRack, defaultSlot, defaultTimeoutMs);
    }
}
