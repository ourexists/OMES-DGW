package com.omes.dgw.store.redis;

/**
 * 设备实时缓存 Redis Lua 脚本（语义须与历史实现一致）。
 */
public final class DeviceRealtimeCacheLuaScripts {

    private DeviceRealtimeCacheLuaScripts() {
    }

    /**
     * KEYS[1]=deviceKey KEYS[2]=indexKey KEYS[3]=newCodeKey（可空串）
     * ARGV[1]=json ARGV[2]=deviceId ARGV[3]=oldCodeKey（可空串）
     */
    public static final String SAVE = """
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('SADD', KEYS[2], ARGV[2])
            if ARGV[3] ~= nil and ARGV[3] ~= '' and ARGV[3] ~= KEYS[3] then
              redis.call('DEL', ARGV[3])
            end
            if KEYS[3] ~= nil and KEYS[3] ~= '' then
              redis.call('SET', KEYS[3], ARGV[2])
            end
            return 1
            """;

    /**
     * KEYS[1]=deviceKey KEYS[2]=indexKey KEYS[3]=newCodeKey（可空串）
     * ARGV[1]=json ARGV[2]=deviceId ARGV[3]=expectOnline(1/0)
     * ARGV[4]=checkLastUpdate(1/0) ARGV[5]=expectLastUpdate ARGV[6]=oldCodeKey
     * 返回 1=ok / 0=mismatch / -1=not found
     */
    public static final String SAVE_IF_ONLINE_MATCHES = """
            local raw = redis.call('GET', KEYS[1])
            if not raw then
              return -1
            end
            local obj = cjson.decode(raw)
            local online = obj['online']
            local expectOnline = (ARGV[3] == '1')
            local actualOnline = (online == true or online == 1 or online == 'true' or online == '1')
            if actualOnline ~= expectOnline then
              return 0
            end
            if ARGV[4] == '1' then
              local lut = obj['lastUpdateTime']
              if lut == nil then
                lut = ''
              else
                lut = tostring(lut)
              end
              if lut ~= ARGV[5] then
                return 0
              end
            end
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('SADD', KEYS[2], ARGV[2])
            if ARGV[6] ~= nil and ARGV[6] ~= '' and ARGV[6] ~= KEYS[3] then
              redis.call('DEL', ARGV[6])
            end
            if KEYS[3] ~= nil and KEYS[3] ~= '' then
              redis.call('SET', KEYS[3], ARGV[2])
            end
            return 1
            """;

    /**
     * KEYS[1]=deviceKey KEYS[2]=indexKey KEYS[3]=codeKey（可空串）
     * ARGV[1]=deviceId
     */
    public static final String REMOVE = """
            if KEYS[1] ~= nil and KEYS[1] ~= '' then
              redis.call('DEL', KEYS[1])
            end
            if ARGV[1] ~= nil and ARGV[1] ~= '' then
              redis.call('SREM', KEYS[2], ARGV[1])
            end
            if KEYS[3] ~= nil and KEYS[3] ~= '' then
              redis.call('DEL', KEYS[3])
            end
            return 1
            """;

    /**
     * KEYS[1]=indexKey
     * 返回设备 JSON 数组（可能含 false 空洞，由 Java 过滤）
     */
    public static final String LIST_ALL = """
            local ids = redis.call('SMEMBERS', KEYS[1])
            if #ids == 0 then
              return {}
            end
            local prefix = ARGV[1]
            local keys = {}
            for i, id in ipairs(ids) do
              keys[i] = prefix .. ':id:' .. id
            end
            return redis.call('MGET', unpack(keys))
            """;
}
