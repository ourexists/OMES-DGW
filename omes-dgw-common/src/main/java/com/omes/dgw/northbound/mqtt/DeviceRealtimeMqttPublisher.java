package com.omes.dgw.northbound.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQTT推送：设备实时快照写入本地缓存后异步推送，每设备一条消息。
 */
@Slf4j
@Service
public class DeviceRealtimeMqttPublisher {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong published = new AtomicLong();

    private volatile MqttClient client;
    private volatile ThreadPoolExecutor executor;

    public DeviceRealtimeMqttPublisher(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 实时缓存写入成功后调用；未启用时立即返回。
     */
    public void onSnapshotSaved(DeviceRealtimeObj snapshot) {
        AppProperties.Mqtt mqtt = mqttConfig();
        if (mqtt == null || !mqtt.isEnabled() || snapshot == null
                || !StringUtils.hasText(snapshot.getDeviceId())) {
            return;
        }
        ensureStarted(mqtt);
        ThreadPoolExecutor pool = this.executor;
        if (pool == null) {
            return;
        }
        try {
            pool.execute(() -> publishSafe(snapshot));
        } catch (Exception e) {
            long n = dropped.incrementAndGet();
            if (n == 1 || n % 100 == 0) {
                log.warn("MQTT推送队列已满或拒绝，已丢弃 count={} deviceId={}", n, snapshot.getDeviceId());
            }
        }
    }

    private void publishSafe(DeviceRealtimeObj snapshot) {
        AppProperties.Mqtt mqtt = mqttConfig();
        if (mqtt == null || !mqtt.isEnabled()) {
            return;
        }
        try {
            ensureConnected(mqtt);
            MqttClient c = this.client;
            if (c == null || !c.isConnected()) {
                return;
            }
            DeviceRealtimeMqttPayload payload = DeviceRealtimeMqttPayload.from(snapshot);
            byte[] body = objectMapper.writeValueAsBytes(payload);
            String topic = resolveTopic(mqtt.getTopic(), snapshot);
            MqttMessage message = new MqttMessage(body);
            message.setQos(clampQos(mqtt.getQos()));
            message.setRetained(mqtt.isRetained());
            c.publish(topic, message);
            long n = published.incrementAndGet();
            if (n == 1 || n % 500 == 0) {
                log.debug("MQTT推送已发送 count={} topic={} deviceId={} bytes={}",
                        n, topic, snapshot.getDeviceId(), body.length);
            }
        } catch (Exception e) {
            log.warn("MQTT推送失败 deviceId={}: {}", snapshot.getDeviceId(), e.getMessage());
        }
    }

    private synchronized void ensureStarted(AppProperties.Mqtt mqtt) {
        if (started.get()) {
            return;
        }
        int capacity = Math.max(64, mqtt.getPublishQueueCapacity());
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "mqtt-publish");
            t.setDaemon(true);
            return t;
        };
        this.executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(capacity),
                tf,
                new ThreadPoolExecutor.AbortPolicy()
        );
        started.set(true);
        log.info("MQTT推送已启用 broker={} topic={}", mqtt.getBrokerUrl(), mqtt.getTopic());
    }

    private synchronized void ensureConnected(AppProperties.Mqtt mqtt) throws MqttException {
        MqttClient existing = this.client;
        if (existing != null && existing.isConnected()) {
            return;
        }
        if (existing != null) {
            try {
                existing.close(true);
            } catch (Exception ignored) {
                // ignore
            }
            this.client = null;
        }
        if (!StringUtils.hasText(mqtt.getBrokerUrl())) {
            throw new IllegalStateException("MQTT推送 brokerUrl 未配置");
        }
        String clientId = resolveClientId(mqtt.getClientId());
        MqttClient newClient = new MqttClient(mqtt.getBrokerUrl().trim(), clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(mqtt.isAutomaticReconnect());
        options.setCleanSession(true);
        options.setConnectionTimeout(Math.max(1, mqtt.getConnectionTimeoutSec()));
        options.setKeepAliveInterval(Math.max(10, mqtt.getKeepAliveIntervalSec()));
        if (StringUtils.hasText(mqtt.getUsername())) {
            options.setUserName(mqtt.getUsername().trim());
        }
        if (StringUtils.hasText(mqtt.getPassword())) {
            options.setPassword(mqtt.getPassword().toCharArray());
        }
        newClient.connect(options);
        this.client = newClient;
        log.info("MQTT推送已连接 broker={} clientId={}", mqtt.getBrokerUrl(), clientId);
    }

    private AppProperties.Mqtt mqttConfig() {
        AppProperties.Northbound nb = appProperties.getNorthbound();
        return nb != null ? nb.getMqtt() : null;
    }

    static String resolveTopic(String template, DeviceRealtimeObj snapshot) {
        String topic = StringUtils.hasText(template) ? template.trim() : "omes/dgw/realtime/{deviceCode}";
        String deviceId = nullToEmpty(snapshot.getDeviceId());
        String deviceCode = StringUtils.hasText(snapshot.getDeviceCode())
                ? snapshot.getDeviceCode() : deviceId;
        String protocol = snapshot.getProtocol() != null ? snapshot.getProtocol().name() : "unknown";
        return topic
                .replace("{deviceId}", sanitizeTopicSegment(deviceId))
                .replace("{deviceCode}", sanitizeTopicSegment(deviceCode))
                .replace("{protocol}", sanitizeTopicSegment(protocol));
    }

    private String resolveClientId(String configured) {
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        String portal = appProperties.getPortal() != null && StringUtils.hasText(appProperties.getPortal().getMode())
                ? appProperties.getPortal().getMode().trim() : "box";
        return "omes-dgw-" + portal + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static int clampQos(int qos) {
        if (qos < 0) {
            return 0;
        }
        if (qos > 2) {
            return 2;
        }
        return qos;
    }

    /** MQTT topic 段不允许 + # 以及空白 */
    static String sanitizeTopicSegment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '+' || c == '#' || c == '/' || Character.isWhitespace(c)) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? "unknown" : sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @PreDestroy
    public synchronized void shutdown() {
        started.set(false);
        ThreadPoolExecutor pool = this.executor;
        this.executor = null;
        if (pool != null) {
            pool.shutdownNow();
        }
        MqttClient c = this.client;
        this.client = null;
        if (c != null) {
            try {
                if (c.isConnected()) {
                    c.disconnect();
                }
                c.close(true);
            } catch (Exception e) {
                log.debug("关闭 MQTT推送客户端: {}", e.getMessage());
            }
        }
        log.info("MQTT推送已停止 published={} dropped={}", published.get(), dropped.get());
    }
}
