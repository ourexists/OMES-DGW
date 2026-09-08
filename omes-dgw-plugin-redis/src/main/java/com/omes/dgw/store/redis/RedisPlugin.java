package com.omes.dgw.store.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCacheStore;
import com.omes.dgw.metrics.MetricsInfluxWriteLeader;
import com.omes.dgw.metrics.MetricsInstanceIdProvider;
import com.omes.dgw.plugin.api.StorePlugin;
import org.pf4j.PluginWrapper;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.Map;

public final class RedisPlugin extends StorePlugin {
    private LettuceConnectionFactory connection;
    private Map<Class<?>, Object> exports = Map.of();

    public RedisPlugin(PluginWrapper wrapper) { super(wrapper); }

    @Override public void initialize(Host host) {
        Map<String, String> config = host.configuration();
        RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(
                config.getOrDefault("REDIS_HOST", "localhost"),
                Integer.parseInt(config.getOrDefault("REDIS_PORT", "6379")));
        redis.setDatabase(Integer.parseInt(config.getOrDefault("REDIS_DATABASE", "0")));
        String password = config.getOrDefault("REDIS_PASSWORD", "");
        if (!password.isEmpty()) redis.setPassword(password);
        connection = new LettuceConnectionFactory(redis, LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5)).shutdownTimeout(Duration.ofSeconds(2)).build());
        connection.afterPropertiesSet();
        connection.start();
        StringRedisTemplate template = new StringRedisTemplate(connection);
        ObjectMapper mapper = host.service(ObjectMapper.class);
        exports = Map.of(
                DeviceRealtimeCacheStore.class, new RedisDeviceRealtimeCacheStore(template, mapper, host.service(AppProperties.class)),
                MetricsInfluxWriteLeader.class, new RedisMetricsInfluxWriteLeader(host.service(MetricsInstanceIdProvider.class), template));
    }

    @Override public Map<Class<?>, Object> services() { return exports; }
    @Override public void checkHealth() {
        try (var client = connection.getConnection()) {
            if (!"PONG".equalsIgnoreCase(client.ping())) throw new IllegalStateException("Redis PING 失败");
        }
    }
    @Override public void stop() {
        exports = Map.of();
        if (connection != null) { connection.destroy(); connection = null; }
    }
}
