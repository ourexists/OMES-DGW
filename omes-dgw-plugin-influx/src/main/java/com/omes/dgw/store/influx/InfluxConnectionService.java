package com.omes.dgw.store.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.HealthCheck;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.timeseries.InfluxRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * InfluxDB 连接封装：健康检查与读写 API 入口；实现 {@link InfluxRuntime} SPI。
 * 由 {@link InfluxStoreAutoConfiguration} {@code @Import} 注册（勿加 {@code @Service}，避免与组件扫描重复）。
 */
@Slf4j
@ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "influxdb")
@ConditionalOnBean(InfluxDBClient.class)
public class InfluxConnectionService implements InfluxRuntime {

    private final InfluxDBClient client;
    private final WriteApiBlocking writeApi;
    private final AppProperties.Influx influx;

    public InfluxConnectionService(
            InfluxDBClient client,
            WriteApiBlocking writeApi,
            AppProperties appProperties
    ) {
        this.client = client;
        this.writeApi = writeApi;
        this.influx = appProperties.getInflux();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean ping() {
        try {
            HealthCheck health = client.health();
            return health != null && HealthCheck.StatusEnum.PASS.equals(health.getStatus());
        } catch (Exception e) {
            log.warn("InfluxDB ping failed: {}", e.getMessage());
            return false;
        }
    }

    public WriteApiBlocking writeApi() {
        return writeApi;
    }

    public QueryApi queryApi() {
        return client.getQueryApi();
    }

    @Override
    public String org() {
        return influx.getOrg();
    }

    @Override
    public String bucket() {
        return influx.getBucket();
    }
}
