# OMES设备软边缘网关 - 服务端资源配置说明

本文说明 `src/main/resources` 下各文件的用途、**部署时可改的配置**与 **Modbus 轮询运维调参**。面向现场部署、调参与排障。

与 **113-manage** 同机/同域多服务部署（端口与 CORS）见运维部署文档。默认存储后端为 **Caffeine + 本地队列**；可选 Redis 需 `-Pcache-redis` 打包并设 `COLLECT_STORE_BACKEND=redis`。

---

## 目录结构

| 路径 | 用途 |
|------|------|
| `application.yml` | Spring Boot 主配置；含 `${变量名:默认值}` 占位，可被外部 properties / 环境变量覆盖 |
| `config/config.properties`（**包外**，见下文） | 部署目录下的外部配置模板；与 yml 占位项一一对应 |
| `log4j2-spring.xml` | 日志输出、滚动策略与包级别日志级别 |
| `mapper/*.xml` | MyBatis-Plus 自定义 SQL |
| `db/migration/` | Flyway SQLite DDL |

---

## 1. 外部部署配置

### 1.1 加载方式

`application.yml` 通过 Spring Boot 3 的 `spring.config.import` 加载包外文件（**可选**，不存在时不报错）：

```yaml
spring:
  config:
    import: optional:file:config/config.properties
```

推荐目录布局：

```text
部署目录/
  omes-dgw-server.jar
  config/
    config.properties    # 从仓库 config/config.properties 复制并按环境修改
  data/
    omes-dgw.db          # SQLite 业务库（首次启动由 Flyway 建表）
  logs/                  # 默认日志目录（可由 LOG_PATH 指定）
```

也可改用 **环境变量** 或 **JVM 系统属性** 覆盖同名占位项，优先级高于 `config.properties`。

### 1.2 配置项一览

下列变量均在仓库 `config/config.properties` 中有注释说明；修改后 **重启** 服务生效。

#### 基础设施

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `COLLECT_STORE_BACKEND` | caffeine | `app.collect.store-backend` | `caffeine` 本地；`redis` 需 `-Pcache-redis` 打包 |
| `COLLECT_REDIS_KEY_PREFIX` | omes:dwg:device:realtime | `app.collect.redis-key-prefix` | 仅 redis 模式：实时缓存键前缀 |
| `REDIS_HOST` | localhost | `spring.data.redis.host` | 仅 redis 模式 |
| `REDIS_PORT` | 6379 | `spring.data.redis.port` | 仅 redis 模式 |
| `REDIS_PASSWORD` | （空） | `spring.data.redis.password` | 仅 redis 模式 |
| `REDIS_DATABASE` | 0 | `spring.data.redis.database` | 仅 redis 模式 |
| `SQLITE_PATH` | ./data/omes-dgw.db | `spring.datasource.url` | SQLite 库文件路径 |
| `FLYWAY_ENABLED` | true | `spring.flyway.enabled` | 是否自动执行迁移 |

#### 采集调度与连接熔断

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `COLLECT_POLLING_SCHEDULER_POOL_SIZE` | 4 | `app.collect.polling-scheduler-pool-size` | 全设备 cron 共用调度线程池 |
| `COLLECT_DIAGNOSTICS_POLL_TIMING_MODE` | on_issue | `app.collect.diagnostics.poll-timing-mode` | 轮询耗时分解：always/on_issue/debug_only/off |
| `COLLECT_DIAGNOSTICS_POINT_VALUE_CHANGE` | true | `app.collect.diagnostics.point-value-change-log-enabled` | 点位有值→无值 WARN |
| `COLLECT_DIAGNOSTICS_MEMORY_LOG` | true | `app.collect.diagnostics.memory-log-enabled` | 定期 JVM 内存/GC 日志 |
| `COLLECT_DIAGNOSTICS_MEMORY_LOG_INTERVAL_SEC` | 300 | `app.collect.diagnostics.memory-log-interval-sec` | 内存日志间隔（秒） |
| `COLLECT_CONNECTION_MAX_CONSECUTIVE_FAILURES` | 5 | `app.collect.connection.max-consecutive-failures` | 连续整轮失败熔断次数；`-1` 不熔断 |
| `COLLECT_CONNECTION_BACKOFF_INITIAL_MS` | 10000 | `app.collect.connection.backoff-initial-ms` | 熔断后首次半开等待 |
| `COLLECT_CONNECTION_BACKOFF_MAX_MS` | 300000 | `app.collect.connection.backoff-max-ms` | 退避基础上限（毫秒） |
| `COLLECT_CONNECTION_BACKOFF_MULTIPLIER` | 2.0 | `app.collect.connection.backoff-multiplier` | 退避倍数 |
| `COLLECT_CONNECTION_MAX_OPEN_RETRIES` | 10 | `app.collect.connection.max-open-retries` | 超过后放弃自动半开恢复 |
| `COLLECT_CONNECTION_BACKOFF_MAX_SCALE_STEPS` | 10 | `app.collect.connection.backoff-max-scale-steps` | 退避上限递增步数 |
| `COLLECT_CONNECTION_BACKOFF_MAX_SCALE_FACTOR` | 0.1 | `app.collect.connection.backoff-max-scale-factor` | 每步递增比例（×2 封顶约 600s） |
| `COLLECT_PLC_CONNECTION_MAX_LEASE_SEC` | 120 | `app.collect.connection.plc-connection-max-lease-sec` | PLC4X 连接最大租约（秒） |
| `COLLECT_PLC_CONNECTION_MAX_WAIT_SEC` | 20 | `app.collect.connection.plc-connection-max-wait-sec` | 从池取连接最长等待（秒） |
| `COLLECT_PLC_CONNECTION_MAX_IDLE_SEC` | 300 | `app.collect.connection.plc-connection-max-idle-sec` | 缓存连接最大空闲（秒），超时关闭 |

#### Modbus 轮询

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `COLLECT_MODBUS_MAX_MERGE_GAP_REGISTERS` | 8 | `app.collect.modbus-polling.max-merge-gap-registers` | 寄存器区合并允许的最大地址空洞 |
| `COLLECT_MODBUS_MAX_MERGE_GAP_BITS` | 64 | `app.collect.modbus-polling.max-merge-gap-bits` | 线圈/离散量区合并允许的最大位空洞 |
| `COLLECT_MODBUS_TAG_BATCH_SIZE` | 20 | `app.collect.modbus-polling.tag-batch-size` | 单批读 tag/块数上限（chunk、fallback、降级批） |
| `COLLECT_MODBUS_READ_MAX_RETRIES` | 3 | `app.collect.modbus-polling.read-max-retries` | 块/批读失败重试次数 |
| `COLLECT_MODBUS_READ_RETRY_DELAY_MS` | 300 | `app.collect.modbus-polling.read-retry-delay-ms` | 读重试间隔（毫秒） |

#### 设备离线检测

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `COLLECT_OFFLINE_CHECK_DELAY_MS` | 30000 | `app.collect.offline.check-delay-ms` | 实时数据写入 Redis 后延迟检查 |
| `COLLECT_OFFLINE_TIMEOUT_MS` | 30000 | `app.collect.offline.offline-timeout-ms` | 超时未更新则判离线 |
| `COLLECT_OFFLINE_SCHEDULER_POOL_SIZE` | 4 | `app.collect.offline.scheduler-pool-size` | 离线检测调度线程池 |

#### 采集指标（Influx）

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `METRICS_PERSIST_INFLUX` | true | `app.collect.metrics.persist-to-influx` | 是否将采样指标写入 Influx |
| `METRICS_INFLUX_BATCH_SIZE` | 500 | `app.collect.metrics.influx-batch-size` | 批量刷写阈值 |
| `METRICS_INFLUX_FLUSH_INTERVAL_SEC` | 60 | `app.collect.metrics.influx-flush-interval-sec` | 定时刷写间隔 |
| `METRICS_INFLUX_MAX_QUEUE_SIZE` | 10000 | `app.collect.metrics.influx-max-queue-size` | 待写队列上限 |
| `METRICS_INFLUX_QUERY_MAX_MINUTES` | 10080 | `app.collect.metrics.influx-query-max-minutes` | 图表查询最大时间跨度（分钟） |

#### 实时快照历史（Influx）

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `COLLECT_HISTORY_ENABLED` | true | `app.collect.history.enabled` | 是否将实时快照批量写入 Influx |
| `COLLECT_HISTORY_BATCH_SIZE` | 200 | `app.collect.history.batch-size` | 批量刷写阈值 |
| `COLLECT_HISTORY_FLUSH_INTERVAL_SEC` | 60 | `app.collect.history.flush-interval-sec` | 定时刷写间隔 |
| `COLLECT_HISTORY_MAX_QUEUE_SIZE` | 50000 | `app.collect.history.max-queue-size` | 待写队列上限 |
| `COLLECT_HISTORY_QUERY_MAX_MINUTES` | 10080 | `app.collect.history.query-max-minutes` | 历史/折线图查询最大跨度 |
| `COLLECT_HISTORY_CHART_MAX_POINTS` | 500 | `app.collect.history.chart-max-points-per-series` | 折线图单序列最大点数 |
| `COLLECT_HISTORY_MAX_WRITE_RETRIES` | 3 | `app.collect.history.max-write-retries` | Influx 写入失败最大重试，超限丢弃 |

#### 设备在线事件（SQLite）

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `COLLECT_ONLINE_EVENT_ENABLED` | true | `app.collect.online-event.enabled` | 是否记录在线/离线变更 |
| `COLLECT_ONLINE_EVENT_BATCH_SIZE` | 100 | `app.collect.online-event.batch-size` | 批量入库阈值 |
| `COLLECT_ONLINE_EVENT_FLUSH_INTERVAL_SEC` | 30 | `app.collect.online-event.flush-interval-sec` | 定时刷写间隔 |
| `COLLECT_ONLINE_EVENT_MAX_QUEUE_SIZE` | 10000 | `app.collect.online-event.max-queue-size` | 待写队列上限 |

#### 控制点位写入

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `COLLECT_POINT_WRITE_ENABLED` | true | `app.collect.point-write.enabled` | 是否执行控制写入任务 |
| `COLLECT_POINT_WRITE_IDLE_POLL_MS` | 500 | `app.collect.point-write.idle-poll-interval-ms` | 无指令时空闲轮询间隔 |

#### InfluxDB 2.x

| 环境变量 | 默认 | 对应 yml 路径 | 说明 |
|----------|------|---------------|------|
| `COLLECT_MONITOR_TYPE` | none | `app.collect.monitor-type` | 采集监控类型：none（无）、influxdb（需导入 `omes-dgw-plugin-influx`） |
| `INFLUX_URL` | http://localhost:8086 | `app.influx.url` | HTTP API 地址 |
| `INFLUX_TOKEN` | （见 yml） | `app.influx.token` | API Token |
| `INFLUX_ORG` | omes | `app.influx.org` | 组织名 |
| `INFLUX_BUCKET` | collect_metrics | `app.influx.bucket` | Bucket（指标与快照历史共用） |

> **依赖关系**：`COLLECT_HISTORY_ENABLED` 与 `METRICS_PERSIST_INFLUX` 均需 `COLLECT_MONITOR_TYPE=influxdb` 且已加载 Influx 插件才会实际落库。无插件时请保持 `COLLECT_MONITOR_TYPE=none`。

### 1.3 配置示例

关闭实时快照写 Influx：

```properties
COLLECT_HISTORY_ENABLED=false
```

Docker / K8s 环境变量批量注入：

```bash
COLLECT_POLLING_SCHEDULER_POOL_SIZE=8
COLLECT_PLC_CONNECTION_MAX_LEASE_SEC=180
COLLECT_MODBUS_MAX_MERGE_GAP_REGISTERS=8
INFLUX_URL=http://influx:8086
INFLUX_TOKEN=your-token
```

### 1.4 仍须在 yml 内维护的项

以下配置 **未** 列入 `config.properties`，需直接改 `application.yml` 或另行打包：

- `server.port`（默认 12010）
- `app.security`（JWT 密钥、内置用户与角色）
- `app.menus`（管理平台菜单）
- `app.collect.metrics.sample-interval-sec`、`history-minutes` 等无环境变量占位的细项
- `management.endpoints`（Actuator 暴露端点）

生产环境请修改默认 JWT 密钥与用户密码，勿使用仓库示例值。

---

## 2. Docker Compose 部署

- **研发打包**：`docker/build/` + `docker/生成实施包.bat` → 产出 `docker/release/omes-dgw-deploy/`
- **实施部署**：仅交付 `omes-dgw-deploy/`（含 `images/*.tar`、`03-启动.bat`，无源码）  
**SQLite** 以本地库文件持久化（默认 `./data/omes-dgw.db`，容器内 `/app/data/omes-dgw.db`），首次启动时由应用 **Flyway** 自动建表（`src/main/resources/db/migration/`）。**外用 InfluxDB** 执行 `02-初始化InfluxDB.bat`（见运维 InfluxDB 说明）。

### 2.1 前置条件

- Docker Engine 24+ 与 Docker Compose v2
- 确保 `MOUNT_PATH/omes-dgw/data` 可写（SQLite 库文件）
- 实施包内 `.env` 配置 `MOUNT_PATH` 等（从 `.env.example` 复制）

### 2.2 研发打包（Windows）

```bat
cd ..\docker\build
copy .env.example .env
打包全部.bat
cd ..
生成实施包.bat
```

将 `release\omes-dgw-deploy\` 整体交给实施。

### 2.3 实施启动（Windows）

```bat
cd omes-dgw-deploy
01-初始化环境配置.bat
REM 编辑 .env 后
03-启动.bat
```

### 2.4 服务与端口

| 服务 | 容器名 | 默认宿主机端口 |
|------|--------|----------------|
| omes-dgw-server | omes-dgw-server | 12010 |
| omes-dgw-web | omes-dgw-web | 12011 |
| InfluxDB | influxdb2.7 | 8086 |
| Redis | redis | 6379 |

- 配置 / 日志 / 监控：宿主机 `{MOUNT_PATH}/collection/`、`{MOUNT_PATH}/nginx/`
- 镜像包：`images/*.tar`（`03-启动.bat` 自动 `docker load`）

### 2.5 停止与排障

```bat
04-停止.bat
docker compose logs -f omes-dgw-server
```

| 现象 | 处理 |
|------|------|
| 应用启动失败、SQLite 无法写库 | 确认 `SQLITE_PATH` 父目录可写；容器需挂载 `omes-dgw/data` |
| Influx 写入失败 | `INFLUX_ADMIN_TOKEN` 与 Influx 初始化一致 |
| Modbus 采不到现场设备 | 容器需能路由到 PLC 网段 |
| 修改 `config.properties` 不生效 | `docker compose restart omes-dgw-server` |

### 2.6 相关文件

| 文件 | 说明 |
|------|------|
| `docker/deploy/docker-compose.yml` | 实施编排 |
| `docker/build/` | 研发打包容器脚本 |
| `docker/release/omes-dgw-deploy/` | 交给实施的完整目录 |
| `docker/build/Dockerfile.backend` | 后端镜像构建（jar 来自 `build/packages`） |

---

## 3. Prometheus 告警

规则文件：`monitoring/prometheus/alerts/omes-dgw-server.yml`，说明见 `monitoring/prometheus/README.md`。

| 告警 | 触发条件 |
|------|----------|
| `CollectPollSuccessRateLow` | 单设备轮询成功率 &lt; 80%，持续 3 分钟 |
| `CollectDeviceOfflineTooLong` | 离线时长 &gt; 5 分钟 |
| `CollectHistoryQueueBacklog` | 历史队列 `queue=history` 深度 &gt; 40000 |
| `CollectRedisPoolNearExhaustion` | Lettuce 连接池使用率 &gt; 90% |
| `CollectRedisPoolPendingConnections` | 连接池存在等待线程 |
| `CollectDbPoolActiveHigh` | 数据库 Hikari 活跃连接 &gt; 80% |
| `CollectJvmGcPauseHigh` | GC 单次暂停 &gt; 500ms |

新增指标：`collect_device_offline_duration_seconds`；队列深度每 30s 从 Redis LLEN 刷新。

---

## 4. 日志

由 `log4j2-spring.xml` 配置，在 `application.yml` 中引用：

```yaml
logging:
  config: classpath:log4j2-spring.xml
```

| 项 | 说明 |
|----|------|
| 默认目录 | `logs/`（相对进程工作目录） |
| 环境变量 `LOG_PATH` | 覆盖日志根目录 |
| 常规日志 | `omes-dgw-server.log`，按天 + 50MB 滚动，保留 14 份 |
| 错误日志 | `omes-dgw-server-error.log`，仅 ERROR 及以上，保留 30 份 |
| `com.omes.dgw` | info |
| `com.omes.dgw.collectlog` | info（结构化诊断） |

### 4.1 采集结构化诊断日志

关键路径使用 `event=...` + `key=value` 格式，便于单文件检索：

| event | 级别 | 含义 |
|-------|------|------|
| `poll_round` | INFO（默认 on_issue） | 单轮耗时：`connectMs` / `readMs` / `parseMs` / `redisMs` / `totalMs`，及 Modbus `plcTx` 等 |
| `point_value_lost` | WARN | 点位从有值变为未读/无值 |
| `plc_tag_response` | WARN | 批读/点读非 OK 的 PLC4X 响应码与 Modbus 异常语义提示 |
| `plc_connection_read_fail` | WARN | 连接级读失败与最近响应码摘要 |
| `jvm_memory` | INFO（默认 5 分钟） | 堆使用、线程数、各 GC count/time |

排查示例：

```bash
# 某设备整轮耗时
grep "event=poll_round" logs/omes-dgw-server.log | grep "deviceId=YOUR_ID"

# 某点位为何突然无值
grep "event=point_value_lost" logs/omes-dgw-server.log | grep "point=YOUR_POINT"

# Modbus 非法地址类异常
grep "event=plc_tag_response" logs/omes-dgw-server.log | grep "INVALID_ADDRESS"
```

将 `COLLECT_DIAGNOSTICS_POLL_TIMING_MODE=always` 可对每轮成功采集也输出 `poll_round`（设备多时注意日志量）。

| `com.omes.dgw.device.cache` | debug |
| `com.omes.dgw.protocol.ModbusTcpPollingProtocolManager` | debug（块读细节；生产可改为 info） |

---

## 5. 数据库与 Mapper

### 5.1 建表

首次部署配置 `SQLITE_PATH`（或使用默认 `./data/omes-dgw.db`）后启动服务，Flyway 自动执行 SQLite 迁移。无需外部数据库进程。

主要业务表：`T_DEVICE_CATEGORY`、`T_DEVICE`、`T_DEVICE_ONLINE_RECORD`、`T_DEVICE_POINT_WRITE_COMMAND` 等。

### 5.2 Mapper

`mapper/DeviceMapper.xml`、`mapper/DeviceCategoryMapper.xml`、`mapper/DevicePointWriteCommandMapper.xml` 为 MyBatis 自定义 SQL；其余 CRUD 由 MyBatis-Plus 生成。枚举字段通过 `@EnumValue` 以字符串入库。

---

## 6. Modbus TCP 轮询 — 运维调参

本节说明 Modbus 轮询行为、可配置项，以及各参数对**延迟**、**吞吐**、**稳定性**的影响。

### 6.1 采集链路

```
cron 触发 → 调度线程池 → 每设备单线程 readExecutor
    → 取 PLC4X 缓存连接 → readOnce（多块读 + 复杂地址点读）
    → 解析 → 写 Redis 实时缓存
```

要点：

- **每设备一个读线程**：同一设备上一轮未结束时，本轮 **skip**（防重入），可能丢一个采集周期。
- **块读优先**：简单地址（`holding-register:1` 等）启动时预分组，合并为最多 **125 寄存器 / 2000 位** 的 PDU 块（Modbus 协议上限，代码内固定）。
- **微批与降级**：块按 `tag-batch-size` 分批提交 PLC4X；块读失败经 `read-max-retries` 重试后降级为小批 → 单点；复杂地址走 fallback 批读。
- **部分成功**：任意点位读到即写 Redis；仅 **全部失败** 才抛错并可能触发熔断。

### 6.2 应用级配置

#### 调度与连接

| 配置项 | 环境变量 | 默认 | 调大 | 调小 |
|--------|----------|------|------|------|
| `polling-scheduler-pool-size` | `COLLECT_POLLING_SCHEDULER_POOL_SIZE` | 4 | 多设备时减少 cron 排队 | 省资源；设备多时触发变慢 |
| `connection.plc-connection-max-lease-sec` | `COLLECT_PLC_CONNECTION_MAX_LEASE_SEC` | 120 | 单轮读久时不被租约踢掉 | 异常连接释放更快 |
| `connection.plc-connection-max-wait-sec` | `COLLECT_PLC_CONNECTION_MAX_WAIT_SEC` | 20 | 高并发读/写时少超时 | — |

**粗估**：单轮读耗时 ≈ `批次数 × 设备 timeout`（每批一次 Modbus 往返，另加重试与降级）。  
若接近 **租约秒数 × 1000 ms**，应增大 `plc-connection-max-lease-sec`、降低 cron 频率或优化点位布局。

#### 合并切块

| 配置项 | 环境变量 | 默认 | 调大 | 调小 |
|--------|----------|------|------|------|
| `modbus-polling.max-merge-gap-registers` | `COLLECT_MODBUS_MAX_MERGE_GAP_REGISTERS` | 8 | 更少块、更高吞吐，但多读空洞 | 块更碎、报文更紧 |
| `modbus-polling.max-merge-gap-bits` | `COLLECT_MODBUS_MAX_MERGE_GAP_BITS` | 64 | 同上（位区） | 同上（位区） |

示例：点位在 40001、40010，中间空洞 8 个寄存器；`max-merge-gap-registers=8` 时可能合并为一块读 9 个寄存器；设为 `0` 则拆成两块。

#### 分批与重试（可外部配置）

| 配置项 | 环境变量 | 默认 | 说明 |
|--------|----------|------|------|
| `modbus-polling.tag-batch-size` | `COLLECT_MODBUS_TAG_BATCH_SIZE` | 20 | chunk 微批、fallback 批、块失败降级批的统一批大小 |
| `modbus-polling.read-max-retries` | `COLLECT_MODBUS_READ_MAX_RETRIES` | 3 | 块/批读失败重试次数 |
| `modbus-polling.read-retry-delay-ms` | `COLLECT_MODBUS_READ_RETRY_DELAY_MS` | 300 | 重试间隔；不阻塞读线程（由调度等待） |

#### 熔断（全协议共用）

| 配置项 | 环境变量 | 默认 | 说明 |
|--------|----------|------|------|
| `connection.max-consecutive-failures` | `COLLECT_CONNECTION_MAX_CONSECUTIVE_FAILURES` | 5 | 连续整轮失败停止轮询；`-1` 不熔断 |
| `connection.max-open-retries` | `COLLECT_CONNECTION_MAX_OPEN_RETRIES` | 10 | 熔断打开次数超限后放弃自动恢复 |
| `connection.backoff-*` | 见 §1.2 | — | 熔断退避与递增上限策略 |

**注意**：部分点位失败但本轮有数据时视为**成功**，不计连续失败；仅 **整轮零数据** 才计失败。

### 6.3 设备台账参数（每设备）

连接参数 JSON 或台账字段（轮询与按需读共用）：

| 字段 | 默认 | 说明 |
|------|------|------|
| `port` | 502 | Modbus TCP 端口 |
| `unitId` | 1 | 从站号 |
| `timeout` | 10000 | 单次读/写超时（ms）；**线性影响单轮耗时** |
| `modbusByteOrder` | 采集配置 | ABCD/CDAB 等，影响多寄存器解析 |
| `reusePollingConnection` | false | `true`：控制写复用轮询连接；`false`：独立短连接（推荐） |
| `collectCron` | — | 如 `0/5 * * * * ?`；应 **大于** 典型单轮读耗时 |
| `connectTimeoutMs` | — | 建连超时 |

### 6.4 代码内固定上限

| 常量 | 值 | 含义 |
|------|-----|------|
| 寄存器块上限 | 125 | 单 PDU 最多寄存器数 |
| 线圈块上限 | 2000 | 单 PDU 最多位数 |
| 连接级读重试 | 1 次（Modbus） | 块内已重试；逻辑失败（坏地址）不踢连接 |

### 6.5 场景化调参

**cron 经常 skip**（`poll skipped: previous run still in progress`）  
→ 增大 `polling-scheduler-pool-size`；拉长 `collectCron`；在稳定前提下适当降低 `timeout`。

**数百～上千点、单轮很慢**  
→ 增大 `plc-connection-max-lease-sec`（如 180～300）；地址尽量连续配置。

**点位稀疏、跨距大**  
→ `max-merge-gap-registers` 设为 0～4；注意块数增多需同步加大租约或降低频率。

**个别坏点拖整块**  
→ 已有块→小批→单点降级；仍失败查日志 `未读到点位=`，核对地址与数据类型。

**偶发熔断**  
→ 查网络与从站连接数；可适当加大 `max-consecutive-failures`；确认非「整轮零数据」误判。

**控制写与采集冲突**  
→ 保持 `reusePollingConnection: false`。

### 6.6 日志关键字

| 关键字 | 含义 |
|--------|------|
| `poll skipped: previous run still in progress` | 单轮超过 cron 间隔，本轮跳过 |
| `Modbus partial read deviceId=` | 部分点失败，已写 Redis |
| `块读失败 query=` / `未读到点位=` | 块读+降级后仍有点未读到 |
| `点读批次失败` | 复杂地址 fallback 失败 |
| `connection read ... chunkRetried=true` | 整轮失败；块级逻辑失败不踢连接 |
| `polling stopped` | 熔断停止轮询 |

### 6.7 吞吐与延迟粗算

设：

- `B` = chunk 微批次数（与块数、`tag-batch-size` 有关）
- `F` = fallback 批次数（复杂地址点数 / `tag-batch-size` 向上取整）
- `T` = 设备 `timeout`（秒）

理想单轮（无重试无降级）：

```text
单轮 ≈ (B + F) × T
```

降级/重试可再乘 1.5～3。**采集周期** 建议 ≥ `单轮 × 1.2`，并预留控制写余量。

### 6.8 配置示例

**中等规模（约 50 台、每台 100 点内）**

```yaml
app:
  collect:
    polling-scheduler-pool-size: 8
    connection:
      plc-connection-max-lease-sec: 120
    modbus-polling:
      max-merge-gap-registers: 8
      tag-batch-size: 20
```

设备 `timeout` 建议 3000～8000 ms；单轮需 3～5 s 时 `collectCron` 不低于 10 s。

**大点位、稀疏地址**

```yaml
app:
  collect:
    polling-scheduler-pool-size: 12
    connection:
      plc-connection-max-lease-sec: 300
    modbus-polling:
      max-merge-gap-registers: 2
      max-merge-gap-bits: 32
```

### 6.9 Apache PLC4X 版本与升级

| 项 | 说明 |
|----|------|
| **当前版本** | `0.13.1`（`pom.xml` 属性 `plc4x.version`；Maven Central 最新稳定版） |
| **0.14.0+** | 截至文档同步时 **尚未发布** 至 Maven Central（仅有 `0.14.0-SNAPSHOT` 开发线），无法在生产依赖中直接升级 |
| **预发验证** | `mvn -Pplc4x-snapshot compile` 使用 Apache 快照仓库（需网络可达） |
| **0.13.1 缓解** | 已启用 `CachedPlcConnectionManager.withMaxIdleTime`；批读/点读遇 `REMOTE_ERROR`/`INTERNAL_ERROR`/`REMOTE_BUSY` 时 `removeCachedConnection` 并重建；`PlcTagResponseSupport` 规避 0.13 响应码 NPE |

官方建议：连接异常后 `removeCachedConnection` 再 `getConnection`（见 [plc4x#2045](https://github.com/apache/plc4x/issues/2045)）。本服务在轮询超时、传输失败及上述 Modbus 异常响应时自动踢缓存。

**0.14.0 发布后**：将 `plc4x.version` 改为 `0.14.0` 并做回归（块读优化、异常响应处理、连接状态监听 API 变更等）。

### 6.10 相关代码

| 模块 | 类 |
|------|-----|
| 轮询主逻辑 | `ModbusTcpPollingProtocolManager` |
| 读计划预计算 | `ModbusReadPlan` |
| 连接参数解析 | `ModbusConnectParamsParser` |
| 调度/熔断基类 | `AbstractPlc4xPollingProtocolManager` |
| 配置绑定 | `AppProperties` / `application.yml` |
| PLC 响应/连接防护 | `PlcTagResponseSupport` / `PlcConnectionFaultException` |

---

*文档与当前 `application.yml`、`config/config.properties` 及 Modbus 轮询实现同步。*
