# OMES 设备软网关

`omes-dgw` 跑在设备附近，按周期读取 PLC 和仪表，把实时值留在本机，并通过 HTTP 交给 [OMES 业务平台](../omes-iomp/readme.md) 或其他调用方。认证和台账都在本进程内完成。

版权 2025–2026 OMES 贡献者。分发条款见 [Apache-2.0](LICENSE) 和 [NOTICE](NOTICE)。

## 能做什么

南向已经实现的协议（仅 **中控门户** 打包）：

| 代码 | 设备 | 实现要点 |
| --- | --- | --- |
| `MODBUS_TCP` | Modbus TCP | PLC4X，线圈与寄存器块读合并，失败重试 |
| `OPC_UA` | OPC UA | PLC4X，端点发现、安全策略、用户名密码 |
| `S7_TCP` | 西门子 S7 | s7connector，配置 rack / slot |
| `FINS_TCP` | 欧姆龙 FINS/TCP | 自研客户端，常见端口 9600 |
| `SLMP_TCP` | 三菱 SLMP / MC 3E | 自研客户端，常见端口 5000 |

每台设备一条 Cron。同一设备不会并发跑两轮采集。连续失败会熔断并指数退避。点位类型覆盖 BOOL、INT、REAL、LREAL、TEXT8 等，各协议可用的类型不同。

## 双门户部署

| 场景 | 模块 | 存储 | 采集 | 前端 |
| --- | --- | --- | --- | --- |
| **边缘盒子** | `omes-dgw-portal-box` | 仅内存 Caffeine | **无**（不依赖 collect/protocol） | **内嵌同 JAR**（不前后端分离） |
| **中控电脑** | `omes-dgw-portal-center` | Redis + InfluxDB | **有**完整协议采集 | 独立 `omes-dgw-web` / Nginx |

启动（在仓库根目录打包后）：

```bash
# 边缘盒子：单 JAR，浏览器打开 http://<host>:12020/
java -jar omes-dgw-portal-box/target/omes-dgw-portal-box-1.0-SNAPSHOT.jar

# 中控电脑（需可用的 Redis 与 InfluxDB；前端另起 Nginx/Vite）
java -jar omes-dgw-portal-center/target/omes-dgw-portal-center-1.0-SNAPSHOT.jar
```

或开发时：

```bash
# 盒子会先 npm 构建前端再启动；仅调后端可加 -Dskip.frontend=true，再用 Vite 连端口
mvn -pl omes-dgw-portal-box -am spring-boot:run
mvn -pl omes-dgw-portal-center -am spring-boot:run
```

约定配置：`app.portal.mode`（`box` / `center`，兼容旧值 `memory`→BOX、`edge`→CENTER）、`app.collect.enabled`、`app.collect.store-backend`、`app.collect.monitor-type`。

外部覆盖配置（JAR 同级 `config/`，门户各自一份）：

| 门户 | 文件 |
| --- | --- |
| 边缘盒子 | [`config/config-box.properties`](config/config-box.properties) |
| 中控电脑 | [`config/config-center.properties`](config/config-center.properties) |

说明见 [`config/README.md`](config/README.md)。可用环境变量 `DEPLOY_CONFIG_FILE` 覆盖路径。

## 存储模块（编译期依赖，非 PF4J）

实时快照默认 Caffeine（盒子门户）。中控门户固定 Redis + InfluxDB 2.x。存储以普通 Maven 模块引入，**不再**支持插件 jar 热插拔。

可观测性：`/actuator/health`、`/actuator/prometheus`。接口文档由 springdoc 提供。门户信息：`GET /system/portal`。

## 构建

后端：JDK 21、Maven、Spring Boot 3.3.5。在 `omes-dgw` 目录执行：

```bash
mvn -DskipTests package
```

可执行包：

- `omes-dgw-portal-box/target/omes-dgw-portal-box-*.jar`（已内嵌 `omes-dgw-web` 的 `build:box` 产物）
- `omes-dgw-portal-center/target/omes-dgw-portal-center-*.jar`

已删除的旧模块：`omes-dgw-server`、`omes-dgw-portal-memory`、`omes-dgw-portal-edge`。

前端：

- **盒子**：打包 portal-box 时自动 `npm run build:box`（`VITE_API_BASE_URL=/`），写入 JAR 的 `/static`
- **中控**：在 `omes-dgw-web` 执行 `npm install`、`npm run build`（`VITE_API_BASE_URL=/api`，配合 Nginx）

## 模块

| 模块 | 职责 |
| --- | --- |
| `omes-dgw-common` | 共享业务：auth/config/deploy/device 台账/queue/timeseries SPI/monitoring |
| `omes-dgw-collect` | 仅中控：protocol + metrics 采样落库 + modscan + collectlog |
| `omes-dgw-store-memory` | Caffeine 实时缓存 |
| `omes-dgw-store-redis` | Redis 实时缓存与队列 |
| `omes-dgw-store-influx` | InfluxDB 历史与指标 |
| `omes-dgw-portal-box` | 边缘盒子 Boot 入口（内嵌前端） |
| `omes-dgw-portal-center` | 中控电脑 Boot 入口 |
| `omes-dgw-web` | 管理端（盒子构建时打进 box JAR；中控仍可独立部署） |
| `omes-dgw-ops` | 镜像和实施包（默认打 portal-center jar） |

## 继续阅读

| 文档 | 内容 |
| --- | --- |
| [服务端资源配置](omes-dgw-common/src/main/resources/README.md) | 外部配置、Modbus 调参、离线检测 |
| [存储插件热插拔（已废弃）](docs/store-plugin-hot-swap.md) | 旧 PF4J 方案说明 |
| [运维交付](omes-dgw-ops/README.md) | 打镜像和生成实施包 |
| [Prometheus](monitoring/prometheus/README.md) | 指标与告警规则 |
