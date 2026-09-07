# Prometheus 监控与告警

## 指标端点

- URL：`http://<host>:12010/actuator/prometheus`
- 已放行（无需 JWT）：见 `SecurityConfig` 中 `/actuator/prometheus`

## 告警规则

文件：`alerts/omes-dgw-server.yml`

| 告警 | 条件 | 默认 for |
|------|------|----------|
| `CollectPollSuccessRateLow` | 单设备 `collect_protocol_poll` 成功率 &lt; 80% | 3m |
| `CollectDeviceOfflineTooLong` | `collect_device_offline_duration_seconds` &gt; 300 | 1m |
| `CollectHistoryQueueBacklog` | `collect_queue_depth{queue="history"}` &gt; 40000 | 2m |
| `CollectHistoryQueueTrimmed` | `collect_queue_trimmed_total{queue="history"}` 5m 内增加 | 0m |
| `CollectDbPoolActiveHigh` | Hikari 活跃 &gt; 80% | 3m |
| `CollectJvmGcPauseHigh` | `jvm_gc_pause_seconds_max` &gt; 0.5s（3m 窗） | 1m |

## Prometheus 配置示例

```yaml
scrape_configs:
  - job_name: omes-dgw-server
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['omes-dgw-server:12010']

rule_files:
  - /etc/prometheus/rules/omes-dgw-server.yml
```

## 指标与代码对应

| 指标 | 来源 |
|------|------|
| `collect_protocol_poll_total` | `ProtocolCollectMetrics.recordPoll` |
| `collect_device_offline_duration_seconds` | `CollectDeviceOfflineMetrics` |
| `collect_queue_depth` | `CollectQueueMetrics`（入队 + 定时刷新） |
| `collect_queue_trimmed_total` | `CollectQueueMetrics`（超限丢弃） |
| `hikaricp_connections_*` | Spring Boot JDBC |
| `jvm_gc_pause_seconds_*` | Micrometer JVM |

## 调参

- 历史队列告警阈值：规则中 `40000`，与 `COLLECT_HISTORY_MAX_QUEUE_SIZE`（默认 50000）对齐
- SQLite 池：`spring.datasource.hikari.maximum-pool-size`（默认 1）

部署后可用 `promtool check rules alerts/omes-dgw-server.yml` 校验语法。
