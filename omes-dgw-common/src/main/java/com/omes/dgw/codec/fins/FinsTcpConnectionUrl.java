package com.omes.dgw.codec.fins;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FINS/TCP 连接标识：{@code fins-tcp://host:port?local-node=n&remote-node=n&request-timeout=ms}
 */
public final class FinsTcpConnectionUrl {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^fins-tcp://([^:/?#]+):(\\d+)(?:\\?(.*))?$",
            Pattern.CASE_INSENSITIVE);

    private FinsTcpConnectionUrl() {
    }

    public record Parts(String host, int port, int localNode, int remoteNode, int requestTimeoutMs) {
    }

    public static String build(String host, int port, int localNode, int remoteNode, int requestTimeoutMs) {
        return "fins-tcp://"
                + normalizeHost(host)
                + ':'
                + port
                + "?local-node="
                + localNode
                + "&remote-node="
                + remoteNode
                + "&request-timeout="
                + requestTimeoutMs;
    }

    public static Parts parse(String uriOrHost, int defaultPort, int defaultLocalNode, int defaultRemoteNode,
                              int defaultTimeoutMs) {
        if (!StringUtils.hasText(uriOrHost)) {
            return new Parts("", defaultPort, defaultLocalNode, defaultRemoteNode, defaultTimeoutMs);
        }
        String input = uriOrHost.trim();
        Matcher matcher = URL_PATTERN.matcher(input);
        if (matcher.matches()) {
            int port = Integer.parseInt(matcher.group(2));
            int localNode = defaultLocalNode;
            int remoteNode = defaultRemoteNode;
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
                        case "local-node" -> localNode = Integer.parseInt(value);
                        case "remote-node" -> remoteNode = Integer.parseInt(value);
                        case "request-timeout" -> timeoutMs = Integer.parseInt(value);
                        default -> {
                        }
                    }
                }
            }
            return new Parts(matcher.group(1), port, localNode, remoteNode, timeoutMs);
        }
        return new Parts(normalizeHost(input), defaultPort, defaultLocalNode, defaultRemoteNode, defaultTimeoutMs);
    }

    public static Parts fromDevice(String ipAddress, Integer port, Integer connectTimeoutMs, Integer localNode,
                                   Integer remoteNode) {
        int resolvedPort = port != null ? port : FinsConnectParams.DEFAULT_PORT;
        int resolvedTimeout = connectTimeoutMs != null ? connectTimeoutMs : FinsConnectParams.DEFAULT_TIMEOUT_MS;
        if (resolvedTimeout < FinsConnectParams.MIN_TIMEOUT_MS) {
            resolvedTimeout = FinsConnectParams.MIN_TIMEOUT_MS;
        }
        int resolvedLocal = localNode != null ? localNode : FinsConnectParams.DEFAULT_LOCAL_NODE;
        // 未配置 finsRemoteNode 时用 0，握手后由 PLC 响应填入实际 DA1（勿用 IP 末段猜测）
        int resolvedRemote = remoteNode != null ? remoteNode : 0;
        return parse(ipAddress, resolvedPort, resolvedLocal, resolvedRemote, resolvedTimeout);
    }

    private static String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String value = host.trim();
        if (value.regionMatches(true, 0, "fins-tcp://", 0, "fins-tcp://".length())) {
            return parse(value, FinsConnectParams.DEFAULT_PORT, FinsConnectParams.DEFAULT_LOCAL_NODE, 0,
                    FinsConnectParams.DEFAULT_TIMEOUT_MS).host();
        }
        return value;
    }
}
