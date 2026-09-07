package com.omes.dgw.store.influx;

import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.omes.dgw.influx.DeviceRealtimeHistoryWriter;
import com.omes.dgw.influx.DeviceSnapshotHistoryQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从 InfluxDB 查询设备点位快照历史（measurement {@link DeviceRealtimeHistoryWriter#MEASUREMENT_POINT}）。
 */
@Slf4j
@ConditionalOnProperty(prefix = "app.collect.history", name = "enabled", havingValue = "true")
@ConditionalOnBean(name = "influxDBClient")
public class DeviceSnapshotInfluxQueryService implements DeviceSnapshotHistoryQuery {

    private final QueryApi queryApi;
    private final String influxOrg;
    private final String bucket;

    public DeviceSnapshotInfluxQueryService(InfluxConnectionService influx) {
        this.queryApi = influx.queryApi();
        this.influxOrg = influx.org();
        this.bucket = influx.bucket();
    }

    @Override
    public List<SnapshotPoint> queryPointSeries(
            String deviceId,
            Set<String> pointNames,
            Instant start,
            Instant end,
            String fluxEveryOrNull
    ) {
        String flux = buildFlux(deviceId, pointNames, start, end, fluxEveryOrNull);
        if (log.isDebugEnabled()) {
            log.debug("Influx 设备快照 Flux: {}", flux);
        }
        return queryPoints(flux);
    }

    private String buildFlux(
            String deviceId,
            Set<String> pointNames,
            Instant start,
            Instant end,
            String fluxEveryOrNull
    ) {
        boolean aggregated = StringUtils.hasText(fluxEveryOrNull);
        StringBuilder flux = new StringBuilder(aggregated ? 512 : 320);
        flux.append("from(bucket: ").append(fluxString(bucket)).append(")\n");
        flux.append("  |> range(start: ")
                .append(fluxTime(start))
                .append(", stop: ")
                .append(fluxTime(end))
                .append(")\n");
        flux.append("  |> filter(fn: (r) => r._measurement == ")
                .append(fluxString(DeviceRealtimeHistoryWriter.MEASUREMENT_POINT))
                .append(")\n");
        flux.append("  |> filter(fn: (r) => r.device_id == ")
                .append(fluxString(deviceId))
                .append(")\n");
        if (pointNames != null && !pointNames.isEmpty()) {
            flux.append("  |> filter(fn: (r) => ")
                    .append(pointNames.stream()
                            .map(name -> "r.point_name == " + fluxString(name))
                            .collect(Collectors.joining(" or ")))
                    .append(")\n");
        }
        if (aggregated) {
            flux.append("  |> filter(fn: (r) => r._field == \"value\")\n");
            flux.append("  |> group(columns: [\"device_id\", \"device_code\", \"protocol\", \"point_name\", \"address\"])\n");
            flux.append("  |> aggregateWindow(every: ")
                    .append(fluxEveryOrNull)
                    .append(", fn: last, createEmpty: false)\n");
            flux.append("  |> group()\n");
        } else {
            flux.append("  |> filter(fn: (r) => r._field == \"value\" or r._field == \"value_text\")\n");
        }
        flux.append("  |> sort(columns: [\"_time\"])");
        return flux.toString();
    }

    private static String fluxString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String fluxTime(Instant instant) {
        return "time(v: " + fluxString(instant.toString()) + ")";
    }

    private List<SnapshotPoint> queryPoints(String flux) {
        try {
            List<FluxTable> tables = queryApi.query(flux, influxOrg);
            List<SnapshotPoint> points = new ArrayList<>();
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    SnapshotPoint point = toSnapshotPoint(record);
                    if (point != null) {
                        points.add(point);
                    }
                }
            }
            points.sort(Comparator
                    .comparing(SnapshotPoint::pointName, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(SnapshotPoint::time));
            return points;
        } catch (Exception e) {
            log.error("Influx 设备快照查询失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    private static SnapshotPoint toSnapshotPoint(FluxRecord record) {
        Instant time = record.getTime();
        if (time == null) {
            return null;
        }
        String pointName = stringVal(record, "point_name");
        if (!StringUtils.hasText(pointName)) {
            return null;
        }
        Double value = parseFieldValue(record);
        if (value == null) {
            return null;
        }
        return new SnapshotPoint(time, pointName, stringVal(record, "address"), value);
    }

    private static Double parseFieldValue(FluxRecord record) {
        Object raw = record.getValue();
        if (raw == null) {
            raw = record.getValueByKey("_value");
        }
        Double parsed = doubleVal(raw);
        if (parsed != null) {
            return parsed;
        }
        if ("value_text".equals(record.getField()) && raw != null) {
            return doubleVal(raw);
        }
        return null;
    }

    private static String stringVal(FluxRecord record, String key) {
        Object v = record.getValueByKey(key);
        return v != null ? v.toString() : null;
    }

    private static Double doubleVal(Object v) {
        if (v instanceof Number number) {
            return number.doubleValue();
        }
        if (v != null && StringUtils.hasText(v.toString())) {
            try {
                return Double.parseDouble(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
