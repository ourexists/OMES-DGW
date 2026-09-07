package com.omes.dgw.config;

import com.omes.dgw.device.cache.DeviceRealtimeCacheStore;
import com.omes.dgw.device.cache.caffeine.CaffeineDeviceRealtimeCacheStore;
import com.omes.dgw.queue.BoundedListQueueOps;
import com.omes.dgw.queue.BoundedLocalListQueueOps;
import com.omes.dgw.queue.CollectQueueMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * caffeine / 本地队列后端装配（默认）。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        prefix = "app.collect",
        name = "store-backend",
        havingValue = "caffeine",
        matchIfMissing = true
)
public class StoreBackendConfiguration {

    @Bean
    public DeviceRealtimeCacheStore deviceRealtimeCacheStore() {
        return new CaffeineDeviceRealtimeCacheStore();
    }

    @Bean
    public BoundedListQueueOps boundedListQueueOps(CollectQueueMetrics queueMetrics) {
        log.info("采集有界队列后端：本地进程内队列");
        return new BoundedLocalListQueueOps(queueMetrics);
    }
}
