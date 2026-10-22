package com.omes.dgw.metrics;

import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 定期根据本地实时缓存刷新离线设备持续时长 Gauge。
 */
@Slf4j
@Component
public class CollectDeviceOfflineDurationRefresher {

    private final DeviceRealtimeCache realtimeCache;
    private final CollectDeviceOfflineMetrics offlineMetrics;

    public CollectDeviceOfflineDurationRefresher(
            DeviceRealtimeCache realtimeCache,
            CollectDeviceOfflineMetrics offlineMetrics
    ) {
        this.realtimeCache = realtimeCache;
        this.offlineMetrics = offlineMetrics;
    }

    @Scheduled(fixedDelayString = "${app.collect.metrics.offline-duration-refresh-ms:60000}")
    public void refresh() {
        try {
            List<DeviceRealtimeObj> all = realtimeCache.listAll();
            for (DeviceRealtimeObj vo : all) {
                if (vo == null || !StringUtils.hasText(vo.getDeviceId())) {
                    continue;
                }
                offlineMetrics.observe(vo.getDeviceId(), vo.isOnline(), vo.getLastUpdateTime());
            }
        } catch (Exception e) {
            log.debug("刷新设备离线时长指标失败: {}", e.getMessage());
        }
    }
}
