package com.omes.dgw.store.influx;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.metrics.ProtocolMetricsHistoryQuery;
import com.omes.dgw.metrics.ProtocolMetricsHistoryStore;
import com.omes.dgw.metrics.ProtocolMetricsHistoryWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 使用 Flux 从 InfluxDB 查询协议监控指标历史。
 */
@Slf4j
@ConditionalOnProperty(prefix = "app.collect.metrics", name = "persist-to-influx", havingValue = "true")
@ConditionalOnBean(name = "influxDBClient")
public class ProtocolMetricsInfluxQueryService implements ProtocolMetricsHistoryQuery {

    private final QueryApi queryApi;
    private final String influxOrg;
    private final String bucket;
    private final int aggregateEverySec;

    public ProtocolMetricsInfluxQueryService(InfluxConnectionService influx, AppProperties appProperties) {
        this.queryApi = influx.queryApi();
        this.influxOrg = influx.org();
        this.bucket = influx.bucket();
        this.aggregateEverySec = Math.max(1, appProperties.getCollect().getMetrics().getSampleIntervalSec());
    }

    @Override
    public List<ProtocolMetricsHistoryStore.MetricsPoint> queryDeviceSeries(String deviceId, int minutes) {
        String flux = """
                from(bucket: %s)
                  |> range(start: %s)
                  |> filter(fn: (r) => r._measurement == %s)
                  |> filter(fn: (r) => r.scope == "device")
                  |> filter(fn: (r) => r.device_id == %s)
                  %s
                  |> sort(columns: ["_time"])
                """.formatted(
                fluxString(bucket),
                fluxDuration(minutes),
                fluxString(ProtocolMetricsHistoryWriter.MEASUREMENT),
                fluxString(deviceId),
                aggregateAndPivotSuffix()
        );
        return queryPoints(flux);
    }

    @Override
    public List<ProtocolMetricsHistoryStore.MetricsPoint> querySummarySeries(int minutes) {
        String flux = """
                from(bucket: %s)
                  |> range(start: %s)
                  |> filter(fn: (r) => r._measurement == %s)
                  |> filter(fn: (r) => r.scope == "summary")
                  |> filter(fn: (r) => r.device_id == %s)
                  %s
                  |> sort(columns: ["_time"])
                """.formatted(
                fluxString(bucket),
                fluxDuration(minutes),
                fluxString(ProtocolMetricsHistoryWriter.MEASUREMENT),
                fluxString(ProtocolMetricsHistoryStore.SUMMARY_DEVICE_ID),
                aggregateAndPivotSuffix()
        );
        return queryPoints(flux);
    }

    @Override
    public List<ProtocolMetricsHistoryStore.MetricsPoint> queryLatestPerDevice(int minutes) {
        String flux = """
                from(bucket: %s)
                  |> range(start: %s)
                  |> filter(fn: (r) => r._measurement == %s)
                  |> filter(fn: (r) => r.scope == "device")
                  |> group(columns: ["device_id", "protocol", "scope", "_field"])
                  |> aggregateWindow(every: %ds, fn: max, createEmpty: false)
                  |> pivot(rowKey: ["_time", "device_id", "protocol", "scope"], columnKey: ["_field"], valueColumn: "_value")
                  |> group(columns: ["device_id"])
                  |> sort(columns: ["_time"], desc: true)
                  |> limit(n: 1)
                """.formatted(
                fluxString(bucket),
                fluxDuration(minutes),
                fluxString(ProtocolMetricsHistoryWriter.MEASUREMENT),
                aggregateEverySec
        );
        return queryPoints(flux);
    }

    private String aggregateAndPivotSuffix() {
        return """
                  |> group(columns: ["device_id", "protocol", "scope", "_field"])
                  |> aggregateWindow(every: %ds, fn: max, createEmpty: false)
                  |> pivot(rowKey: ["_time", "device_id", "protocol", "scope"], columnKey: ["_field"], valueColumn: "_value")"""
                .formatted(aggregateEverySec);
    }

    private static String fluxDuration(int minutes) {
        return "-" + Math.max(1, minutes) + "m";
    }

    private static String fluxString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private List<ProtocolMetricsHistoryStore.MetricsPoint> queryPoints(String flux) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Influx 监控指标 Flux: {}", flux);
            }
            List<FluxTable> tables = queryApi.query(flux, influxOrg);
            List<ProtocolMetricsHistoryStore.MetricsPoint> points = new ArrayList<>();
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    ProtocolMetricsHistoryStore.MetricsPoint point = toMetricsPoint(record);
                    if (point != null) {
                        points.add(point);
                    }
                }
            }
            points.sort(Comparator.comparing(ProtocolMetricsHistoryStore.MetricsPoint::time));
            return points;
        } catch (Exception e) {
            log.error("Influx Flux 查询失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    private ProtocolMetricsHistoryStore.MetricsPoint toMetricsPoint(FluxRecord record) {
        Instant time = record.getTime();
        if (time == null) {
            return null;
        }
        String deviceId = stringVal(record, "device_id");
        if (ProtocolMetricsHistoryStore.SUMMARY_DEVICE_ID.equals(deviceId)
                || ProtocolMetricsHistoryStore.SUMMARY_KEY.equals(deviceId)) {
            deviceId = null;
        }
        String protocol = stringVal(record, "protocol");
        if (ProtocolMetricsHistoryStore.SUMMARY_PROTOCOL.equals(protocol)) {
            protocol = null;
        }
        return new ProtocolMetricsHistoryStore.MetricsPoint(
                time,
                protocol,
                deviceId,
                doubleVal(record, "read_qps"),
                doubleVal(record, "poll_qps"),
                doubleVal(record, "write_qps"),
                doubleVal(record, "read_fail_rate"),
                doubleVal(record, "poll_fail_rate"),
                doubleVal(record, "write_fail_rate"),
                doubleVal(record, "read_latency_ms"),
                doubleVal(record, "poll_latency_ms"),
                doubleVal(record, "write_latency_ms"),
                doubleVal(record, "poll_skip_qps"),
                doubleVal(record, "poll_skip_streak"),
                doubleVal(record, "poll_last_duration_ms"),
                doubleVal(record, "offline_duration_sec")
        );
    }

    private static String stringVal(FluxRecord record, String key) {
        Object v = record.getValueByKey(key);
        return v != null ? v.toString() : null;
    }

    private static double doubleVal(FluxRecord record, String key) {
        Object v = record.getValueByKey(key);
        if (v instanceof Number number) {
            return number.doubleValue();
        }
        if (v != null && StringUtils.hasText(v.toString())) {
            try {
                return Double.parseDouble(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
