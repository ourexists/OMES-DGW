package com.omes.dgw.store.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.timeseries.*;
import com.omes.dgw.metrics.*;
import com.omes.dgw.plugin.api.StorePlugin;
import org.pf4j.PluginWrapper;

import java.util.Map;

public final class InfluxPlugin extends StorePlugin {
    private InfluxDBClient client;
    private InfluxConnectionService runtime;
    private Map<Class<?>, Object> exports = Map.of();

    public InfluxPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void initialize(Host host) {
        AppProperties properties = host.service(AppProperties.class);
        client = new InfluxConfig().influxDBClient(properties);
        runtime = new InfluxConnectionService(client, client.getWriteApiBlocking(), properties);
        exports = Map.of(
                InfluxRuntime.class, runtime,
                DeviceRealtimeHistoryWriter.class, new DeviceRealtimeInfluxBatchWriter(runtime, WritePrecision.MS),
                DeviceSnapshotHistoryQuery.class, new DeviceSnapshotInfluxQueryService(runtime),
                ProtocolMetricsHistoryWriter.class, new ProtocolMetricsInfluxBatchWriter(runtime, WritePrecision.MS, host.service(MetricsInstanceIdProvider.class)),
                ProtocolMetricsHistoryQuery.class, new ProtocolMetricsInfluxQueryService(runtime, properties));
    }

    @Override
    public Map<Class<?>, Object> services() {
        return exports;
    }

    @Override
    public void checkHealth() {
        if (!runtime.ping()) throw new IllegalStateException("InfluxDB 健康检查失败");
        // Verify token and bucket access before replacing the active instance.
        if (client.getBucketsApi().findBucketByName(runtime.bucket()) == null)
            throw new IllegalStateException("InfluxDB bucket 不存在或 token 无访问权限");
    }

    @Override
    public void stop() {
        exports = Map.of();
        runtime = null;
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
