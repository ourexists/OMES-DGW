package com.omes.dgw.protocol.opcua;

import com.omes.dgw.codec.opcua.OpcUaConnectionUrl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.protocol.AbstractPlc4xPollingProtocolManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpcUaConnectionUrlTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsAnonymousUrlFromOmesParams() {
        OpcUaConnectionUrl.Parts parts = OpcUaConnectionUrl.parse(objectMapper, "10.0.0.8",
                "{\"port\":4840,\"discovery\":true,\"securityPolicy\":\"None\",\"timeout\":10000}");
        String url = OpcUaConnectionUrl.build(parts);
        assertEquals(
                "opcua:tcp://10.0.0.8:4840?discovery=true&security-policy=NONE&request-timeout=10000",
                url);
        assertFalse(url.contains("password="));
    }

    @Test
    void masksPasswordAndRejectsQueryBreakers() {
        OpcUaConnectionUrl.Parts parts = OpcUaConnectionUrl.parse(objectMapper, "opc.tcp://10.0.0.8:4841",
                "{\"username\":\"op\",\"password\":\"secret\",\"securityPolicy\":\"basic256sha256\"}");
        assertEquals(4841, parts.port());
        assertEquals("Basic256Sha256", parts.securityPolicy());
        assertTrue(OpcUaConnectionUrl.build(parts).contains("password=secret"));
        assertTrue(OpcUaConnectionUrl.buildMasked(parts).contains("password=***"));
        assertFalse(OpcUaConnectionUrl.buildMasked(parts).contains("secret"));
        assertEquals(
                "opcua:tcp://h:1?discovery=true&password=***",
                AbstractPlc4xPollingProtocolManager.redactConnectionUrl(
                        "opcua:tcp://h:1?discovery=true&password=secret"));
        assertThrows(IllegalArgumentException.class,
                () -> OpcUaConnectionUrl.checkCredential("密码", "a&b"));
    }
}
