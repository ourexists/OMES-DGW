package com.omes.dgw.monitoring.service;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCacheMetrics;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.metrics.MetricsInfluxWriteLeader;
import com.omes.dgw.metrics.MetricsInstanceIdProvider;
import com.omes.dgw.metrics.ProtocolCollectMetrics;
import com.omes.dgw.metrics.ProtocolPollSkipMetrics;
import com.omes.dgw.monitoring.dto.MonitoringInfrastructureVo;
import com.omes.dgw.monitoring.dto.MonitoringPipelineVo;
import com.omes.dgw.monitoring.dto.MonitoringPollSkipVo;
import com.omes.dgw.monitoring.dto.MonitoringQueueVo;
import com.omes.dgw.monitoring.dto.MonitoringRealtimeCacheWritesVo;
import com.omes.dgw.monitoring.dto.MonitoringSchedulingVo;
import com.omes.dgw.queue.CollectQueueMetrics;
import com.omes.dgw.queue.CollectQueueNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MonitoringSnapshotService {

    private static final int TOP_SKIP_LIMIT = 10;

    private final DeviceMapper deviceMapper;
    private final MeterRegistry meterRegistry;
    private final AppProperties appProperties;
    private final MetricsInstanceIdProvider instanceIdProvider;
    private final ObjectProvider<MetricsInfluxWriteLeader> influxWriteLeader;

    public MonitoringSnapshotService(
            DeviceMapper deviceMapper,
            MeterRegistry meterRegistry,
            AppProperties appProperties,
            MetricsInstanceIdProvider instanceIdProvider,
            ObjectProvider<MetricsInfluxWriteLeader> influxWriteLeader
    ) {
        this.deviceMapper = deviceMapper;
        this.meterRegistry = meterRegistry;
        this.appProperties = appProperties;
        this.instanceIdProvider = instanceIdProvider;
        this.influxWriteLeader = influxWriteLeader;
    }

    public String instanceId() {
        return instanceIdProvider.instanceId();
    }

    public MonitoringPipelineVo pipelineSnapshot() {
        AppProperties.Metrics metricsCfg = appProperties.getCollect().getMetrics();
        AppProperties.History historyCfg = appProperties.getCollect().getHistory();
        AppProperties.OnlineEvent onlineCfg = appProperties.getCollect().getOnlineEvent();

        MonitoringPipelineVo pipeline = new MonitoringPipelineVo();
        pipeline.setInfluxEnabled("influxdb".equalsIgnoreCase(appProperties.getCollect().getMonitorType()));
        pipeline.setInfluxMetricsPersistEnabled(metricsCfg.isPersistToInflux());
        boolean redisBackend = "redis".equalsIgnoreCase(
                appProperties.getCollect().getStoreBackend());
        // redis 模式启用选主；caffeine 单机始终可写
        pipeline.setInfluxMetricsWriteLeaderEnabled(redisBackend);
        pipeline.setInfluxMetricsWriteLeader(influxWriteLeader.getIfAvailable() == null
                || influxWriteLeader.getObject().isLeader());

        pipeline.getQueues().add(buildQueue(CollectQueueNames.HISTORY, historyCfg.getMaxQueueSize()));
        pipeline.getQueues().add(buildQueue(CollectQueueNames.METRICS, metricsCfg.getInfluxMaxQueueSize()));
        pipeline.getQueues().add(buildQueue(CollectQueueNames.ONLINE_EVENT, onlineCfg.getMaxQueueSize()));
        pipeline.setRealtimeCacheWrites(buildCacheWrites());
        return pipeline;
    }

    public MonitoringInfrastructureVo infrastructureSnapshot() {
        MonitoringInfrastructureVo infra = new MonitoringInfrastructureVo();
        double heapUsed = readGaugeDouble("jvm.memory.used", "area", "heap");
        double heapMax = readGaugeDouble("jvm.memory.max", "area", "heap");
        infra.setJvmHeapUsedRatio(heapMax > 0 ? round3(heapUsed / heapMax) : 0);

        int active = (int) readGaugeDouble("hikaricp.connections.active");
        int max = (int) readGaugeDouble("hikaricp.connections.max");
        infra.setHikariActiveConnections(active);
        infra.setHikariMaxConnections(max);
        infra.setHikariActiveRatio(max > 0 ? round3(active / (double) max) : 0);
        return infra;
    }

    public MonitoringSchedulingVo schedulingSnapshot() {
        List<MonitoringPollSkipVo> skips = new ArrayList<>();
        for (Meter meter : meterRegistry.find(ProtocolPollSkipMetrics.METER_SKIP_STREAK).meters()) {
            long streak = (long) meter.measure().iterator().next().getValue();
            if (streak <= 0) {
                continue;
            }
            String deviceId = meter.getId().getTag(ProtocolCollectMetrics.TAG_DEVICE_ID);
            String protocol = meter.getId().getTag(ProtocolCollectMetrics.TAG_PROTOCOL);
            MonitoringPollSkipVo item = new MonitoringPollSkipVo();
            item.setDeviceId(deviceId);
            item.setProtocol(protocol);
            item.setSkipStreak(streak);
            item.setLastDurationMs(readGauge(
                    ProtocolPollSkipMetrics.METER_LAST_DURATION_MS,
                    ProtocolCollectMetrics.TAG_DEVICE_ID, deviceId,
                    ProtocolCollectMetrics.TAG_PROTOCOL, protocol
            ));
            enrichDeviceInfo(item);
            skips.add(item);
        }
        skips.sort(Comparator
                .comparingLong(MonitoringPollSkipVo::getSkipStreak).reversed()
                .thenComparing(MonitoringPollSkipVo::getDeviceName, Comparator.nullsLast(String::compareTo)));

        MonitoringSchedulingVo scheduling = new MonitoringSchedulingVo();
        scheduling.setDevicesWithSkipStreakGte3(
                (int) skips.stream().filter(item -> item.getSkipStreak() >= 3).count()
        );
        scheduling.setTopSkipStreak(skips.stream().limit(TOP_SKIP_LIMIT).toList());
        return scheduling;
    }

    public MonitoringPollSkipVo devicePollSkipSnapshot(String deviceId, String protocol) {
        MonitoringPollSkipVo item = new MonitoringPollSkipVo();
        item.setDeviceId(deviceId);
        item.setProtocol(protocol);
        if (deviceId == null || deviceId.isBlank()) {
            item.setSkipStreak(0);
            item.setLastDurationMs(0);
            return item;
        }
        String resolvedProtocol = protocol;
        long streak = 0;
        long lastDurationMs = 0;
        if (resolvedProtocol != null && !resolvedProtocol.isBlank()) {
            streak = readGauge(
                    ProtocolPollSkipMetrics.METER_SKIP_STREAK,
                    ProtocolCollectMetrics.TAG_DEVICE_ID, deviceId,
                    ProtocolCollectMetrics.TAG_PROTOCOL, resolvedProtocol
            );
            lastDurationMs = readGauge(
                    ProtocolPollSkipMetrics.METER_LAST_DURATION_MS,
                    ProtocolCollectMetrics.TAG_DEVICE_ID, deviceId,
                    ProtocolCollectMetrics.TAG_PROTOCOL, resolvedProtocol
            );
        } else {
            for (Meter meter : meterRegistry.find(ProtocolPollSkipMetrics.METER_SKIP_STREAK).meters()) {
                if (!deviceId.equals(meter.getId().getTag(ProtocolCollectMetrics.TAG_DEVICE_ID))) {
                    continue;
                }
                resolvedProtocol = meter.getId().getTag(ProtocolCollectMetrics.TAG_PROTOCOL);
                streak = (long) meter.measure().iterator().next().getValue();
                lastDurationMs = readGauge(
                        ProtocolPollSkipMetrics.METER_LAST_DURATION_MS,
                        ProtocolCollectMetrics.TAG_DEVICE_ID, deviceId,
                        ProtocolCollectMetrics.TAG_PROTOCOL, resolvedProtocol
                );
                break;
            }
        }
        item.setProtocol(resolvedProtocol);
        item.setSkipStreak(streak);
        item.setLastDurationMs(lastDurationMs);
        enrichDeviceInfo(item);
        return item;
    }

    private MonitoringQueueVo buildQueue(String name, int maxSize) {
        MonitoringQueueVo queue = new MonitoringQueueVo();
        queue.setName(name);
        queue.setMaxSize(Math.max(1, maxSize));
        queue.setDepth(readGauge(CollectQueueMetrics.METER_DEPTH, CollectQueueMetrics.TAG_QUEUE, name));
        queue.setTrimmedTotal(readCounter(CollectQueueMetrics.METER_TRIMMED, CollectQueueMetrics.TAG_QUEUE, name));
        queue.setUsageRatio(queue.getDepth() / (double) queue.getMaxSize());
        return queue;
    }

    private MonitoringRealtimeCacheWritesVo buildCacheWrites() {
        MonitoringRealtimeCacheWritesVo writes = new MonitoringRealtimeCacheWritesVo();
        writes.setSuccess(readCounter(DeviceRealtimeCacheMetrics.METER_WRITE,
                DeviceRealtimeCacheMetrics.TAG_RESULT, DeviceRealtimeCacheMetrics.RESULT_SUCCESS));
        writes.setDegraded(readCounter(DeviceRealtimeCacheMetrics.METER_WRITE,
                DeviceRealtimeCacheMetrics.TAG_RESULT, DeviceRealtimeCacheMetrics.RESULT_DEGRADED));
        writes.setSerializationError(readCounter(DeviceRealtimeCacheMetrics.METER_WRITE,
                DeviceRealtimeCacheMetrics.TAG_RESULT, DeviceRealtimeCacheMetrics.RESULT_SERIALIZATION));
        writes.setError(readCounter(DeviceRealtimeCacheMetrics.METER_WRITE,
                DeviceRealtimeCacheMetrics.TAG_RESULT, DeviceRealtimeCacheMetrics.RESULT_ERROR));
        return writes;
    }

    private void enrichDeviceInfo(MonitoringPollSkipVo item) {
        Device device = deviceMapper.selectById(item.getDeviceId());
        if (device == null) {
            item.setDeviceName(item.getDeviceId());
            return;
        }
        item.setDeviceCode(device.getCode());
        item.setDeviceName(device.getName());
    }

    private long readGauge(String name, String tagKey, String tagValue) {
        Gauge gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
        return gauge != null ? (long) gauge.value() : 0L;
    }

    private long readGauge(String name, String tagKey1, String tagValue1, String tagKey2, String tagValue2) {
        Gauge gauge = meterRegistry.find(name)
                .tag(tagKey1, tagValue1)
                .tag(tagKey2, tagValue2)
                .gauge();
        return gauge != null ? (long) gauge.value() : 0L;
    }

    private double readGaugeDouble(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        return gauge != null ? gauge.value() : 0;
    }

    private double readGaugeDouble(String name, String tagKey, String tagValue) {
        Gauge gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
        return gauge != null ? gauge.value() : 0;
    }

    private long readCounter(String name, String tagKey, String tagValue) {
        Counter counter = meterRegistry.find(name).tag(tagKey, tagValue).counter();
        return counter != null ? (long) counter.count() : 0L;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
