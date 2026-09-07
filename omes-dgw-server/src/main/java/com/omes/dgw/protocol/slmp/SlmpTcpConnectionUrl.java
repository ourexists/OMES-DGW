package com.omes.dgw.protocol.slmp;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SLMP/TCP 连接标识：{@code slmp-tcp://host:port?network-no=n&station-no=n&request-timeout=ms}
 */
public final class SlmpTcpConnectionUrl {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^slmp-tcp://([^:/?#]+):(\\d+)(?:\\?(.*))?$",
            Pattern.CASE_INSENSITIVE);

    private SlmpTcpConnectionUrl() {
    }

    public record Parts(String host, int port, int networkNo, int stationNo, int requestTimeoutMs) {
    }

    public static String build(String host, int port, int networkNo, int stationNo, int requestTimeoutMs) {
        return "slmp-tcp://"
                + normalizeHost(host)
                + ':'
                + port
                + "?network-no="
                + networkNo
                + "&station-no="
                + stationNo
                + "&request-timeout="
                + requestTimeoutMs;
    }

    public static Parts parse(String uriOrHost, int defaultPort, int defaultNetworkNo, int defaultStationNo,
                              int defaultTimeoutMs) {
        if (!StringUtils.hasText(uriOrHost)) {
            return new Parts("", defaultPort, defaultNetworkNo, defaultStationNo, defaultTimeoutMs);
        }
        String input = uriOrHost.trim();
        Matcher matcher = URL_PATTERN.matcher(input);
        if (matcher.matches()) {
            int port = Integer.parseInt(matcher.group(2));
            int networkNo = defaultNetworkNo;
            int stationNo = defaultStationNo;
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
                        case "network-no" -> networkNo = Integer.parseInt(value);
                        case "station-no" -> stationNo = Integer.parseInt(value);
                        case "request-timeout" -> timeoutMs = Integer.parseInt(value);
                        default -> {
                        }
                    }
                }
            }
            return new Parts(matcher.group(1), port, networkNo, stationNo, timeoutMs);
        }
        return new Parts(normalizeHost(input), defaultPort, defaultNetworkNo, defaultStationNo, defaultTimeoutMs);
    }

    public static Parts fromDevice(String ipAddress, Integer port, Integer connectTimeoutMs, Integer networkNo,
                                   Integer stationNo) {
        int resolvedPort = port != null ? port : SlmpConnectParams.DEFAULT_PORT;
        int resolvedTimeout = connectTimeoutMs != null ? connectTimeoutMs : SlmpConnectParams.DEFAULT_TIMEOUT_MS;
        if (resolvedTimeout < SlmpConnectParams.MIN_TIMEOUT_MS) {
            resolvedTimeout = SlmpConnectParams.MIN_TIMEOUT_MS;
        }
        int resolvedNetwork = networkNo != null ? networkNo : SlmpConnectParams.DEFAULT_NETWORK_NO;
        int resolvedStation = stationNo != null ? stationNo : SlmpConnectParams.DEFAULT_STATION_NO;
        return parse(ipAddress, resolvedPort, resolvedNetwork, resolvedStation, resolvedTimeout);
    }

    private static String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String value = host.trim();
        if (value.regionMatches(true, 0, "slmp-tcp://", 0, "slmp-tcp://".length())) {
            return parse(value, SlmpConnectParams.DEFAULT_PORT, SlmpConnectParams.DEFAULT_NETWORK_NO,
                    SlmpConnectParams.DEFAULT_STATION_NO, SlmpConnectParams.DEFAULT_TIMEOUT_MS).host();
        }
        return value;
    }
}
