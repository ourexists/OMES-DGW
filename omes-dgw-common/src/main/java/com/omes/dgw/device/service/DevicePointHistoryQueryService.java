package com.omes.dgw.device.service;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.dto.DevicePointHistoryDeviceVo;
import com.omes.dgw.device.dto.DevicePointHistoryItemRequest;
import com.omes.dgw.device.dto.DevicePointHistoryPointMetaVo;
import com.omes.dgw.device.dto.DevicePointHistoryQueryRequest;
import com.omes.dgw.device.dto.DevicePointHistoryTimeRowVo;
import com.omes.dgw.device.dto.DevicePointHistoryVo;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.timeseries.DeviceSnapshotHistoryQuery;
import com.omes.dgw.timeseries.DeviceSnapshotHistoryQuery.SnapshotPoint;
import com.omes.dgw.timeseries.SnapshotChartAggregateResolver;
import com.omes.dgw.timeseries.SnapshotChartAggregateResolver.ResolveResult;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
@Service
public class DevicePointHistoryQueryService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ObjectProvider<DeviceSnapshotHistoryQuery> influxQuery;
    private final DeviceMapper deviceMapper;
    private final AppProperties appProperties;

    public DevicePointHistoryQueryService(
            ObjectProvider<DeviceSnapshotHistoryQuery> influxQuery,
            DeviceMapper deviceMapper,
            AppProperties appProperties
    ) {
        this.influxQuery = influxQuery;
        this.deviceMapper = deviceMapper;
        this.appProperties = appProperties;
    }

    public DevicePointHistoryVo query(DevicePointHistoryQueryRequest request) {
        assertHistoryQueryEnabled();
        TimeRange range = resolveTimeRange(request.getStartTime(), request.getEndTime());

        validateAggregateParams(request);

        DevicePointHistoryVo result = new DevicePointHistoryVo();
        result.setStartTime(range.startTime());
        result.setEndTime(range.endTime());
        result.setAggregated(false);

        int defaultMaxPoints = appProperties.getCollect().getHistory().getChartMaxPointsPerSeries();
        long spanSec = ChronoUnit.SECONDS.between(range.startInstant(), range.endInstant());

        long totalRows = 0;
        for (DevicePointHistoryItemRequest item : request.getItems()) {
            Device device = requireDevice(item.getDeviceId());
            Map<String, DevicePointConfigItem> configByName = indexPointsByName(device.getCollectConfig());
            List<String> orderedPointNames = resolveOrderedPointNames(item.getPointNames(), configByName);
            Set<String> queryNames = new LinkedHashSet<>(orderedPointNames);

            int collectSec = collectIntervalSec(device);
            ResolveResult aggregate = SnapshotChartAggregateResolver.resolve(
                    spanSec,
                    collectSec,
                    defaultMaxPoints,
                    request.getAggregateIntervalSec(),
                    request.getMaxPointsPerSeries()
            );
            if (aggregate.aggregated()) {
                result.setAggregated(true);
            }
            int interval = aggregate.sampleIntervalSec();
            if (result.getSampleIntervalSec() == 0) {
                result.setSampleIntervalSec(interval);
            } else {
                result.setSampleIntervalSec(Math.min(result.getSampleIntervalSec(), interval));
            }

            List<SnapshotPoint> points;
            try {
                points = influxQuery.getObject().queryPointSeries(
                        device.getId(),
                        queryNames,
                        range.startInstant(),
                        range.endInstant(),
                        aggregate.fluxEveryOrNull()
                );
            } catch (Exception e) {
                log.warn("Influx 点位历史查询失败 deviceId={}: {}", device.getId(), e.getMessage());
                throw new BusinessException(
                        "点位历史查询失败 deviceId=" + device.getId() + ": " + e.getMessage());
            }

            DevicePointHistoryDeviceVo deviceVo = buildDeviceHistory(
                    device, points, configByName, orderedPointNames);
            result.getDevices().add(deviceVo);
            totalRows += deviceVo.getTimeRows().size();
        }
        result.setTotal(totalRows);
        return result;
    }

    private DevicePointHistoryDeviceVo buildDeviceHistory(
            Device device,
            List<SnapshotPoint> points,
            Map<String, DevicePointConfigItem> configByName,
            List<String> orderedPointNames
    ) {
        DevicePointHistoryDeviceVo deviceVo = new DevicePointHistoryDeviceVo();
        deviceVo.setDeviceId(device.getId());
        deviceVo.setDeviceCode(device.getCode());
        deviceVo.setDeviceName(device.getName());
        deviceVo.setPointMetas(buildPointMetas(orderedPointNames, configByName));

        Map<String, Map<String, Double>> valuesByTime =
                new TreeMap<>(Comparator.reverseOrder());
        for (SnapshotPoint point : points) {
            String time = TIME_FORMAT.format(point.time());
            DevicePointConfigItem config = configByName.get(point.pointName());
            double value = roundValue(point.value(), config);
            valuesByTime
                    .computeIfAbsent(time, k -> new LinkedHashMap<>())
                    .put(point.pointName(), value);
        }

        for (Map.Entry<String, Map<String, Double>> entry : valuesByTime.entrySet()) {
            DevicePointHistoryTimeRowVo row = new DevicePointHistoryTimeRowVo();
            row.setTime(entry.getKey());
            LinkedHashMap<String, Double> values = new LinkedHashMap<>();
            for (String pointName : orderedPointNames) {
                values.put(pointName, entry.getValue().get(pointName));
            }
            row.setValues(values);
            deviceVo.getTimeRows().add(row);
        }
        return deviceVo;
    }

    private static List<DevicePointHistoryPointMetaVo> buildPointMetas(
            List<String> orderedPointNames,
            Map<String, DevicePointConfigItem> configByName
    ) {
        List<DevicePointHistoryPointMetaVo> metas = new ArrayList<>(orderedPointNames.size());
        for (String pointName : orderedPointNames) {
            DevicePointConfigItem config = configByName.get(pointName);
            DevicePointHistoryPointMetaVo meta = new DevicePointHistoryPointMetaVo();
            meta.setPointName(pointName);
            if (config != null) {
                meta.setPointCode(config.getCode());
                meta.setAddress(config.getActualAddress());
                meta.setUnit(config.getEngUnit());
                meta.setDataType(config.getDataType());
            }
            metas.add(meta);
        }
        return metas;
    }

    /**
     * 未传 pointNames 时返回设备全部配置点位（保持配置顺序）；传入时按请求顺序解析名称或编码。
     */
    private List<String> resolveOrderedPointNames(
            List<String> pointNames,
            Map<String, DevicePointConfigItem> configByName
    ) {
        if (configByName.isEmpty()) {
            throw new BusinessException("设备未配置采集点位");
        }
        if (pointNames == null || pointNames.isEmpty()) {
            return new ArrayList<>(configByName.keySet());
        }
        List<String> resolved = new ArrayList<>();
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

    private record TimeRange(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Instant startInstant,
            Instant endInstant
    ) {
    }

    private static double roundValue(double value, DevicePointConfigItem config) {
        if (config == null || config.getDecimalPlaces() == null) {
            return value;
        }
        int scale = Math.max(0, Math.min(10, config.getDecimalPlaces()));
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
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

    private void validateAggregateParams(DevicePointHistoryQueryRequest request) {
        Integer interval = request.getAggregateIntervalSec();
        if (interval != null && interval < 0) {
            throw new BusinessException("aggregateIntervalSec 不能为负数");
        }
        Integer maxPoints = request.getMaxPointsPerSeries();
        if (maxPoints != null && maxPoints < 10) {
            throw new BusinessException("maxPointsPerSeries 不能小于 10");
        }
    }

    private void assertHistoryQueryEnabled() {
        if (!"influxdb".equalsIgnoreCase(appProperties.getCollect().getMonitorType())) {
            throw new BusinessException("未启用 InfluxDB（app.collect.monitor-type）");
        }
        if (!appProperties.getCollect().getHistory().isEnabled()) {
            throw new BusinessException("未启用快照历史入库（app.collect.history.enabled）");
        }
        if (influxQuery.getIfAvailable() == null) {
            throw new BusinessException("历史查询服务不可用，请检查 Influx 连接与 history 配置");
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
            throw new BusinessException("设备不存在: " + deviceId);
        }
        return device;
    }
}
