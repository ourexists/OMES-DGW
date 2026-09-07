package com.omes.dgw.deploy.configui;

import java.util.List;
import java.util.stream.Stream;

import static com.omes.dgw.deploy.configui.ConfigFieldDef.bool;
import static com.omes.dgw.deploy.configui.ConfigFieldDef.decimal;
import static com.omes.dgw.deploy.configui.ConfigFieldDef.integer;
import static com.omes.dgw.deploy.configui.ConfigFieldDef.password;
import static com.omes.dgw.deploy.configui.ConfigFieldDef.select;
import static com.omes.dgw.deploy.configui.ConfigFieldDef.string;

/**
 * config.properties 分区定义。
 * <ul>
 *   <li>builtin：系统配置中的通用运行参数</li>
 *   <li>plugin-redis / plugin-influx：插件管理二级窗口（连接管理 + 依赖插件的落库参数）</li>
 * </ul>
 */
public final class RuntimeConfigCatalog {

    private RuntimeConfigCatalog() {
    }

    public static List<ConfigSectionDef> all() {
        return List.of(
                dataStore(),
                cacheFlush(),
                collectRuntime(),
                observability(),
                statusControl(),
                redisPlugin(),
                influxPlugin()
        );
    }

    public static ConfigSectionDef byId(String id) {
        return all().stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static ConfigSectionDef dataStore() {
        return new ConfigSectionDef(
                "data-store",
                "数据存储",
                "本地业务库路径与结构迁移",
                "builtin",
                List.of(
                        string("SQLITE_PATH", "数据库文件路径", "./data/omes-dgw.db", "相对进程工作目录或绝对路径")
                                .withGroup("SQLite"),
                        bool("FLYWAY_ENABLED", "启用 Flyway 自动迁移", "true", "首次启动自动建表/升级")
                                .withGroup("SQLite")
                )
        );
    }

    private static ConfigSectionDef collectRuntime() {
        return new ConfigSectionDef(
                "collect-runtime",
                "采集运行",
                "调度、连接熔断、协议轮询与离线判定",
                "builtin",
                concat(
                        List.of(
                                integer("COLLECT_POLLING_SCHEDULER_POOL_SIZE", "轮询调度线程池大小", "8", "共享调度线程数")
                                        .withGroup("调度线程池")
                        ),
                        List.of(
                                integer("COLLECT_CONNECTION_MAX_CONSECUTIVE_FAILURES", "连续失败熔断次数", "5", "-1 表示不熔断")
                                        .withGroup("连接熔断"),
                                integer("COLLECT_CONNECTION_BACKOFF_INITIAL_MS", "首次半开等待(ms)", "10000", null)
                                        .withGroup("连接熔断"),
                                integer("COLLECT_CONNECTION_BACKOFF_MAX_MS", "退避上限(ms)", "300000", null)
                                        .withGroup("连接熔断"),
                                decimal("COLLECT_CONNECTION_BACKOFF_MULTIPLIER", "退避倍数", "2.0", null)
                                        .withGroup("连接熔断"),
                                integer("COLLECT_CONNECTION_MAX_OPEN_RETRIES", "放弃自动恢复次数", "10", null)
                                        .withGroup("连接熔断"),
                                integer("COLLECT_CONNECTION_BACKOFF_MAX_SCALE_STEPS", "退避上限递增步数", "10", null)
                                        .withGroup("连接熔断"),
                                decimal("COLLECT_CONNECTION_BACKOFF_MAX_SCALE_FACTOR", "每步递增比例", "0.1", null)
                                        .withGroup("连接熔断"),
                                integer("COLLECT_CONNECTION_BATCH_START_STAGGER_MS", "批量启用错开(ms)", "300", "0 表示不错开")
                                        .withGroup("连接熔断")
                        ),
                        List.of(
                                integer("COLLECT_PLC_CONNECTION_MAX_LEASE_SEC", "PLC 租约(秒)", "120", null)
                                        .withGroup("PLC 连接池"),
                                integer("COLLECT_PLC_CONNECTION_MAX_WAIT_SEC", "取连接最大等待(秒)", "20", null)
                                        .withGroup("PLC 连接池"),
                                integer("COLLECT_PLC_CONNECTION_MAX_IDLE_SEC", "连接最大空闲(秒)", "300", null)
                                        .withGroup("PLC 连接池")
                        ),
                        List.of(
                                integer("COLLECT_MODBUS_MAX_MERGE_GAP_REGISTERS", "寄存器合并间隙", "8", null)
                                        .withGroup("Modbus 轮询"),
                                integer("COLLECT_MODBUS_MAX_MERGE_GAP_BITS", "位合并间隙", "64", null)
                                        .withGroup("Modbus 轮询"),
                                integer("COLLECT_MODBUS_TAG_BATCH_SIZE", "单批点位数", "20", null)
                                        .withGroup("Modbus 轮询"),
                                integer("COLLECT_MODBUS_READ_MAX_RETRIES", "读失败重试次数", "3", null)
                                        .withGroup("Modbus 轮询"),
                                integer("COLLECT_MODBUS_READ_RETRY_DELAY_MS", "读重试间隔(ms)", "300", null)
                                        .withGroup("Modbus 轮询")
                        ),
                        List.of(
                                integer("COLLECT_OFFLINE_CHECK_DELAY_MS", "检查延迟(ms)", "30000", null)
                                        .withGroup("离线检测"),
                                integer("COLLECT_OFFLINE_TIMEOUT_MS", "离线超时(ms)", "30000", null)
                                        .withGroup("离线检测"),
                                integer("COLLECT_OFFLINE_SCHEDULER_POOL_SIZE", "检测线程池大小", "8", null)
                                        .withGroup("离线检测")
                        )
                )
        );
    }

    private static ConfigSectionDef observability() {
        return new ConfigSectionDef(
                "observability",
                "运维诊断",
                "采集诊断日志与本地运行指标采样",
                "builtin",
                concat(
                        List.of(
                                select("COLLECT_DIAGNOSTICS_POLL_TIMING_MODE", "轮询耗时日志模式", "on_issue",
                                        List.of("always", "on_issue", "debug_only", "off"),
                                        "always / on_issue / debug_only / off")
                                        .withGroup("采集诊断"),
                                bool("COLLECT_DIAGNOSTICS_POINT_VALUE_CHANGE", "点位丢值告警", "true", "有值变未读时打 WARN")
                                        .withGroup("采集诊断"),
                                bool("COLLECT_DIAGNOSTICS_MEMORY_LOG", "定期输出 JVM 内存", "true", null)
                                        .withGroup("采集诊断"),
                                integer("COLLECT_DIAGNOSTICS_MEMORY_LOG_INTERVAL_SEC", "内存日志间隔(秒)", "300", null)
                                        .withGroup("采集诊断")
                        ),
                        List.of(
                                integer("METRICS_SAMPLE_INTERVAL_SEC", "指标采样间隔(秒)", "10", null)
                                        .withGroup("运行指标"),
                                string("METRICS_INSTANCE_ID", "实例 ID", "", "可选；K8s 建议 Pod 名，空则 hostname-pid")
                                        .withGroup("运行指标"),
                                integer("COLLECT_METRICS_QUEUE_DEPTH_REFRESH_MS", "队列深度刷新(ms)", "30000", null)
                                        .withGroup("运行指标"),
                                integer("COLLECT_METRICS_OFFLINE_DURATION_REFRESH_MS", "离线时长刷新(ms)", "60000", null)
                                        .withGroup("运行指标")
                        )
                )
        );
    }

    private static ConfigSectionDef statusControl() {
        return new ConfigSectionDef(
                "status-control",
                "状态与控制",
                "在线事件落库与可写点位指令下发",
                "builtin",
                concat(
                        List.of(
                                bool("COLLECT_ONLINE_EVENT_ENABLED", "启用在线事件", "true", null)
                                        .withGroup("在线事件"),
                                integer("COLLECT_ONLINE_EVENT_BATCH_SIZE", "批量写入条数", "100", null)
                                        .withGroup("在线事件"),
                                integer("COLLECT_ONLINE_EVENT_FLUSH_INTERVAL_SEC", "定时刷写间隔(秒)", "30", null)
                                        .withGroup("在线事件"),
                                integer("COLLECT_ONLINE_EVENT_MAX_QUEUE_SIZE", "队列最大长度", "10000", null)
                                        .withGroup("在线事件")
                        ),
                        List.of(
                                bool("COLLECT_POINT_WRITE_ENABLED", "启用点位写入", "true", null)
                                        .withGroup("控制点位写入"),
                                integer("COLLECT_POINT_WRITE_IDLE_POLL_MS", "空闲轮询间隔(ms)", "500", null)
                                        .withGroup("控制点位写入")
                        )
                )
        );
    }

    /** 系统配置：仅通用后端开关；Redis 连接在插件二级窗口。 */
    private static ConfigSectionDef cacheFlush() {
        return new ConfigSectionDef(
                "cache-flush",
                "缓存与刷数",
                "选择实时缓存与采集队列后端；选 Redis 后在下方上传插件并配置连接",
                "builtin",
                List.of(
                        select("COLLECT_STORE_BACKEND", "存储后端", "caffeine",
                                List.of("caffeine", "redis"), "caffeine=进程内；redis=需下方上传插件并配置连接")
                                .withGroup("存储后端")
                )
        );
    }

    /** 插件二级窗口：Redis 连接管理（独属于插件）。 */
    private static ConfigSectionDef redisPlugin() {
        return new ConfigSectionDef(
                "plugin-redis",
                "Redis 连接",
                "缓存与刷数插件的连接参数；请先上传 omes-dgw-plugin-redis",
                "plugin-redis",
                List.of(
                        string("REDIS_HOST", "Redis Host", "localhost", null)
                                .withGroup("连接管理"),
                        integer("REDIS_PORT", "Redis Port", "6379", null)
                                .withGroup("连接管理"),
                        password("REDIS_PASSWORD", "Redis 密码", "留空保存表示不修改")
                                .withGroup("连接管理"),
                        integer("REDIS_DATABASE", "Database", "0", null)
                                .withGroup("连接管理"),
                        string("COLLECT_REDIS_KEY_PREFIX", "键前缀", "collect:device:realtime", "实时缓存与刷写队列共用前缀")
                                .withGroup("键空间")
                )
        );
    }

    /** 插件二级窗口：Influx 连接管理 + 历史/指标落库（依赖插件）。 */
    private static ConfigSectionDef influxPlugin() {
        return new ConfigSectionDef(
                "plugin-influx",
                "Influx 监控采集",
                "监控采集插件的连接与落库参数；请先上传 omes-dgw-plugin-influx",
                "plugin-influx",
                concat(
                        List.of(
                                bool("INFLUX_ENABLED", "启用 InfluxDB", "false", "无插件时请保持关闭")
                                        .withGroup("连接管理"),
                                string("INFLUX_URL", "Influx URL", "http://localhost:8086", null)
                                        .withGroup("连接管理"),
                                password("INFLUX_TOKEN", "API Token", "留空保存表示不修改")
                                        .withGroup("连接管理"),
                                string("INFLUX_ORG", "Org", "omes", null)
                                        .withGroup("连接管理"),
                                string("INFLUX_BUCKET", "Bucket", "dgw_metrics", null)
                                        .withGroup("连接管理")
                        ),
                        List.of(
                                bool("COLLECT_HISTORY_ENABLED", "启用快照历史落库", "true", "依赖上方 Influx 启用")
                                        .withGroup("历史落库"),
                                integer("COLLECT_HISTORY_BATCH_SIZE", "历史批量条数", "200", null)
                                        .withGroup("历史落库"),
                                integer("COLLECT_HISTORY_FLUSH_INTERVAL_SEC", "历史刷写间隔(秒)", "60", null)
                                        .withGroup("历史落库"),
                                integer("COLLECT_HISTORY_MAX_QUEUE_SIZE", "历史队列上限", "50000", null)
                                        .withGroup("历史落库"),
                                integer("COLLECT_HISTORY_QUERY_MAX_MINUTES", "历史查询最大跨度(分钟)", "10080", null)
                                        .withGroup("历史落库"),
                                integer("COLLECT_HISTORY_CHART_MAX_POINTS", "折线单序列最大点数", "500", null)
                                        .withGroup("历史落库"),
                                integer("COLLECT_HISTORY_MAX_WRITE_RETRIES", "写入最大重试", "3", null)
                                        .withGroup("历史落库")
                        ),
                        List.of(
                                bool("METRICS_PERSIST_INFLUX", "指标写入 Influx", "true", "依赖上方 Influx 启用")
                                        .withGroup("指标落库"),
                                integer("METRICS_INFLUX_BATCH_SIZE", "指标批量条数", "500", null)
                                        .withGroup("指标落库"),
                                integer("METRICS_INFLUX_FLUSH_INTERVAL_SEC", "指标刷写间隔(秒)", "60", null)
                                        .withGroup("指标落库"),
                                integer("METRICS_INFLUX_MAX_QUEUE_SIZE", "指标队列上限", "10000", null)
                                        .withGroup("指标落库"),
                                integer("METRICS_INFLUX_QUERY_MAX_MINUTES", "指标查询最大跨度(分钟)", "10080", null)
                                        .withGroup("指标落库")
                        )
                )
        );
    }

    @SafeVarargs
    private static List<ConfigFieldDef> concat(List<ConfigFieldDef>... parts) {
        return Stream.of(parts).flatMap(List::stream).toList();
    }
}
