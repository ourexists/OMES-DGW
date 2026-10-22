package com.omes.dgw.store.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.omes.dgw.metrics.MetricsInfluxWriteLeader;
import com.omes.dgw.metrics.MetricsInstanceIdProvider;
import com.omes.dgw.metrics.ProtocolMetricsHistoryStore;
import com.omes.dgw.metrics.ProtocolMetricsHistoryWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@ConditionalOnProperty(prefix = "app.collect.metrics", name = "persist-to-influx", havingValue = "true")
@ConditionalOnBean(InfluxDBClient.class)
public class ProtocolMetricsInfluxBatchWriter implements ProtocolMetricsHistoryWriter {

    private final InfluxConnectionService influx;
    private final WritePrecision writePrecision;
    private final MetricsInstanceIdProvider instanceIdProvider;

    public ProtocolMetricsInfluxBatchWriter(
            InfluxConnectionService influx,
            WritePrecision writePrecision,
            MetricsInstanceIdProvider instanceIdProvider
    ) {
        this.influx = influx;
        this.writePrecision = writePrecision;
        this.instanceIdProvider = instanceIdProvider;
    }

    @Override
    public void writeMetrics(List<ProtocolMetricsHistoryStore.MetricsPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<Point> influxPoints = new ArrayList<>(points.size());
        for (ProtocolMetricsHistoryStore.MetricsPoint point : points) {
            Point influxPoint = toPoint(point);
            if (influxPoint != null) {
                influxPoints.add(influxPoint);
            }
        }
        if (influxPoints.isEmpty()) {
            return;
        }
        try {
            influx.writeApi().writePoints(influxPoints);
            log.info("Influx 监控指标批量写入完成 metrics={}, influxPoints={}", points.size(), influxPoints.size());
        } catch (Exception e) {
            log.error("Influx 监控指标批量写入失败 metrics={}: {}", points.size(), e.getMessage(), e);
            throw e;
        }
    }

    private Point toPoint(ProtocolMetricsHistoryStore.MetricsPoint point) {
        if (point == null || point.time() == null) {
            return null;
        }
        boolean summary = ProtocolMetricsHistoryStore.isSummary(point);
        String protocol = summary
                ? ProtocolMetricsHistoryStore.SUMMARY_PROTOCOL
                : point.protocol();
        String deviceId = summary
                ? ProtocolMetricsHistoryStore.SUMMARY_DEVICE_ID
                : point.deviceId();
        if (!summary && !StringUtils.hasText(protocol)) {
            return null;
        }
        if (!summary && !StringUtils.hasText(deviceId)) {
            return null;
        }

        return Point.measurement(MEASUREMENT)
                .addTag(MetricsInfluxWriteLeader.TAG_INSTANCE_ID, instanceIdProvider.instanceId())
                .addTag("protocol", protocol)
                .addTag("device_id", deviceId)
                .addTag("scope", summary ? "summary" : "device")
                .addField("read_qps", point.readQps())
                .addField("poll_qps", point.pollQps())
                .addField("write_qps", point.writeQps())
                .addField("read_fail_rate", point.readFailRate())
                .addField("poll_fail_rate", point.pollFailRate())
                .addField("write_fail_rate", point.writeFailRate())
                .addField("read_latency_ms", point.readLatencyMs())
                .addField("poll_latency_ms", point.pollLatencyMs())
                .addField("write_latency_ms", point.writeLatencyMs())
                .addField("poll_skip_qps", point.pollSkipQps())
                .addField("poll_skip_streak", point.pollSkipStreak())
                .addField("poll_last_duration_ms", point.pollLastDurationMs())
                .addField("offline_duration_sec", point.offlineDurationSec())
                .time(point.time(), writePrecision);
    }
}
