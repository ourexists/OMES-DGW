package com.omes.dgw.store.memory;

import com.omes.dgw.device.cache.DeviceRealtimeCacheStore;
import com.omes.dgw.queue.BoundedListQueueOps;
import com.omes.dgw.queue.BoundedLocalListQueueOps;
import com.omes.dgw.queue.CollectQueueMetrics;
import com.omes.dgw.store.PortalStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Memory portal store wiring: Caffeine cache + in-process bounded queue.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.collect", name = "store-backend", havingValue = "caffeine", matchIfMissing = true)
public class MemoryStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DeviceRealtimeCacheStore.class)
    public DeviceRealtimeCacheStore deviceRealtimeCacheStore() {
        log.info("Collect store backend: Caffeine (in-process)");
        return new CaffeineDeviceRealtimeCacheStore();
    }

    @Bean
    @ConditionalOnMissingBean(BoundedListQueueOps.class)
    public BoundedListQueueOps boundedListQueueOps(CollectQueueMetrics metrics) {
        return new BoundedLocalListQueueOps(metrics);
    }

    @Bean
    @ConditionalOnMissingBean(PortalStoreFactory.class)
    public PortalStoreFactory boxPortalStoreFactory() {
        return com.omes.dgw.store.box.BoxPortalStoreFactory.INSTANCE;
    }
}
