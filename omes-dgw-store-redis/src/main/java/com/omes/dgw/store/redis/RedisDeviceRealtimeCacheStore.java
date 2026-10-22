package com.omes.dgw.store.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeCacheStore;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.cache.RealtimeCacheInfrastructureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis + Lua 实时快照存储。
 */
@Slf4j
public class RedisDeviceRealtimeCacheStore implements DeviceRealtimeCacheStore {

    private static final DateTimeFormatter LAST_UPDATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    private final DefaultRedisScript<Long> saveScript;
    private final DefaultRedisScript<Long> casScript;
    private final DefaultRedisScript<Long> removeScript;
    private final DefaultRedisScript<List> listAllScript;

    @SuppressWarnings("rawtypes")
    public RedisDeviceRealtimeCacheStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AppProperties appProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        String prefix = appProperties.getCollect().getRedisKeyPrefix();
        this.keyPrefix = StringUtils.hasText(prefix) ? prefix.trim() : "omes:dwg:device:realtime";

        this.saveScript = new DefaultRedisScript<>();
        this.saveScript.setScriptText(DeviceRealtimeCacheLuaScripts.SAVE);
        this.saveScript.setResultType(Long.class);

        this.casScript = new DefaultRedisScript<>();
        this.casScript.setScriptText(DeviceRealtimeCacheLuaScripts.SAVE_IF_ONLINE_MATCHES);
        this.casScript.setResultType(Long.class);

        this.removeScript = new DefaultRedisScript<>();
        this.removeScript.setScriptText(DeviceRealtimeCacheLuaScripts.REMOVE);
        this.removeScript.setResultType(Long.class);

        this.listAllScript = new DefaultRedisScript<>();
        this.listAllScript.setScriptText(DeviceRealtimeCacheLuaScripts.LIST_ALL);
        this.listAllScript.setResultType(List.class);

        log.info("设备 Redis 实时缓存后端已初始化 keyPrefix={}", this.keyPrefix);
    }

    @Override
    public void save(DeviceRealtimeObj snapshot, String json) {
        String deviceId = snapshot.getDeviceId();
        String payload = json != null ? json : writeJson(snapshot);
        String previousCodeKey = resolvePreviousCodeKey(deviceId, snapshot.getDeviceCode());
        try {
            redisTemplate.execute(
                    saveScript,
                    List.of(deviceKey(deviceId), indexKey(), codeKeyOrEmpty(snapshot.getDeviceCode())),
                    payload,
                    deviceId,
                    previousCodeKey
            );
        } catch (Exception e) {
            throw wrapInfra(e, "save deviceId=" + deviceId);
        }
    }

    @Override
    public long saveIfOnlineStateMatches(
            DeviceRealtimeObj snapshot,
            String json,
            boolean expectCurrentOnline,
            LocalDateTime expectLastUpdate
    ) {
        String deviceId = snapshot.getDeviceId();
        String payload = json != null ? json : writeJson(snapshot);
        String previousCodeKey = resolvePreviousCodeKey(deviceId, snapshot.getDeviceCode());
        boolean checkLut = expectLastUpdate != null;
        String expectLut = checkLut ? LAST_UPDATE_FMT.format(expectLastUpdate) : "";
        try {
            Long result = redisTemplate.execute(
                    casScript,
                    List.of(deviceKey(deviceId), indexKey(), codeKeyOrEmpty(snapshot.getDeviceCode())),
                    payload,
                    deviceId,
                    expectCurrentOnline ? "1" : "0",
                    checkLut ? "1" : "0",
                    expectLut,
                    previousCodeKey
            );
            if (result == null) {
                return DeviceRealtimeCache.CAS_SAVE_MISMATCH;
            }
            return result;
        } catch (Exception e) {
            throw wrapInfra(e, "cas deviceId=" + deviceId);
        }
    }

    @Override
    public DeviceRealtimeObj getByDeviceId(String deviceId) {
        try {
            String json = redisTemplate.opsForValue().get(deviceKey(deviceId));
            return readJson(json);
        } catch (Exception e) {
            throw wrapInfra(e, "getByDeviceId=" + deviceId);
        }
    }

    @Override
    public DeviceRealtimeObj getByDeviceCode(String deviceCode) {
        try {
            String deviceId = redisTemplate.opsForValue().get(codeKey(deviceCode));
            if (!StringUtils.hasText(deviceId)) {
                return null;
            }
            return getByDeviceId(deviceId);
        } catch (RealtimeCacheInfrastructureException e) {
            throw e;
        } catch (Exception e) {
            throw wrapInfra(e, "getByDeviceCode=" + deviceCode);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DeviceRealtimeObj> listAll() {
        try {
            List<Object> rawList = redisTemplate.execute(
                    listAllScript,
                    List.of(indexKey()),
                    keyPrefix
            );
            if (rawList == null || rawList.isEmpty()) {
                return Collections.emptyList();
            }
            List<DeviceRealtimeObj> result = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                if (item == null) {
                    continue;
                }
                DeviceRealtimeObj obj = readJson(String.valueOf(item));
                if (obj != null) {
                    result.add(obj);
                }
            }
            return result;
        } catch (Exception e) {
            throw wrapInfra(e, "listAll");
        }
    }

    @Override
    public void remove(String deviceId, String deviceCode) {
        try {
            String resolvedId = deviceId;
            String resolvedCode = deviceCode;
            if (!StringUtils.hasText(resolvedId) && StringUtils.hasText(resolvedCode)) {
                resolvedId = redisTemplate.opsForValue().get(codeKey(resolvedCode));
            }
            if (StringUtils.hasText(resolvedId) && !StringUtils.hasText(resolvedCode)) {
                String json = redisTemplate.opsForValue().get(deviceKey(resolvedId));
                DeviceRealtimeObj existing = readJson(json);
                if (existing != null) {
                    resolvedCode = existing.getDeviceCode();
                }
            }
            redisTemplate.execute(
                    removeScript,
                    List.of(
                            StringUtils.hasText(resolvedId) ? deviceKey(resolvedId) : "",
                            indexKey(),
                            codeKeyOrEmpty(resolvedCode)
                    ),
                    StringUtils.hasText(resolvedId) ? resolvedId : ""
            );
        } catch (Exception e) {
            throw wrapInfra(e, "remove deviceId=" + deviceId + ", code=" + deviceCode);
        }
    }

    private String resolvePreviousCodeKey(String deviceId, String newCode) {
        try {
            String existingJson = redisTemplate.opsForValue().get(deviceKey(deviceId));
            DeviceRealtimeObj existing = readJson(existingJson);
            if (existing == null || !StringUtils.hasText(existing.getDeviceCode())) {
                return "";
            }
            String oldCode = existing.getDeviceCode();
            if (StringUtils.hasText(newCode) && oldCode.equals(newCode)) {
                return "";
            }
            return codeKey(oldCode);
        } catch (Exception e) {
            if (RedisInfrastructureFailureDetector.isInfrastructureFailure(e)) {
                throw wrapInfra(e, "resolvePreviousCode deviceId=" + deviceId);
            }
            return "";
        }
    }

    private String deviceKey(String deviceId) {
        return keyPrefix + ":id:" + deviceId;
    }

    private String indexKey() {
        return keyPrefix + ":ids";
    }

    private String codeKey(String deviceCode) {
        return keyPrefix + ":code:" + deviceCode;
    }

    private String codeKeyOrEmpty(String deviceCode) {
        return StringUtils.hasText(deviceCode) ? codeKey(deviceCode) : "";
    }

    private String writeJson(DeviceRealtimeObj snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化实时缓存失败", e);
        }
    }

    private DeviceRealtimeObj readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DeviceRealtimeObj.class);
        } catch (JsonProcessingException e) {
            log.warn("反序列化实时缓存失败: {}", e.getMessage());
            return null;
        }
    }

    private static RealtimeCacheInfrastructureException wrapInfra(Exception e, String action) {
        if (e instanceof RealtimeCacheInfrastructureException infra) {
            return infra;
        }
        if (RedisInfrastructureFailureDetector.isInfrastructureFailure(e)) {
            return new RealtimeCacheInfrastructureException(
                    "Redis 实时缓存基础设施失败: " + action, e);
        }
        if (e instanceof RuntimeException runtime) {
            throw runtime;
        }
        return new RealtimeCacheInfrastructureException(
                "Redis 实时缓存失败: " + action, e);
    }
}
