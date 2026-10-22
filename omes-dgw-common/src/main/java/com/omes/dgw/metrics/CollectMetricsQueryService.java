package com.omes.dgw.metrics;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.metrics.dto.CollectMetricsChartVo;
import com.omes.dgw.metrics.dto.CollectMetricsDeviceSnapshotVo;
import com.omes.dgw.metrics.dto.CollectMetricsOverviewVo;
import com.omes.dgw.monitoring.service.MonitoringSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CollectMetricsQueryService {

    private static final int DEFAULT_QUERY_MINUTES = 30;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ProtocolMetricsHistoryStore historyStore;
    private final ObjectProvider<ProtocolMetricsHistoryQuery> influxQuery;
    private final DeviceMapper deviceMapper;
    private final AppProperties appProperties;
    private final DeviceRealtimeCache deviceRealtimeCache;
    private final MonitoringSnapshotService monitoringSnapshotService;

    public CollectMetricsQueryService(
            ProtocolMetricsHistoryStore historyStore,
            ObjectProvider<ProtocolMetricsHistoryQuery> influxQuery,
            DeviceMapper deviceMapper,
            AppProperties appProperties,
            DeviceRealtimeCache deviceRealtimeCache,
            MonitoringSnapshotService monitoringSnapshotService
    ) {
        this.historyStore = historyStore;
        this.influxQuery = influxQuery;
        this.deviceMapper = deviceMapper;
        this.appProperties = appProperties;
        this.deviceRealtimeCache = deviceRealtimeCache;
        this.monitoringSnapshotService = monitoringSnapshotService;
    }

    public CollectMetricsChartVo chart(String deviceId, Integer minutes) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException("deviceId 不能为空");
        }
        Device device = requireDevice(deviceId);
        int rangeMinutes = resolveQueryMinutes(minutes);

        List<ProtocolMetricsHistoryStore.MetricsPoint> points = queryDevicePoints(device, rangeMinutes);
        return toChartVo(device, points, rangeMinutes);
    }

    public CollectMetricsChartVo summaryChart(Integer minutes) {
        int rangeMinutes = resolveQueryMinutes(minutes);
        List<ProtocolMetricsHistoryStore.MetricsPoint> points = querySummaryPoints(rangeMinutes);
        CollectMetricsChartVo vo = buildChartSeries(points);
        vo.setIntervalSec(sampleIntervalSec());
        vo.setRangeMinutes(rangeMinutes);
        return vo;
    }

    public CollectMetricsOverviewVo overview(Integer minutes) {
        int rangeMinutes = resolveQueryMinutes(minutes);
        CollectMetricsOverviewVo overview = new CollectMetricsOverviewVo();
        overview.setSummary(summaryChart(minutes));

        List<CollectMetricsDeviceSnapshotVo> snapshots = useInfluxQuery()
                ? buildSnapshotsFromInflux(rangeMinutes)
                : buildSnapshotsFromMemory(rangeMinutes);
        overview.setDevices(snapshots);
        return overview;
    }

    private List<ProtocolMetricsHistoryStore.MetricsPoint> queryDevicePoints(Device device, int rangeMinutes) {
        if (useInfluxQuery()) {
            try {
                List<ProtocolMetricsHistoryStore.MetricsPoint> influxPoints =
                        influxQuery.getObject().queryDeviceSeries(device.getId(), rangeMinutes);
                if (!influxPoints.isEmpty()) {
                    return influxPoints;
                }
                log.debug("Influx 设备指标无数据，回退内存: deviceId={}", device.getId());
            } catch (Exception e) {
                log.warn("Influx 设备指标查询失败，回退内存: deviceId={}, {}", device.getId(), e.getMessage());
            }
        }
        return queryDevicePointsFromMemory(device, rangeMinutes);
    }

    private List<ProtocolMetricsHistoryStore.MetricsPoint> queryDevicePointsFromMemory(Device device, int rangeMinutes) {
        if (device.getProtocol() == null) {
            return List.of();
        }
        String key = device.getProtocol().getCode() + "|" + device.getId();
        List<ProtocolMetricsHistoryStore.MetricsPoint> points = filterPoints(historyStore.getPoints(key), rangeMinutes);
        if (!points.isEmpty()) {
            return points;
        }
        return findDevicePointsInMemory(device.getId(), rangeMinutes);
    }

    /** 协议码不一致时按 deviceId 兜底匹配内存序列 */
    private List<ProtocolMetricsHistoryStore.MetricsPoint> findDevicePointsInMemory(String deviceId, int rangeMinutes) {
        Instant cutoff = Instant.now().minus(rangeMinutes, ChronoUnit.MINUTES);
        for (Map.Entry<String, java.util.concurrent.ConcurrentLinkedDeque<ProtocolMetricsHistoryStore.MetricsPoint>> entry
                : historyStore.snapshotSeries().entrySet()) {
            if (ProtocolMetricsHistoryStore.SUMMARY_KEY.equals(entry.getKey())) {
                continue;
            }
            List<ProtocolMetricsHistoryStore.MetricsPoint> matched = entry.getValue().stream()
                    .filter(p -> deviceId.equals(p.deviceId()) && !p.time().isBefore(cutoff))
                    .toList();
            if (!matched.isEmpty()) {
                log.debug("内存指标按 deviceId 命中序列 key={}, deviceId={}", entry.getKey(), deviceId);
                return matched;
            }
        }
        return List.of();
    }

    private List<ProtocolMetricsHistoryStore.MetricsPoint> querySummaryPoints(int rangeMinutes) {
        if (useInfluxQuery()) {
            try {
                List<ProtocolMetricsHistoryStore.MetricsPoint> influxPoints =
                        influxQuery.getObject().querySummarySeries(rangeMinutes);
                if (!influxPoints.isEmpty()) {
                    return influxPoints;
                }
                log.debug("Influx 汇总指标无数据，回退内存");
            } catch (Exception e) {
                log.warn("Influx 汇总指标查询失败，回退内存: {}", e.getMessage());
            }
        }
        return filterPoints(historyStore.getPoints(ProtocolMetricsHistoryStore.SUMMARY_KEY), rangeMinutes);
    }

    private List<CollectMetricsDeviceSnapshotVo> buildSnapshotsFromInflux(int rangeMinutes) {
        List<ProtocolMetricsHistoryStore.MetricsPoint> latest;
        try {
            latest = influxQuery.getObject().queryLatestPerDevice(rangeMinutes);
        } catch (Exception e) {
            log.warn("Influx 设备最新指标查询失败，回退内存: {}", e.getMessage());
            return buildSnapshotsFromMemory(rangeMinutes);
        }
        List<CollectMetricsDeviceSnapshotVo> snapshots = new ArrayList<>(latest.size());
        for (ProtocolMetricsHistoryStore.MetricsPoint point : latest) {
            CollectMetricsDeviceSnapshotVo snap = toSnapshot(point);
            if (snap != null) {
                snapshots.add(snap);
            }
        }
        snapshots.sort(Comparator.comparing(CollectMetricsDeviceSnapshotVo::getDeviceName,
                Comparator.nullsLast(String::compareTo)));
        return snapshots;
    }

    private List<CollectMetricsDeviceSnapshotVo> buildSnapshotsFromMemory(int rangeMinutes) {
        Instant cutoff = Instant.now().minus(rangeMinutes, ChronoUnit.MINUTES);
        List<CollectMetricsDeviceSnapshotVo> snapshots = new ArrayList<>();

        for (Map.Entry<String, java.util.concurrent.ConcurrentLinkedDeque<ProtocolMetricsHistoryStore.MetricsPoint>> entry
                : historyStore.snapshotSeries().entrySet()) {
            if (ProtocolMetricsHistoryStore.SUMMARY_KEY.equals(entry.getKey())) {
                continue;
            }
            ProtocolMetricsHistoryStore.MetricsPoint latest = entry.getValue().stream()
                    .filter(p -> !p.time().isBefore(cutoff))
                    .max(Comparator.comparing(ProtocolMetricsHistoryStore.MetricsPoint::time))
                    .orElse(null);
            if (latest != null) {
                CollectMetricsDeviceSnapshotVo snap = toSnapshot(latest);
                if (snap != null) {
                    snapshots.add(snap);
                }
            }
        }
        snapshots.sort(Comparator.comparing(CollectMetricsDeviceSnapshotVo::getDeviceName,
                Comparator.nullsLast(String::compareTo)));
        return snapshots;
    }

    private CollectMetricsDeviceSnapshotVo toSnapshot(ProtocolMetricsHistoryStore.MetricsPoint latest) {
        if (ProtocolMetricsHistoryStore.isSummary(latest)) {
            return null;
        }
        Device device = parseDevice(latest.deviceId());
        CollectMetricsDeviceSnapshotVo snap = new CollectMetricsDeviceSnapshotVo();
        snap.setDeviceId(device != null ? device.getId() : latest.deviceId());
        snap.setDeviceCode(device != null ? device.getCode() : null);
        snap.setDeviceName(device != null ? device.getName() : latest.deviceId());
        snap.setProtocol(latest.protocol());
        snap.setSampleTime(TIME_FORMAT.format(latest.time()));
        snap.setReadQps(round3(latest.readQps()));
        snap.setPollQps(round3(latest.pollQps()));
        snap.setWriteQps(round3(latest.writeQps()));
        snap.setReadFailRate(round3(latest.readFailRate()));
        snap.setPollFailRate(round3(latest.pollFailRate()));
        snap.setWriteFailRate(round3(latest.writeFailRate()));
        snap.setReadLatencyMs(round3(latest.readLatencyMs()));
        snap.setPollLatencyMs(round3(latest.pollLatencyMs()));
        snap.setWriteLatencyMs(round3(latest.writeLatencyMs()));
        snap.setPollSkipQps(round3(latest.pollSkipQps()));
        snap.setPollSkipStreak(round3(latest.pollSkipStreak()));
        snap.setPollLastDurationMs(round3(latest.pollLastDurationMs()));
        snap.setOfflineDurationSec(round3(latest.offlineDurationSec()));
        return snap;
    }

    private CollectMetricsChartVo toChartVo(
            Device device,
            List<ProtocolMetricsHistoryStore.MetricsPoint> points,
            int rangeMinutes
    ) {
        CollectMetricsChartVo vo = buildChartSeries(points);
        vo.setIntervalSec(sampleIntervalSec());
        vo.setRangeMinutes(rangeMinutes);
        vo.setDeviceId(device.getId());
        vo.setDeviceCode(device.getCode());
        vo.setDeviceName(device.getName());
        vo.setProtocol(device.getProtocol() != null ? device.getProtocol().getCode() : null);
        enrichDeviceChartContext(vo, device);
        return vo;
    }

    private void enrichDeviceChartContext(CollectMetricsChartVo vo, Device device) {
        vo.setInstanceId(monitoringSnapshotService.instanceId());
        vo.setInfrastructure(monitoringSnapshotService.infrastructureSnapshot());
        vo.setPollSkip(monitoringSnapshotService.devicePollSkipSnapshot(
                device.getId(),
                device.getProtocol() != null ? device.getProtocol().getCode() : null
        ));

        DeviceRealtimeObj realtime = deviceRealtimeCache.getByDeviceId(device.getId());
        if (realtime != null) {
            vo.setOnline(realtime.isOnline());
            if (realtime.getConnectionStatus() != null) {
                vo.setConnectionStatus(realtime.getConnectionStatus().name());
                vo.setConnectionStatusLabel(realtime.getConnectionStatusLabel());
            }
            vo.setConnectionStatusMessage(realtime.getConnectionStatusMessage());
            if (realtime.getOfflineReason() != null) {
                vo.setOfflineReason(realtime.getOfflineReason().getLabel());
            }
            if (realtime.getLastUpdateTime() != null) {
                vo.setLastUpdateTime(realtime.getLastUpdateTime().toString());
            }
        }
    }

    private CollectMetricsChartVo buildChartSeries(List<ProtocolMetricsHistoryStore.MetricsPoint> points) {
        CollectMetricsChartVo vo = new CollectMetricsChartVo();
        for (ProtocolMetricsHistoryStore.MetricsPoint p : points) {
            vo.getTimestamps().add(TIME_FORMAT.format(p.time()));
            vo.getReadQps().add(round3(p.readQps()));
            vo.getPollQps().add(round3(p.pollQps()));
            vo.getWriteQps().add(round3(p.writeQps()));
            vo.getReadFailRate().add(round3(p.readFailRate()));
            vo.getPollFailRate().add(round3(p.pollFailRate()));
            vo.getWriteFailRate().add(round3(p.writeFailRate()));
            vo.getReadLatencyMs().add(round3(p.readLatencyMs()));
            vo.getPollLatencyMs().add(round3(p.pollLatencyMs()));
            vo.getWriteLatencyMs().add(round3(p.writeLatencyMs()));
            vo.getPollSkipQps().add(round3(p.pollSkipQps()));
            vo.getPollSkipStreak().add(round3(p.pollSkipStreak()));
            vo.getPollLastDurationMs().add(round3(p.pollLastDurationMs()));
            vo.getOfflineDurationSec().add(round3(p.offlineDurationSec()));
        }
        return vo;
    }

    private List<ProtocolMetricsHistoryStore.MetricsPoint> filterPoints(
            List<ProtocolMetricsHistoryStore.MetricsPoint> points,
            int rangeMinutes
    ) {
        Instant cutoff = Instant.now().minus(rangeMinutes, ChronoUnit.MINUTES);
        return points.stream()
                .filter(p -> !p.time().isBefore(cutoff))
                .toList();
    }

    private boolean useInfluxQuery() {
        return appProperties.getCollect().getMetrics().isPersistToInflux()
                && influxQuery.getIfAvailable() != null;
    }

    /**
     * 解析查询时间窗（分钟）：以请求参数为准，仅受 influxQueryMaxMinutes 上限约束。
     * historyMinutes 仅控制内存环形缓冲保留时长，不得压缩用户请求的查询范围。
     */
    private int resolveQueryMinutes(Integer minutes) {
        int maxAllowed = Math.max(1, appProperties.getCollect().getMetrics().getInfluxQueryMaxMinutes());
        int requested = minutes == null || minutes <= 0 ? DEFAULT_QUERY_MINUTES : minutes;
        return Math.min(Math.max(1, requested), maxAllowed);
    }

    private int sampleIntervalSec() {
        return Math.max(1, appProperties.getCollect().getMetrics().getSampleIntervalSec());
    }

    private Device requireDevice(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private Device parseDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        return deviceMapper.selectById(deviceId.trim());
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
