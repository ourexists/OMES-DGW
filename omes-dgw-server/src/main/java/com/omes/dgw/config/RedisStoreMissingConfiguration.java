package com.omes.dgw.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 配置了 store-backend=redis 但 classpath 无 Redis 客户端时快速失败，提示引入插件包。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.collect", name = "store-backend", havingValue = "redis")
@ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")
public class RedisStoreMissingConfiguration {

    @PostConstruct
    public void failFast() {
        throw new IllegalStateException(
                "app.collect.store-backend=redis，但未找到 Redis 客户端。"
                        + "请将 omes-dgw-plugin-redis 插件 jar（推荐 *-plugin.jar）加入 classpath 后再启动。"
                        + "详见 omes-dgw-plugin-redis/README.md"
        );
    }
}
