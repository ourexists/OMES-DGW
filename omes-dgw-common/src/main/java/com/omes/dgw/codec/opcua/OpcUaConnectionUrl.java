package com.omes.dgw.codec.opcua;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * PLC4X OPC UA 连接串，参数与 omes {@code OPCUaPollingProtocolManager} 一致。
 */
public final class OpcUaConnectionUrl {

    public static final int DEFAULT_PORT = 4840;
    public static final String POLICY_NONE = "NONE";

    private static final Set<String> POLICIES = Set.of(
            "NONE",
            "Basic128Rsa15",
            "Basic256",
            "Basic256Sha256",
            "Aes128_Sha256_RsaOaep",
            "Aes256_Sha256_RsaPss");

    private OpcUaConnectionUrl() {
    }

    public record Parts(
            String host,
            int port,
            boolean discovery,
            String username,
            String password,
            String securityPolicy,
            int timeoutMs
    ) {
    }

    public static String normalizePolicy(String policy) {
        if (!StringUtils.hasText(policy)) {
            return POLICY_NONE;
        }
        String trimmed = policy.trim();
        if ("none".equalsIgnoreCase(trimmed)) {
            return POLICY_NONE;
        }
        for (String allowed : POLICIES) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return allowed;
            }
        }
        return POLICY_NONE;
    }

    public static void checkCredential(String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (value.indexOf('&') >= 0 || value.indexOf('=') >= 0
                || value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            throw new IllegalArgumentException(label + "不能包含 &、=、?、#");
        }
    }

    public static Parts parse(ObjectMapper objectMapper, String uri, String paramsJson) {
        int port = DEFAULT_PORT;
        boolean discovery = true;
        String username = null;
        String password = null;
        String securityPolicy = POLICY_NONE;
        int timeoutMs = 10_000;
        if (StringUtils.hasText(paramsJson) && objectMapper != null) {
            try {
                JsonNode node = objectMapper.readTree(paramsJson);
                if (node.has("port")) {
                    port = node.get("port").asInt(port);
                }
                if (node.has("discovery")) {
                    discovery = node.get("discovery").asBoolean(true);
                }
                if (node.hasNonNull("username")) {
                    username = blankToNull(node.get("username").asText());
                }
                if (node.hasNonNull("password")) {
                    password = blankToNull(node.get("password").asText());
                }
                if (node.hasNonNull("securityPolicy")) {
                    securityPolicy = normalizePolicy(node.get("securityPolicy").asText());
                }
                if (node.has("timeout")) {
                    timeoutMs = node.get("timeout").asInt(timeoutMs);
                }
            } catch (Exception ignored) {
                securityPolicy = POLICY_NONE;
            }
        }
        String host = hostOf(uri);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(host.substring(colon + 1).trim());
                host = host.substring(0, colon);
            } catch (NumberFormatException ignored) {
                // 端口以 params 为准
            }
        }
        if (port <= 0) {
            port = DEFAULT_PORT;
        }
        if (timeoutMs <= 0) {
            timeoutMs = 10_000;
        }
        return new Parts(host.trim(), port, discovery, username, password, securityPolicy, timeoutMs);
    }

    public static String build(Parts parts) {
        checkCredential("用户名", parts.username());
        checkCredential("密码", parts.password());
        StringBuilder url = new StringBuilder("opcua:tcp://")
                .append(parts.host())
                .append(':')
                .append(parts.port())
                .append("?discovery=")
                .append(parts.discovery())
                .append("&security-policy=")
                .append(normalizePolicy(parts.securityPolicy()))
                .append("&request-timeout=")
                .append(Math.max(1, parts.timeoutMs()));
        if (StringUtils.hasText(parts.username())) {
            url.append("&username=").append(parts.username().trim());
        }
        if (StringUtils.hasText(parts.password())) {
            url.append("&password=").append(parts.password());
        }
        return url.toString();
    }

    public static String buildMasked(Parts parts) {
        if (!StringUtils.hasText(parts.password())) {
            return build(parts);
        }
        return build(new Parts(
                parts.host(), parts.port(), parts.discovery(),
                parts.username(), "***", parts.securityPolicy(), parts.timeoutMs()));
    }

    public static String jsonQuote(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String hostOf(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "";
        }
        String value = uri.trim().replaceAll("/$", "");
        return value.replaceFirst("^[a-zA-Z0-9+.]+://", "");
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
