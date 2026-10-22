package com.omes.dgw.store.redis;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

/**
 * 识别 Redis 基础设施类失败（连接/超时/系统异常）。
 */
public final class RedisInfrastructureFailureDetector {

    private RedisInfrastructureFailureDetector() {
    }

    public static boolean isInfrastructureFailure(Throwable error) {
        Throwable cur = error;
        while (cur != null) {
            if (cur instanceof RedisConnectionFailureException
                    || cur instanceof RedisSystemException
                    || cur instanceof QueryTimeoutException) {
                return true;
            }
            String name = cur.getClass().getName();
            if (name.contains("RedisConnectionException")
                    || name.contains("RedisCommandTimeoutException")
                    || name.contains("RedisCommandExecutionException")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
