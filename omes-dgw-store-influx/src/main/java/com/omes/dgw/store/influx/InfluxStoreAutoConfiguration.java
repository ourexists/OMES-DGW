package com.omes.dgw.store.influx;

import com.influxdb.client.InfluxDBClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Influx 时序插件自动配置：仅当 classpath 存在 Influx 客户端且 app.collect.monitor-type=influxdb 时生效。
 */
@AutoConfiguration
@ConditionalOnClass(InfluxDBClient.class)
@ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "influxdb")
@Import({
        InfluxConfig.class,
        InfluxConnectionService.class,
        DeviceRealtimeInfluxBatchWriter.class,
        DeviceSnapshotInfluxQueryService.class,
        ProtocolMetricsInfluxBatchWriter.class,
        ProtocolMetricsInfluxQueryService.class,
        InfluxCollectHistoryHealthChecker.class
})
public class InfluxStoreAutoConfiguration {
}
