package com.omes.dgw.device.cache;

/**
 * 实时缓存后端基础设施不可用（如 Redis 连接失败）。
 * 门面捕获后记 degraded，不中断采集主路径。
 */
public class RealtimeCacheInfrastructureException extends RuntimeException {

    public RealtimeCacheInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }

    public RealtimeCacheInfrastructureException(String message) {
        super(message);
    }
}
