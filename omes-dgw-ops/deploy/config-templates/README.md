# config-templates 配置模板

首次启动（及每次 `03-启动.bat`）时，脚本将本目录内容同步到 `.env` 中的 **`MOUNT_PATH`**，供容器挂载使用。

---

## 模板来源

本目录**不单独在 git 中维护完整副本**（除 `nginx/default.conf` 示例外）。研发执行 `生成实施包.bat` 时，`publish.ps1` 从主仓库源码刷新：

| 实施包路径 | 源码路径 |
|------------|----------|
| `config-templates/omes-dgw/config/config.properties` | `omes-dgw-server/config/config.properties` |
| `config-templates/nginx/default.conf` | `omes-dgw-web/nginx/default.conf` |
| `config-templates/omes-dgw/monitoring/` | `omes-dgw-server/monitoring/` |

修改运行配置模板时，应改**源码对应文件**，再重新 `生成实施包.bat`。

---

## 同步规则（03-启动.bat）

| 目标（MOUNT_PATH 下） | 行为 |
|------------------------|------|
| `nginx/default.conf` | **每次启动**从模板覆盖（保证 `/api` 代理等与研发一致） |
| `omes-dgw/config/config.properties` | 不存在则从模板复制；存在则按 `.env` 同步 SQLite/Influx 项 |
| `omes-dgw/monitoring/` | 仅告警文件不存在时整目录复制 |

同步后 `omes-dgw-web` 会重启以加载最新 Nginx 配置。

---

## 各文件说明

### omes-dgw/config/config.properties

后端 Spring 外部配置，容器挂载为 `/app/config/config.properties`。

`03-up.ps1` 调用 `Sync-DeployConfigProperties`，将 `.env` 中的以下项写入该文件：

- SQLite：`SQLITE_PATH`、`FLYWAY_ENABLED`
- Influx：`INFLUX_URL`、`INFLUX_TOKEN`（来自 `INFLUX_ADMIN_TOKEN`）、`INFLUX_ORG`、`INFLUX_BUCKET`

现场改连接信息时，**改 `.env` 后重启**即可，无需直接编辑此文件（除非有特殊定制）。

### nginx/default.conf

前端 Nginx 配置，容器挂载为 `/etc/nginx/conf.d/default.conf`。

关键配置：

```nginx
location /api/ {
    proxy_pass http://omes-dgw-server:12010/;
    ...
}
```

浏览器访问 `http://IP:12011/api/...` 时，请求转发到后端；路径前缀 `/api` 会被去掉（例如 `/api/auth/login` → 后端 `/auth/login`）。

### omes-dgw/monitoring/

Prometheus 告警规则等，挂载到后端 `/app/monitoring`。一般随版本升级由新实施包覆盖缺失文件。

---

## 现场定制建议

| 需求 | 推荐做法 |
|------|----------|
| 改 SQLite/Influx 连接 | 改 `.env`，`04` → `03` |
| 改发布端口 | 改 `.env` 中 `*_PUBLISH_PORT` |
| 改 Nginx 代理或静态页行为 | 改源码 `omes-dgw-web/nginx/default.conf`，研发重新发包 |
| 改后端业务配置 | 改源码 `omes-dgw-server/config/config.properties`，研发重新发包；或现场改 `MOUNT_PATH/omes-dgw/config/` 后重启（升级包可能被同步逻辑覆盖部分项） |

---

## 与部署包根目录的关系

```text
omes-dgw-deploy/          ← 实施拷贝到服务器（只读即可）
    config-templates/    ← 模板

MOUNT_PATH/              ← .env 指定，持久化数据
    omes-dgw/config/   ← 运行时配置
    nginx/default.conf   ← 运行时 Nginx
    omes-dgw/logs/     ← 日志
    ...
```

升级实施包时，保留 **MOUNT_PATH** 即可保留日志与中间件数据；配置模板会随 `03-启动.bat` 按上述规则更新。
