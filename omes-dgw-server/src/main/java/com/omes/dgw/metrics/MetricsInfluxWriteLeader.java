package com.omes.dgw.metrics;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 单机边缘场景下本实例即为 Influx 指标写入方。
 * Redis 选主由插件 {@code omes-dgw-plugin-redis} 提供（store-backend=redis）。
 */
@Slf4j
public class MetricsInfluxWriteLeader {

    public static final String TAG_INSTANCE_ID = "instance_id";

    private final MetricsInstanceIdProvider instanceIdProvider;

    public MetricsInfluxWriteLeader(MetricsInstanceIdProvider instanceIdProvider) {
        this.instanceIdProvider = instanceIdProvider;
    }

    @PostConstruct
    protected void init() {
        log.info("Influx 指标写入由本实例承担（本地单机）, instanceId={}",
                instanceIdProvider.instanceId());
    }

    public boolean isLeader() {
        return true;
    }

    public String instanceId() {
        return instanceIdProvider.instanceId();
    }
}
