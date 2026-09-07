package com.omes.dgw.device.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 设备实时缓存写入 Micrometer 指标。
 * {@link #RESULT_DEGRADED}：Redis 等基础设施失败时由门面记录；Caffeine 路径通常不触发。
 */
@Component
public class DeviceRealtimeCacheMetrics {

    public static final String METER_WRITE = "collect.realtime.cache.write";
    public static final String TAG_RESULT = "result";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_DEGRADED = "degraded";
    public static final String RESULT_SERIALIZATION = "serialization_error";
    public static final String RESULT_ERROR = "error";

    private final MeterRegistry registry;

    public DeviceRealtimeCacheMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSuccess() {
        counter(RESULT_SUCCESS).increment();
    }

    public void recordDegraded() {
        counter(RESULT_DEGRADED).increment();
    }

    public void recordSerializationError() {
        counter(RESULT_SERIALIZATION).increment();
    }

    public void recordError() {
        counter(RESULT_ERROR).increment();
    }

    private Counter counter(String result) {
        return Counter.builder(METER_WRITE)
                .tag(TAG_RESULT, result)
                .register(registry);
    }
}
