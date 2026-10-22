package com.omes.dgw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 门户模式：{@code app.portal.mode=memory|edge}�?     * memory=全程内存且通常 {@code app.collect.enabled=false}；edge=Redis+Influx 完整采集�?     */
    private Portal portal = new Portal();
    private Collect collect = new Collect();
    private Influx influx = new Influx();
    private Security security = new Security();
    private List<MenuItem> menus = new ArrayList<>();

    @Data
    public static class Portal {
        /** memory | edge，对�?{@link com.omes.dgw.portal.PortalMode} */
        private String mode = "box";
    }

    @Data
    public static class Collect {
        /**
         * 是否启动协议采集轮询（DeviceLifecycleManager.startProtocol 等）�?         * 盒子门户应为 false；edge 默认�?true。缺�?true 以兼容旧配置�?         */
        private boolean enabled = true;
        /** 采集监控类型：none（无）或 influxdb�?*/
        private String monitorType = "none";
        /**
         * 实时缓存与有界队列统一后端：{@code caffeine}（本地，默认）或 {@code redis}
         * （由 omes-dgw-store-redis 编译期依赖提供）�?         */
        private String storeBackend = "caffeine";
        /** Redis 实时缓存键前缀（仅 store-backend=redis�?*/
        private String redisKeyPrefix = "omes:dwg:device:realtime";
        /** 协议轮询 cron 触发共用调度线程池大�?*/
        private int pollingSchedulerPoolSize = 4;
        /**
         * 单轮采集耗时超过 cron 间隔时会被防重入门禁连续跳过；达到该次数后打 WARN 并可通过指标告警�?         */
        private int pollSkipWarnThreshold = 10;
        private Metrics metrics = new Metrics();
        private History history = new History();
        private Offline offline = new Offline();
        private OnlineEvent onlineEvent = new OnlineEvent();
        private PointWrite pointWrite = new PointWrite();
        private Connection connection = new Connection();
        private ModbusPolling modbusPolling = new ModbusPolling();
        private SlmpPolling slmpPolling = new SlmpPolling();
        private FinsPolling finsPolling = new FinsPolling();
        private CollectDiagnostics diagnostics = new CollectDiagnostics();

        @Data
        public static class CollectDiagnostics {
            /**
             * 轮询耗时分解日志：always=每轮 INFO；on_issue=失败/降级/慢轮�?丢值时 INFO；debug_only；off�?             */
            private String pollTimingMode = "on_issue";
            /** 点位从有值变为无�?未读时打 WARN */
            private boolean pointValueChangeLogEnabled = true;
            /** 是否定期输出 JVM 内存�?GC 统计 */
            private boolean memoryLogEnabled = true;
            /** 内存/GC 日志间隔（秒�?*/
            private int memoryLogIntervalSec = 300;
        }
    }

    @Data
    public static class Connection {
        /**
         * 轮询启动后连续无法建�?读取连接的次数达到该值后，停止轮询并将连接状态置为未连接�?         * 设为 -1 表示不启用熔断，永不断连�?         */
        private int maxConsecutiveFailures = 5;
        /** 熔断后首次半开重试等待时间（毫秒） */
        private long backoffInitialMs = 10_000L;
        /** 退避等待上限（毫秒�?*/
        private long backoffMaxMs = 300_000L;
        /** 退避倍数（第 n 次打开熔断等待 = initial * multiplier^(n-1)�?capped�?*/
        private double backoffMultiplier = 2.0;
        /**
         * 熔断打开次数超过该值后进入放弃状态，不再调度自动半开恢复（仅人工「开启连接」）�?         */
        private int maxOpenRetries = 10;
        /**
         * 退避上限递增步数：有效上�?= backoffMaxMs × (1 + min(openCount, steps) × factor)，默认最�?600s�?         */
        private int backoffMaxScaleSteps = 10;
        /** �?{@link #backoffMaxScaleSteps} 配合的每步递增比例�?.1 表示 openCount=10 时上�?×2�?*/
        private double backoffMaxScaleFactor = 0.1;
        /**
         * PLC4X 缓存连接最大租约（秒）。单轮多�?Modbus 读可能超�?30s，默�?120�?         */
        private int plcConnectionMaxLeaseSec = 120;
        /** 等待从缓存获取连接的最长时间（秒） */
        private int plcConnectionMaxWaitSec = 20;
        /**
         * 缓存连接最大空闲时间（秒），超时关闭并从缓存移除，缓解 Modbus 断线后僵死连接（PLC4X 0.13+）�?         */
        private int plcConnectionMaxIdleSec = 300;
        /**
         * 批量启用 / 启动恢复时，相邻设备首次建连的错开间隔（毫秒）�?         * 避免一次性同时打开大量 TCP 连接打满 PLC、本机端口或调度线程�? 表示不错开�?         */
        private long batchStartStaggerMs = 300L;
    }

    @Data
    public static class ModbusPolling {
        /**
         * 合并块读时寄存器区允许的最大地址空洞（PDU 寄存器数）；超过则切块，避免读未配置空洞�?         */
        private int maxMergeGapRegisters = 8;
        /** 线圈/离散量区合并时允许的最大位地址空洞 */
        private int maxMergeGapBits = 64;
        /**
         * 单次 {@link org.apache.plc4x.java.api.messages.PlcReadRequest} 最多合并的块数�?tag �?         *（chunk 微批、fallback 点读批、块读降级批；与 ModScan 默认 20 对齐）�?         */
        private int tagBatchSize = 20;
        /** �?批读失败时的重试次数（整段重试计 1 次逻辑读指标） */
        private int readMaxRetries = 3;
        /** �?批读重试间隔（毫秒） */
        private long readRetryDelayMs = 300L;
    }

    @Data
    public static class SlmpPolling {
        /** 字设备合并读时允许的最大地址空洞（字�?*/
        private int maxMergeGapWords = 8;
        /** 位设备合并读时允许的最大位地址空洞 */
        private int maxMergeGapBits = 64;
        /**
         * 单次 0401 字单位批量读/写上限（MC 协议 Q/L/iQ 最�?960，响应数�?1920 字节）�?         * 配置值会�?{@link com.omes.dgw.protocol.slmp.SlmpLimits#MAX_WORDS_PER_REQUEST} 封顶�?         */
        private int maxWordsPerRequest = 960;
        /**
         * 单次 0401 位单位批量读/写上限（Q/L 系列保守 3584，iQ-R 可达 7168）�?         */
        private int maxBitsPerRequest = 3584;
    }

    @Data
    public static class FinsPolling {
        /** 字区（DM 等）合并读时允许的最大地址空洞（字�?*/
        private int maxMergeGapWords = 8;
        /**
         * 单次 FINS 字读上限（工程推�?100~300；协议硬上限�?999）�?         */
        private int maxWordsPerRead = 250;
        /**
         * 单次 FINS 字写上限（工程推�?20~100）�?         */
        private int maxWordsPerWrite = 80;
    }

    @Data
    public static class PointWrite {
        /** 是否启用控制点位写入指令顺序执行 */
        private boolean enabled = true;
        /** 无待执行指令时，执行线程空闲轮询间隔（毫秒） */
        private long idlePollIntervalMs = 500L;
    }

    @Data
    public static class OnlineEvent {
        /** 是否采集在线态变更并批量写入 SQLite */
        private boolean enabled = true;
        private int batchSize = 100;
        private int flushIntervalSec = 30;
        private int maxQueueSize = 10_000;
    }

    @Data
    public static class Offline {
        private boolean enabled = true;
        /** 成功写入实时缓存后，延迟多久执行离线检查（毫秒�?*/
        private long checkDelayMs = 30_000L;
        /** 最后更新时间距当前超过该值则判为离线（毫秒） */
        private long offlineTimeoutMs = 30_000L;
        /** 共享调度线程池大小（按设备复用任务，非每设备一线程�?*/
        private int schedulerPoolSize = 4;
    }

    @Data
    public static class History {
        /** 是否将新实时快照同步入队并批量写�?Influx（需 app.collect.monitor-type=influxdb�?*/
        private boolean enabled = false;
        /** 触发批量写入的队列长度阈�?*/
        private int batchSize = 200;
        /** 未满批量大小时，定时刷写间隔（秒�?*/
        private int flushIntervalSec = 60;
        /** 队列最大长度，超出时优先触发刷�?*/
        private int maxQueueSize = 50_000;
        /** �?Influx 查询快照折线图时的最大时间范围（分钟�?*/
        private int queryMaxMinutes = 7 * 24 * 60;
        /** 折线图单序列最大返回点数；超出时按时间跨度�?Influx �?aggregateWindow 降采�?*/
        private int chartMaxPointsPerSeries = 500;
        /** 单条快照 Influx 写入失败后的最大重试次数，超过则丢弃并记丢失日�?*/
        private int maxWriteRetries = 3;
    }

    @Data
    public static class Metrics {
        /** 指标采样间隔（秒），用于前端图表时序 */
        private int sampleIntervalSec = 10;
        /** 内存环形缓冲保留时长（分钟），仅影响未走 Influx 时的本地缓存，不限制 chart �?minutes 查询参数 */
        private int historyMinutes = 60;
        /** 是否将采样指标同步入队并批量写入 Influx（需 app.collect.monitor-type=influxdb�?*/
        private boolean persistToInflux = false;
        /** 触发批量写入 Influx 的队列条数阈�?*/
        private int influxBatchSize = 500;
        /** 未满批时定时刷写间隔（秒�?*/
        private int influxFlushIntervalSec = 60;
        /** 指标队列最大长�?*/
        private int influxMaxQueueSize = 10_000;
        /** �?Influx 查询图表时的最大时间范围（分钟�?*/
        private int influxQueryMaxMinutes = 7 * 24 * 60;
        /** 实例标识（可选）；未配置时使�?hostname-pid */
        private String instanceId;
        /** 刷新本地队列深度 Gauge 的间隔（毫秒�?*/
        private long queueDepthRefreshMs = 30_000L;
        /** 刷新设备离线时长 Gauge 的间隔（毫秒�?*/
        private long offlineDurationRefreshMs = 60_000L;
    }

    @Data
    public static class Influx {
        /** InfluxDB HTTP API 地址，如 http://localhost:8086 */
        private String url = "http://localhost:8086";
        private String token = "";
        private String org = "ktr";
        private String bucket = "collect_metrics";
        private int connectTimeoutMs = 10_000;
        private int readTimeoutMs = 10_000;
        private int writeTimeoutMs = 10_000;
    }

    @Data
    public static class Security {
        private Jwt jwt = new Jwt();
        private List<AdminUser> users = new ArrayList<>();
    }

    @Data
    public static class Jwt {
        /** HS256 密钥；至少 32 字节。门户 yml / 环境变量可覆盖。 */
        private String secret = "mGX8dXtX3/t8xucjIpA9gr4y+tGe/bzkDepbu72BrrFwMybHYasrzD5VMfJ8ilef";
        private long expirationMs = 86_400_000L;
    }

    @Data
    public static class AdminUser {
        private String username;
        private String password;
        private String nickname;
        private List<String> roles = new ArrayList<>();
        private boolean enabled = true;
    }

    @Data
    public static class MenuItem {
        private String id;
        private String title;
        private String path;
        private String icon;
        private List<String> roles = new ArrayList<>();
        private List<MenuItem> children = new ArrayList<>();
    }
}
