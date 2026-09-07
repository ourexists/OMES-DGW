package com.omes.dgw.device.service;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.dto.DeviceSnapshotChartRequest;
import com.omes.dgw.device.dto.DeviceSnapshotChartVo;
import com.omes.dgw.device.dto.DeviceSnapshotPointChartVo;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.influx.DeviceSnapshotHistoryQuery;
import com.omes.dgw.influx.DeviceSnapshotHistoryQuery.SnapshotPoint;
import com.omes.dgw.influx.SnapshotChartAggregateResolver;
import com.omes.dgw.influx.SnapshotChartAggregateResolver.ResolveResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class DeviceSnapshotQueryService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ObjectProvider<DeviceSnapshotHistoryQuery> influxQuery;
    private final DeviceMapper deviceMapper;
    private final AppProperties appProperties;

    public DeviceSnapshotQueryService(
            ObjectProvider<DeviceSnapshotHistoryQuery> influxQuery,
            DeviceMapper deviceMapper,
            AppProperties appProperties
    ) {
        this.influxQuery = influxQuery;
        this.deviceMapper = deviceMapper;
        this.appProperties = appProperties;
    }

    public DeviceSnapshotChartVo chart(DeviceSnapshotChartRequest request) {
        assertHistoryQueryEnabled();

        Device device = requireDevice(request.getDeviceId());
        TimeRange range = resolveTimeRange(request.getStartTime(), request.getEndTime());
        Map<String, DevicePointConfigItem> configByName = indexPointsByName(device.getCollectConfig());
        Set<String> resolvedNames = resolvePointNames(request.getPointNames(), configByName);
        int collectSec = collectIntervalSec(device);
        long spanSec = ChronoUnit.SECONDS.between(range.startInstant(), range.endInstant());
        ResolveResult aggregate = SnapshotChartAggregateResolver.resolve(
                spanSec,
                collectSec,
                appProperties.getCollect().getHistory().getChartMaxPointsPerSeries()
        );

        List<SnapshotPoint> points;
        try {
            points = influxQuery.getObject().queryPointSeries(
                    device.getId(),
                    resolvedNames,
                    range.startInstant(),
                    range.endInstant(),
                    aggregate.fluxEveryOrNull()
            );
        } catch (Exception e) {
            log.warn("Influx 设备快照查询失败 deviceId={}: {}", device.getId(), e.getMessage());
            throw new BusinessException("快照历史查询失败: " + e.getMessage());
        }

        DeviceSnapshotChartVo vo = new DeviceSnapshotChartVo();
        vo.setDeviceId(device.getId());
        vo.setDeviceCode(device.getCode());
        vo.setDeviceName(device.getName());
        vo.setIntervalSec(collectSec);
        vo.setSampleIntervalSec(aggregate.sampleIntervalSec());
        vo.setAggregated(aggregate.aggregated());
        vo.setStartTime(range.startTime());
        vo.setEndTime(range.endTime());
        vo.setCharts(buildPointCharts(points, configByName, resolvedNames));
        return vo;
    }

    private record TimeRange(LocalDateTime startTime, LocalDateTime endTime, Instant startInstant, Instant endInstant) {
    }

    private List<DeviceSnapshotPointChartVo> buildPointCharts(
            List<SnapshotPoint> points,
            Map<String, DevicePointConfigItem> configByName,
            Set<String> requestedNames
    ) {
        Map<String, DeviceSnapshotPointChartVo> chartMap = new LinkedHashMap<>();
        for (String name : requestedNames) {
            chartMap.put(name, newPointChart(name, configByName.get(name)));
        }
        for (SnapshotPoint point : points) {
            DeviceSnapshotPointChartVo chart = chartMap.computeIfAbsent(
                    point.pointName(),
                    n -> newPointChart(n, configByName.get(n))
            );
            if (!StringUtils.hasText(chart.getAddress()) && StringUtils.hasText(point.address())) {
                chart.setAddress(point.address());
            }
            chart.getTimestamps().add(TIME_FORMAT.format(point.time()));
            chart.getValueSeries().add(roundValue(point.value(), configByName.get(point.pointName())));
        }
        return new ArrayList<>(chartMap.values());
    }

    private static DeviceSnapshotPointChartVo newPointChart(String pointName, DevicePointConfigItem config) {
        DeviceSnapshotPointChartVo chart = new DeviceSnapshotPointChartVo();
        chart.setPointName(pointName);
        chart.setTitle(chartTitle(pointName, config));
        if (config != null) {
            chart.setPointCode(config.getCode());
            chart.setAddress(config.getActualAddress());
            chart.setUnit(config.getEngUnit());
            chart.setDataType(config.getDataType());
        }
        return chart;
    }

    private static String chartTitle(String pointName, DevicePointConfigItem config) {
        if (config != null && StringUtils.hasText(config.getEngUnit())) {
            return pointName + "(" + config.getEngUnit() + ")";
        }
        return pointName;
    }

    private static double roundValue(double value, DevicePointConfigItem config) {
        if (config == null || config.getDecimalPlaces() == null) {
            return value;
        }
        int scale = Math.max(0, Math.min(10, config.getDecimalPlaces()));
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private Set<String> resolvePointNames(List<String> pointNames, Map<String, DevicePointConfigItem> configByName) {
        if (configByName.isEmpty()) {
            throw new BusinessException("设备未配置采集点位");
        }
        if (pointNames == null || pointNames.isEmpty()) {
            return new LinkedHashSet<>(configByName.keySet());
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (String token : pointNames) {
            String key = token != null ? token.trim() : "";
            if (!StringUtils.hasText(key)) {
                continue;
            }
            DevicePointConfigItem byName = configByName.get(key);
            if (byName != null) {
                resolved.add(byName.getName());
                continue;
            }
            DevicePointConfigItem byCode = findByCode(configByName, key);
            if (byCode != null) {
                resolved.add(byCode.getName());
                continue;
            }
            throw new BusinessException("点位不存在: " + key);
        }
        if (resolved.isEmpty()) {
            throw new BusinessException("请指定至少一个点位");
        }
        return resolved;
    }

    private static DevicePointConfigItem findByCode(
            Map<String, DevicePointConfigItem> configByName,
            String code
    ) {
        for (DevicePointConfigItem item : configByName.values()) {
            if (code.equals(item.getCode())) {
                return item;
            }
        }
        return null;
    }

    private static Map<String, DevicePointConfigItem> indexPointsByName(DeviceCollectConfig collectConfig) {
        Map<String, DevicePointConfigItem> map = new LinkedHashMap<>();
        DeviceCollectConfig config = DevicePointConfigSupport.normalize(collectConfig);
        for (DevicePointConfigItem item : config.getPoints()) {
            if (item != null && StringUtils.hasText(item.getName())
                    && (item.getAccessType() == null || item.getAccessType().isCollectable())) {
                map.putIfAbsent(item.getName(), item);
            }
        }
        return map;
    }

    private void assertHistoryQueryEnabled() {
        if (!appProperties.getInflux().isEnabled()) {
            throw new BusinessException("未启用 InfluxDB（app.influx.enabled）");
        }
        if (!appProperties.getCollect().getHistory().isEnabled()) {
            throw new BusinessException("未启用快照历史入库（app.collect.history.enabled）");
        }
        if (influxQuery.getIfAvailable() == null) {
            throw new BusinessException("快照历史查询服务不可用，请检查 Influx 连接与 history 配置");
        }
    }

    private TimeRange resolveTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            throw new BusinessException("开始时间不能为空");
        }
        if (endTime == null) {
            throw new BusinessException("结束时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant startInstant = startTime.atZone(zone).toInstant();
        Instant endInstant = endTime.atZone(zone).toInstant();
        long spanMinutes = ChronoUnit.MINUTES.between(startInstant, endInstant);
        if (spanMinutes <= 0) {
            throw new BusinessException("查询时间跨度至少为 1 分钟");
        }
        int maxMinutes = Math.max(1, appProperties.getCollect().getHistory().getQueryMaxMinutes());
        if (spanMinutes > maxMinutes) {
            throw new BusinessException("查询时间跨度不能超过 " + maxMinutes + " 分钟");
        }
        return new TimeRange(startTime, endTime, startInstant, endInstant);
    }

    private static int collectIntervalSec(Device device) {
        return device.getCollectIntervalSec() != null && device.getCollectIntervalSec() > 0
                ? device.getCollectIntervalSec()
                : 1;
    }

    private Device requireDevice(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }
}
