package com.omes.dgw.store.redis;

import com.omes.dgw.metrics.MetricsInfluxWriteLeader;
import com.omes.dgw.metrics.MetricsInstanceIdProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis SET NX 的 Influx 指标写入选主（store-backend=redis 时启用）。
 */
@Slf4j
@ConditionalOnProperty(prefix = "app.collect", name = "store-backend", havingValue = "redis")
@ConditionalOnExpression("'${app.collect.metrics.persist-to-influx:false}' == 'true'")
public class RedisMetricsInfluxWriteLeader extends MetricsInfluxWriteLeader {

    private static final String LOCK_KEY = "omes:dgw:metrics:influx-write-leader";
    private static final Duration LOCK_TTL = Duration.ofSeconds(45);

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    public RedisMetricsInfluxWriteLeader(
            MetricsInstanceIdProvider instanceIdProvider,
            StringRedisTemplate redisTemplate
    ) {
        super(instanceIdProvider);
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceIdProvider.instanceId();
    }

    @PostConstruct
    @Override
    protected void init() {
        log.info("Influx 指标写入启用 Redis 选主, instanceId={}, lockKey={}", instanceId, LOCK_KEY);
    }

    @Override
    public boolean isLeader() {
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, instanceId, LOCK_TTL);
            if (Boolean.TRUE.equals(ok)) {
                return true;
            }
            String current = redisTemplate.opsForValue().get(LOCK_KEY);
            if (instanceId.equals(current)) {
                redisTemplate.expire(LOCK_KEY, LOCK_TTL.toSeconds(), TimeUnit.SECONDS);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Redis 选主失败，本实例暂不写 Influx: {}", e.getMessage());
            return false;
        }
    }
}
