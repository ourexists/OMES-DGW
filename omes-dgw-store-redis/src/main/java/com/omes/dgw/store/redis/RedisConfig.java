package com.omes.dgw.store.redis;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 仅在 store-backend=redis 时启用 Spring Boot Redis 自动配置（StringRedisTemplate 等）。
 * 主应用默认排除 RedisAutoConfiguration，避免 caffeine 模式误连 Redis。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.collect", name = "store-backend", havingValue = "redis")
@ConditionalOnClass(StringRedisTemplate.class)
@ImportAutoConfiguration(RedisAutoConfiguration.class)
public class RedisConfig {
}
