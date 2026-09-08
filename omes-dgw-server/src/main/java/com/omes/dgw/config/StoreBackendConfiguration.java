package com.omes.dgw.config;
import com.omes.dgw.device.cache.DeviceRealtimeCacheStore;
import com.omes.dgw.timeseries.*;
import com.omes.dgw.metrics.*;
import com.omes.dgw.plugin.StorePluginRuntime;
import com.omes.dgw.queue.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class StoreBackendConfiguration {
    @Bean public DeviceRealtimeCacheStore deviceRealtimeCacheStore(StorePluginRuntime runtime) { return runtime.proxy("redis", DeviceRealtimeCacheStore.class); }
    @Bean public BoundedListQueueOps boundedListQueueOps(CollectQueueMetrics metrics) { return new BoundedLocalListQueueOps(metrics); }
    @Bean public InfluxRuntime influxRuntime(StorePluginRuntime runtime) { return runtime.proxy("influx", InfluxRuntime.class); }
    @Bean public DeviceRealtimeHistoryWriter historyWriter(StorePluginRuntime runtime) { return runtime.proxy("influx", DeviceRealtimeHistoryWriter.class); }
    @Bean public DeviceSnapshotHistoryQuery historyQuery(StorePluginRuntime runtime) { return runtime.proxy("influx", DeviceSnapshotHistoryQuery.class); }
    @Bean public ProtocolMetricsHistoryWriter metricsWriter(StorePluginRuntime runtime) { return runtime.proxy("influx", ProtocolMetricsHistoryWriter.class); }
    @Bean public ProtocolMetricsHistoryQuery metricsQuery(StorePluginRuntime runtime) { return runtime.proxy("influx", ProtocolMetricsHistoryQuery.class); }
    @Bean public MetricsInfluxWriteLeader metricsLeader(StorePluginRuntime runtime, MetricsInstanceIdProvider provider) {
        return new MetricsInfluxWriteLeader(provider) { @Override public boolean isLeader() { return runtime.isMetricsLeader(); } };
    }
}
