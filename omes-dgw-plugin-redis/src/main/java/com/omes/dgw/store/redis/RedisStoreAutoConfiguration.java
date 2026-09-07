package com.omes.dgw.store.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 存储插件自动配置：仅当 classpath 存在 Redis 且 store-backend=redis 时生效。
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "app.collect", name = "store-backend", havingValue = "redis")
@Import({RedisConfig.class, RedisStoreBackendConfiguration.class, RedisMetricsInfluxWriteLeader.class})
public class RedisStoreAutoConfiguration {
}
