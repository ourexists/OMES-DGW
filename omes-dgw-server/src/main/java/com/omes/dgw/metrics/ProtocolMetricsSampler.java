package com.omes.dgw.metrics;

import com.omes.dgw.config.AppProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 定时从 Micrometer 采样计数器/计时器，计算 QPS 并写入时序缓存。
 */
@Component
public class ProtocolMetricsSampler {

    private final MeterRegistry meterRegistry;
    private final ProtocolMetricsHistoryStore historyStore;
    private final ObjectProvider<ProtocolMetricsSampleListener> influxConsumer;
    private final AppProperties appProperties;
    private final Map<String, CounterSnapshot> previousByDevice = new HashMap<>();

    private double intervalSec = 10;

    public ProtocolMetricsSampler(
            MeterRegistry meterRegistry,
            ProtocolMetricsHistoryStore historyStore,
            ObjectProvider<ProtocolMetricsSampleListener> influxConsumer,
            AppProperties appProperties
    ) {
        this.meterRegistry = meterRegistry;
        this.historyStore = historyStore;
        this.influxConsumer = influxConsumer;
        this.appProperties = appProperties;
    }

    @PostConstruct
    void init() {
        AppProperties.Metrics cfg = appProperties.getCollect().getMetrics();
        intervalSec = Math.max(1, cfg.getSampleIntervalSec());
        historyStore.configure(cfg.getHistoryMinutes(), cfg.getSampleIntervalSec());
    }

    @Scheduled(fixedDelayString = "${app.collect.metrics.sample-interval-sec:1}000")
    public void sample() {
        Map<String, DeviceCounters> current = collectCurrent();
        Instant now = Instant.now();
        List<ProtocolMetricsHistoryStore.MetricsPoint> sampledPoints = new ArrayList<>(current.size() + 1);

        double summaryReadQps = 0;
        double summaryPollQps = 0;
        double summaryWriteQps = 0;
        double summaryReadFail = 0;
        double summaryPollFail = 0;
        double summaryWriteFail = 0;
        double summaryReadLatency = 0;
        double summaryPollLatency = 0;
        double summaryWriteLatency = 0;
        double summaryPollSkipQps = 0;
        double summaryMaxSkipStreak = 0;
        double summaryPollLastDuration = 0;
        double summaryMaxOfflineDuration = 0;
        int summaryReadDevices = 0;
        int summaryPollDevices = 0;
        int summaryWriteDevices = 0;

        Map<String, CounterSnapshot> nextPrevious = new HashMap<>();

        for (Map.Entry<String, DeviceCounters> entry : current.entrySet()) {
            String key = entry.getKey();
            DeviceCounters counters = entry.getValue();
            CounterSnapshot prev = previousByDevice.get(key);

            double readQps = rate(counters.readTotal(), prev == null ? 0 : prev.readTotal());
            double pollQps = rate(counters.pollTotal(), prev == null ? 0 : prev.pollTotal());
            double writeQps = rate(counters.writeTotal(), prev == null ? 0 : prev.writeTotal());
            double pollSkipQps = rate(counters.skipTotal, prev == null ? 0 : prev.skipTotal());
            double readFailRate = failRate(
                    counters.readSuccess, counters.readFailure,
                    prev == null ? 0 : prev.readSuccess,
                    prev == null ? 0 : prev.readFailure
            );
            double pollFailRate = failRate(
                    counters.pollSuccess, counters.pollFailure,
                    prev == null ? 0 : prev.pollSuccess,
                    prev == null ? 0 : prev.pollFailure
            );
            double writeFailRate = failRate(
                    counters.writeSuccess, counters.writeFailure,
                    prev == null ? 0 : prev.writeSuccess,
                    prev == null ? 0 : prev.writeFailure
            );

            ProtocolMetricsHistoryStore.MetricsPoint point = new ProtocolMetricsHistoryStore.MetricsPoint(
                    now,
                    counters.protocol,
                    counters.deviceId,
                    readQps,
                    pollQps,
                    writeQps,
                    readFailRate,
                    pollFailRate,
                    writeFailRate,
                    counters.readLatencyMs,
                    counters.pollLatencyMs,
                    counters.writeLatencyMs,
                    pollSkipQps,
                    counters.pollSkipStreak,
                    counters.pollLastDurationMs,
                    counters.offlineDurationSec
            );
            historyStore.append(key, point);
            sampledPoints.add(point);

            nextPrevious.put(key, counters.toSnapshot());

            summaryReadQps += readQps;
            summaryPollQps += pollQps;
            summaryWriteQps += writeQps;
            summaryPollSkipQps += pollSkipQps;
            summaryMaxSkipStreak = Math.max(summaryMaxSkipStreak, counters.pollSkipStreak);
            summaryMaxOfflineDuration = Math.max(summaryMaxOfflineDuration, counters.offlineDurationSec);
            if (readQps > 0) {
                summaryReadFail += readFailRate * readQps;
                summaryReadLatency += counters.readLatencyMs * readQps;
                summaryReadDevices++;
            }
            if (pollQps > 0) {
                summaryPollFail += pollFailRate * pollQps;
                summaryPollLatency += counters.pollLatencyMs * pollQps;
                summaryPollLastDuration += counters.pollLastDurationMs * pollQps;
                summaryPollDevices++;
            }
            if (writeQps > 0) {
                summaryWriteFail += writeFailRate * writeQps;
                summaryWriteLatency += counters.writeLatencyMs * writeQps;
                summaryWriteDevices++;
            }
        }

        previousByDevice.clear();
        previousByDevice.putAll(nextPrevious);

        double avgReadFail = summaryReadQps > 0 ? summaryReadFail / summaryReadQps : 0;
        double avgPollFail = summaryPollQps > 0 ? summaryPollFail / summaryPollQps : 0;
        double avgWriteFail = summaryWriteQps > 0 ? summaryWriteFail / summaryWriteQps : 0;
        double avgReadLatency = summaryReadDevices > 0 ? summaryReadLatency / summaryReadDevices : 0;
        double avgPollLatency = summaryPollDevices > 0 ? summaryPollLatency / summaryPollDevices : 0;
        double avgWriteLatency = summaryWriteDevices > 0 ? summaryWriteLatency / summaryWriteDevices : 0;
        double avgPollLastDuration = summaryPollDevices > 0 ? summaryPollLastDuration / summaryPollDevices : 0;

        ProtocolMetricsHistoryStore.MetricsPoint summaryPoint = new ProtocolMetricsHistoryStore.MetricsPoint(
                now,
                ProtocolMetricsHistoryStore.SUMMARY_PROTOCOL,
                ProtocolMetricsHistoryStore.SUMMARY_DEVICE_ID,
                summaryReadQps,
                summaryPollQps,
                summaryWriteQps,
                avgReadFail,
                avgPollFail,
                avgWriteFail,
                avgReadLatency,
                avgPollLatency,
                avgWriteLatency,
                summaryPollSkipQps,
                summaryMaxSkipStreak,
                avgPollLastDuration,
                summaryMaxOfflineDuration
        );
        historyStore.append(ProtocolMetricsHistoryStore.SUMMARY_KEY, summaryPoint);
        sampledPoints.add(summaryPoint);

        influxConsumer.ifAvailable(consumer -> consumer.onSampleCompleted(sampledPoints));
    }

    private Map<String, DeviceCounters> collectCurrent() {
        Map<String, DeviceCounters> map = new HashMap<>();
        for (Meter meter : meterRegistry.getMeters()) {
            Meter.Id id = meter.getId();
            String name = id.getName();
            String deviceId = id.getTag(ProtocolCollectMetrics.TAG_DEVICE_ID);
            if (!StringUtils.hasText(deviceId)) {
                continue;
            }
            String protocol = id.getTag(ProtocolCollectMetrics.TAG_PROTOCOL);
            if (!StringUtils.hasText(protocol)) {
                if (CollectDeviceOfflineMetrics.METER_OFFLINE_DURATION.equals(name) && meter instanceof Gauge gauge) {
                    attachOfflineDuration(map, deviceId, gauge.value());
                }
                continue;
            }
            String result = id.getTag(ProtocolCollectMetrics.TAG_RESULT);
            String key = seriesKey(protocol, deviceId);
            DeviceCounters counters = map.computeIfAbsent(key, k -> new DeviceCounters(protocol, deviceId));

            if (meter instanceof Counter counter) {
                double count = counter.count();
                switch (name) {
                    case ProtocolCollectMetrics.METER_READ -> applyCounter(counters, result, count, CounterKind.READ);
                    case ProtocolCollectMetrics.METER_POLL -> applyCounter(counters, result, count, CounterKind.POLL);
                    case ProtocolCollectMetrics.METER_WRITE -> applyCounter(counters, result, count, CounterKind.WRITE);
                    case ProtocolPollSkipMetrics.METER_SKIP_TOTAL -> counters.skipTotal = count;
                    default -> {
                    }
                }
            } else if (meter instanceof Timer timer) {
                double meanMs = timer.mean(TimeUnit.MILLISECONDS);
                if (ProtocolCollectMetrics.METER_READ_DURATION.equals(name)) {
                    counters.readLatencyMs = meanMs;
                } else if (ProtocolCollectMetrics.METER_POLL_DURATION.equals(name)) {
                    counters.pollLatencyMs = meanMs;
                } else if (ProtocolCollectMetrics.METER_WRITE_DURATION.equals(name)) {
                    counters.writeLatencyMs = meanMs;
                }
            }
        }

        for (DeviceCounters counters : map.values()) {
            counters.pollSkipStreak = readGauge(
                    ProtocolPollSkipMetrics.METER_SKIP_STREAK,
                    ProtocolCollectMetrics.TAG_PROTOCOL, counters.protocol,
                    ProtocolCollectMetrics.TAG_DEVICE_ID, counters.deviceId
            );
            counters.pollLastDurationMs = readGauge(
                    ProtocolPollSkipMetrics.METER_LAST_DURATION_MS,
                    ProtocolCollectMetrics.TAG_PROTOCOL, counters.protocol,
                    ProtocolCollectMetrics.TAG_DEVICE_ID, counters.deviceId
            );
            counters.offlineDurationSec = readGauge(
                    CollectDeviceOfflineMetrics.METER_OFFLINE_DURATION,
                    CollectDeviceOfflineMetrics.TAG_DEVICE_ID, counters.deviceId
            );
        }
        return map;
    }

    private void attachOfflineDuration(Map<String, DeviceCounters> map, String deviceId, double seconds) {
        for (DeviceCounters counters : map.values()) {
            if (deviceId.equals(counters.deviceId)) {
                counters.offlineDurationSec = Math.max(0, seconds);
                return;
            }
        }
    }

    private double readGauge(String name, String tagKey1, String tagValue1, String tagKey2, String tagValue2) {
        if (!StringUtils.hasText(tagValue1) || !StringUtils.hasText(tagValue2)) {
            return 0;
        }
        Gauge gauge = meterRegistry.find(name)
                .tag(tagKey1, tagValue1)
                .tag(tagKey2, tagValue2)
                .gauge();
        return gauge != null ? gauge.value() : 0;
    }

    private double readGauge(String name, String tagKey, String tagValue) {
        if (!StringUtils.hasText(tagValue)) {
            return 0;
        }
        Gauge gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
        return gauge != null ? gauge.value() : 0;
    }

    private static void applyCounter(DeviceCounters counters, String result, double count, CounterKind kind) {
        boolean success = "success".equals(result);
        boolean failure = "failure".equals(result);
        switch (kind) {
            case READ -> {
                if (success) {
                    counters.readSuccess = count;
                } else if (failure) {
                    counters.readFailure = count;
                }
            }
            case POLL -> {
                if (success) {
                    counters.pollSuccess = count;
                } else if (failure) {
                    counters.pollFailure = count;
                }
            }
            case WRITE -> {
                if (success) {
                    counters.writeSuccess = count;
                } else if (failure) {
                    counters.writeFailure = count;
                }
            }
        }
    }

    private double rate(double current, double previous) {
        double delta = current - previous;
        if (delta < 0) {
            return 0;
        }
        return delta / intervalSec;
    }

    private static double failRate(double successNow, double failNow, double successPrev, double failPrev) {
        double deltaSuccess = Math.max(0, successNow - successPrev);
        double deltaFail = Math.max(0, failNow - failPrev);
        double total = deltaSuccess + deltaFail;
        if (total <= 0) {
            return 0;
        }
        return deltaFail / total;
    }

    static String seriesKey(String protocol, String deviceId) {
        return protocol + "|" + deviceId;
    }

    private enum CounterKind {
        READ, POLL, WRITE
    }

    private static final class DeviceCounters {
        final String protocol;
        final String deviceId;
        double readSuccess;
        double readFailure;
        double pollSuccess;
        double pollFailure;
        double writeSuccess;
        double writeFailure;
        double skipTotal;
        double readLatencyMs;
        double pollLatencyMs;
        double writeLatencyMs;
        double pollSkipStreak;
        double pollLastDurationMs;
        double offlineDurationSec;

        DeviceCounters(String protocol, String deviceId) {
            this.protocol = protocol;
            this.deviceId = deviceId;
        }

        double readTotal() {
            return readSuccess + readFailure;
        }

        double pollTotal() {
            return pollSuccess + pollFailure;
        }

        double writeTotal() {
            return writeSuccess + writeFailure;
        }

        CounterSnapshot toSnapshot() {
            return new CounterSnapshot(
                    readSuccess, readFailure, pollSuccess, pollFailure, writeSuccess, writeFailure, skipTotal
            );
        }
    }

    private record CounterSnapshot(
            double readSuccess,
            double readFailure,
            double pollSuccess,
            double pollFailure,
            double writeSuccess,
            double writeFailure,
            double skipTotal
    ) {
        double readTotal() {
            return readSuccess + readFailure;
        }

        double pollTotal() {
            return pollSuccess + pollFailure;
        }

        double writeTotal() {
            return writeSuccess + writeFailure;
        }
    }
}
