package com.omes.dgw.deploy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "保存插件配置（Redis 段与/或 Influx 段）")
public class StorePluginConfigRequest {

    /** 保存目标：redis | influx | both；缺省 both（兼容旧前端只传 Redis 字段） */
    @Pattern(regexp = "redis|influx|both", message = "section 只能是 redis、influx 或 both")
    private String section = "both";

    // --- Redis ---
    @Pattern(regexp = "caffeine|redis", message = "storeBackend 只能是 caffeine 或 redis")
    private String storeBackend;

    private String redisHost;

    @Min(1)
    @Max(65535)
    private Integer redisPort;

    /** null 表示不修改原密码；空字符串表示清空密码 */
    private String redisPassword;

    @Min(0)
    @Max(15)
    private Integer redisDatabase;

    private String redisKeyPrefix;

    // --- Influx ---
    private Boolean influxEnabled;
    private String influxUrl;
    /** null 表示不修改原 token；空字符串表示清空 */
    private String influxToken;
    private String influxOrg;
    private String influxBucket;
    private Boolean historyEnabled;
    private Boolean persistMetrics;
}
