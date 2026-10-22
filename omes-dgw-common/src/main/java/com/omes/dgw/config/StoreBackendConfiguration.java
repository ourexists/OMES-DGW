package com.omes.dgw.config;

import com.omes.dgw.metrics.MetricsInfluxWriteLeader;
import com.omes.dgw.metrics.MetricsInstanceIdProvider;
import com.omes.dgw.metrics.ProtocolMetricsHistoryQuery;
import com.omes.dgw.metrics.ProtocolMetricsHistoryStore;
import com.omes.dgw.metrics.ProtocolMetricsHistoryWriter;
import com.omes.dgw.queue.BoundedListQueueOps;
import com.omes.dgw.queue.BoundedLocalListQueueOps;
import com.omes.dgw.queue.CollectQueueMetrics;
import com.omes.dgw.store.noop.NoopDeviceRealtimeHistoryWriter;
import com.omes.dgw.store.noop.NoopInfluxRuntime;
import com.omes.dgw.store.noop.NoopProtocolMetricsHistoryWriter;
import com.omes.dgw.timeseries.DeviceRealtimeHistoryWriter;
import com.omes.dgw.timeseries.DeviceSnapshotHistoryQuery;
import com.omes.dgw.timeseries.InfluxRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * 存储 SPI 兜底装配：各 store 模块通过 AutoConfiguration 提供真实 {@code @Bean}；
 * 此处仅在 caffeine / monitor-type=none 时注册 Noop 与本地队列。
 */
@Configuration
public class StoreBackendConfiguration {

    @Bean
    @ConditionalOnMissingBean(BoundedListQueueOps.class)
    @ConditionalOnProperty(prefix = "app.collect", name = "store-backend", havingValue = "caffeine", matchIfMissing = true)
    public BoundedListQueueOps boundedListQueueOps(CollectQueueMetrics metrics) {
        return new BoundedLocalListQueueOps(metrics);
    }

    @Bean
    @ConditionalOnMissingBean(InfluxRuntime.class)
    @ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "none", matchIfMissing = true)
    public InfluxRuntime influxRuntime() {
        NoopInfluxRuntime noop = NoopInfluxRuntime.INSTANCE;
        return new InfluxRuntime() {
            @Override
            public boolean available() {
                return noop.available();
            }

            @Override
            public boolean ping() {
                return noop.ping();
            }

            @Override
            public String org() {
                return noop.org();
            }

            @Override
            public String bucket() {
                return noop.bucket();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(DeviceRealtimeHistoryWriter.class)
    @ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "none", matchIfMissing = true)
    public DeviceRealtimeHistoryWriter historyWriter() {
        return snapshots -> NoopDeviceRealtimeHistoryWriter.INSTANCE.writeSnapshots(snapshots);
    }

    @Bean
    @ConditionalOnMissingBean(DeviceSnapshotHistoryQuery.class)
    @ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "none", matchIfMissing = true)
    public DeviceSnapshotHistoryQuery historyQuery() {
        return (deviceId, pointNames, start, end, fluxEveryOrNull) -> Collections.emptyList();
    }

    @Bean
    @ConditionalOnMissingBean(ProtocolMetricsHistoryWriter.class)
    @ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "none", matchIfMissing = true)
    public ProtocolMetricsHistoryWriter metricsWriter() {
        return points -> NoopProtocolMetricsHistoryWriter.INSTANCE.writeMetrics(points);
    }

    @Bean
    @ConditionalOnMissingBean(ProtocolMetricsHistoryQuery.class)
    @ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "none", matchIfMissing = true)
    public ProtocolMetricsHistoryQuery metricsQuery() {
        return new ProtocolMetricsHistoryQuery() {
            @Override
            public List<ProtocolMetricsHistoryStore.MetricsPoint> queryDeviceSeries(String deviceId, int minutes) {
                return Collections.emptyList();
            }

            @Override
            public List<ProtocolMetricsHistoryStore.MetricsPoint> querySummarySeries(int minutes) {
                return Collections.emptyList();
            }

            @Override
            public List<ProtocolMetricsHistoryStore.MetricsPoint> queryLatestPerDevice(int minutes) {
                return Collections.emptyList();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(MetricsInfluxWriteLeader.class)
    @ConditionalOnProperty(prefix = "app.collect", name = "store-backend", havingValue = "caffeine", matchIfMissing = true)
    public MetricsInfluxWriteLeader metricsLeader(MetricsInstanceIdProvider provider) {
        return new MetricsInfluxWriteLeader(provider);
    }
}
