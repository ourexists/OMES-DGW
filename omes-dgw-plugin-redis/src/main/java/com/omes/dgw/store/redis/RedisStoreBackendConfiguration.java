package com.omes.dgw.store.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCacheStore;
import com.omes.dgw.queue.BoundedListQueueOps;
import com.omes.dgw.queue.CollectQueueMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 实时缓存与有界队列后端装配。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.collect", name = "store-backend", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisStoreBackendConfiguration {

    @Bean
    public DeviceRealtimeCacheStore deviceRealtimeCacheStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AppProperties appProperties
    ) {
        log.info("采集存储后端：Redis（实时缓存 + 队列）");
        return new RedisDeviceRealtimeCacheStore(stringRedisTemplate, objectMapper, appProperties);
    }

    @Bean
    public BoundedListQueueOps boundedListQueueOps(
            StringRedisTemplate stringRedisTemplate,
            CollectQueueMetrics queueMetrics
    ) {
        return new BoundedRedisListQueueOps(stringRedisTemplate, queueMetrics);
    }
}
