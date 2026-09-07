package com.omes.dgw.store.redis;

/**
 * 采集有界队列 Redis List Lua。
 */
public final class CollectRedisListQueueLuaScripts {

    private CollectRedisListQueueLuaScripts() {
    }

    /**
     * KEYS[1]=queueKey
     * ARGV[1]=payload ARGV[2]=maxQueueSize
     * 返回 {length, trimmed}
     */
    public static final String RIGHT_PUSH_TRIM = """
            local len = redis.call('RPUSH', KEYS[1], ARGV[1])
            local max = tonumber(ARGV[2])
            local trimmed = 0
            if len > max then
              trimmed = len - max
              redis.call('LTRIM', KEYS[1], -max, -1)
              len = max
            end
            return {len, trimmed}
            """;

    /**
     * KEYS[1]=queueKey
     * ARGV[1]=maxQueueSize ARGV[2..]=payloads（旧→新）
     * 返回 {length, trimmed}
     */
    public static final String REQUEUE_HEAD_TRIM = """
            local max = tonumber(ARGV[1])
            for i = #ARGV, 2, -1 do
              local payload = ARGV[i]
              if payload ~= nil and payload ~= '' then
                redis.call('LPUSH', KEYS[1], payload)
              end
            end
            local len = redis.call('LLEN', KEYS[1])
            local trimmed = 0
            if len > max then
              trimmed = len - max
              redis.call('LTRIM', KEYS[1], -max, -1)
              len = max
            end
            return {len, trimmed}
            """;

    /**
     * KEYS[1]=queueKey
     * ARGV[1]=count
     * 返回 payload 数组
     */
    public static final String POP_BATCH = """
            local n = tonumber(ARGV[1])
            local result = {}
            for i = 1, n do
              local item = redis.call('LPOP', KEYS[1])
              if not item then
                break
              end
              result[#result + 1] = item
            end
            return result
            """;
}
