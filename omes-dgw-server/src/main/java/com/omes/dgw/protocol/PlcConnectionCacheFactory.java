package com.omes.dgw.protocol;

import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager;

import java.time.Duration;

/** PLC4X {@link CachedPlcConnectionManager} 统一构建（轮询 / 按需读共用租约配置）。 */
public final class PlcConnectionCacheFactory {

    private PlcConnectionCacheFactory() {
    }

    public static PlcConnectionManager create(int maxLeaseSec, int maxWaitSec, int maxIdleSec) {
        int lease = Math.max(30, maxLeaseSec);
        int wait = Math.max(5, maxWaitSec);
        int idle = Math.max(60, maxIdleSec);
        return CachedPlcConnectionManager.getBuilder()
                .withMaxLeaseTime(Duration.ofSeconds(lease))
                .withMaxWaitTime(Duration.ofSeconds(wait))
                .withMaxIdleTime(Duration.ofSeconds(idle))
                .build();
    }
}
