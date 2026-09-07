package com.omes.dgw.deploy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "插件管理状态（Redis 存储 + Influx 时序）")
public class StorePluginStatusVo {

    @Schema(description = "plugins 目录绝对路径")
    private String pluginsDir;

    @Schema(description = "config.properties 绝对路径")
    private String configFile;

    private RedisSection redis;
    private InfluxSection influx;

    @Data
    @Builder
    @Schema(description = "Redis 存储插件状态")
    public static class RedisSection {
        private String activeStoreBackend;
        private String configuredStoreBackend;
        private boolean onClasspath;
        private boolean jarPresent;
        private List<String> jarNames;
        private boolean restartRequired;
        private String redisHost;
        private Integer redisPort;
        private boolean redisPasswordSet;
        private Integer redisDatabase;
        private String redisKeyPrefix;
        private String hint;
    }

    @Data
    @Builder
    @Schema(description = "Influx 时序插件状态")
    public static class InfluxSection {
        /** 当前进程生效的 enabled */
        private boolean enabled;
        /** config.properties 中配置的 enabled（可能尚未重启） */
        private boolean configuredEnabled;
        private String url;
        private String org;
        private String bucket;
        private boolean tokenSet;
        private boolean historyEnabled;
        private boolean persistMetrics;
        private boolean onClasspath;
        private boolean jarPresent;
        private List<String> jarNames;
        private boolean restartRequired;
        private String hint;
    }
}
