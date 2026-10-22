package com.omes.dgw.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 协议采集 Micrometer 指标：
 * <ul>
 *   <li>poll：定时整轮采集（读点位 + 解析 + 写实时缓存）</li>
 *   <li>read：对设备的单次协议读（轮询内分块读、Modscan 读等）</li>
 *   <li>write：对设备的单次协议写（下置/控制写，非 Redis 缓存写）</li>
 * </ul>
 */
@Component
public class ProtocolCollectMetrics {

    public static final String METER_POLL = "collect.protocol.poll";
    public static final String METER_POLL_DURATION = "collect.protocol.poll.duration";
    public static final String METER_READ = "collect.protocol.read";
    public static final String METER_READ_DURATION = "collect.protocol.read.duration";
    public static final String METER_WRITE = "collect.protocol.write";
    public static final String METER_WRITE_DURATION = "collect.protocol.write.duration";

    public static final String TAG_PROTOCOL = "protocol";
    public static final String TAG_DEVICE_ID = "device_id";
    public static final String TAG_RESULT = "result";

    private final MeterRegistry registry;

    public ProtocolCollectMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordPoll(String protocol, String deviceId, boolean success, long durationMs) {
        TagsHolder tags = tags(protocol, deviceId, success);
        counter(METER_POLL, tags.all()).increment();
        timer(METER_POLL_DURATION, tags.withoutResult()).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordRead(String protocol, String deviceId, boolean success, long durationMs) {
        TagsHolder tags = tags(protocol, deviceId, success);
        counter(METER_READ, tags.all()).increment();
        timer(METER_READ_DURATION, tags.withoutResult()).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordWrite(String protocol, String deviceId, boolean success, long durationMs) {
        TagsHolder tags = tags(protocol, deviceId, success);
        counter(METER_WRITE, tags.all()).increment();
        timer(METER_WRITE_DURATION, tags.withoutResult()).record(durationMs, TimeUnit.MILLISECONDS);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }

    private static TagsHolder tags(String protocol, String deviceId, boolean success) {
        String result = success ? "success" : "failure";
        return new TagsHolder(
                TAG_PROTOCOL, protocol,
                TAG_DEVICE_ID, deviceId,
                TAG_RESULT, result
        );
    }

    private record TagsHolder(String... pairs) {
        String[] all() {
            return pairs;
        }

        String[] withoutResult() {
            return new String[]{pairs[0], pairs[1], pairs[2], pairs[3]};
        }
    }
}
