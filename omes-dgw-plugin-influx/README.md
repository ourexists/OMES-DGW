# omes-dgw-plugin-influx

OMES设备软边缘网关的 InfluxDB 时序插件：在主应用 **classpath** 中引入本模块后，将 `INFLUX_ENABLED=true` 即可启用快照历史与监控指标落库。

核心服务 `omes-dgw-server` **默认不依赖** `influxdb-client-java`。

## 构建

在仓库根目录：

```bash
mvn -pl omes-dgw-plugin-influx -am package -DskipTests
```

产出：

| 文件 | 说明 |
|------|------|
| `omes-dgw-plugin-influx/target/omes-dgw-plugin-influx-*-plugin.jar` | **推荐**：shade fat jar，已含 influxdb-client 与必要传递依赖 |
| `omes-dgw-plugin-influx/target/omes-dgw-plugin-influx-*.jar` | 普通 jar（不含传递依赖） |

## 导入插件

1. 管理端打开菜单 **插件管理 → Influx 时序**，上传 `omes-dgw-plugin-influx-*-plugin.jar`，填写连接并保存。
2. 或以手动方式将 jar 放到部署目录 `plugins/`。
3. 使用支持 `loader.path` 的启动方式：

```bash
java -Dloader.path=plugins -jar omes-dgw-server-1.0-SNAPSHOT-exec.jar
```

导入/改配置后需**重启**进程。

## 配置

```bash
INFLUX_ENABLED=true
INFLUX_URL=http://localhost:8086
INFLUX_TOKEN=your-token
INFLUX_ORG=omes
INFLUX_BUCKET=dgw_metrics

# 可选业务开关（仍需 INFLUX_ENABLED=true）
COLLECT_HISTORY_ENABLED=true
METRICS_PERSIST_INFLUX=true
```

未引入插件却配置 `INFLUX_ENABLED=true` 时，主应用会启动失败并提示加入 `omes-dgw-plugin-influx`。
