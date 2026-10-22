# config-templates 配置模板

首次启动（及每次 `03-启动.bat`）时，脚本将本目录内容同步到 `.env` 中的 **`MOUNT_PATH`**，供容器挂载使用。

---

## 模板来源

研发执行 `生成实施包.bat` 时，`publish.ps1` 从主仓库源码刷新：

| 实施包路径 | 源码路径 |
|------------|----------|
| `config-templates/omes-dgw/config/config-box.properties` | `config/config-box.properties` |
| `config-templates/omes-dgw/config/config-center.properties` | `config/config-center.properties` |
| `config-templates/nginx/default.conf` | `omes-dgw-web/nginx/default.conf` |
| `config-templates/omes-dgw/monitoring/` | `monitoring/` |

修改运行配置模板时，应改**源码对应文件**，再重新 `生成实施包.bat`。

---

## 同步规则（03-启动.bat）

| 目标（MOUNT_PATH 下） | 行为 |
|------------------------|------|
| `nginx/default.conf` | **每次启动**从模板覆盖 |
| `omes-dgw/config/config-center.properties` | 不存在则从模板复制；存在则按 `.env` 同步 SQLite/Influx 项 |
| `omes-dgw/config/config-box.properties` | 不存在则从模板复制（边缘盒子备用） |
| `omes-dgw/monitoring/` | 仅告警文件不存在时整目录复制 |

实施包默认跑**中控**门户 jar，因此同步以 `config-center.properties` 为准。

---

## 各文件说明

### omes-dgw/config/config-*.properties

| 文件 | 门户 |
|------|------|
| `config-box.properties` | 边缘盒子 `omes-dgw-portal-box` |
| `config-center.properties` | 中控 `omes-dgw-portal-center`（默认） |

容器挂载目录为 `/app/config`。中控进程默认读取 `config/config-center.properties`（可用 `DEPLOY_CONFIG_FILE` 覆盖）。

`03-up.ps1` 对 **center** 文件调用 `Sync-DeployConfigProperties`，将 `.env` 中的 SQLite/Influx 项写入。

### nginx/default.conf

前端 Nginx；`/api/` 代理到后端服务。

### omes-dgw/monitoring/

Prometheus 告警规则等。

---

## 现场定制建议

| 需求 | 推荐做法 |
|------|----------|
| 改 SQLite/Influx 连接 | 改 `.env`，`04` → `03` |
| 改发布端口 | 改 `.env` 中 `*_PUBLISH_PORT` |
| 改 Nginx | 改源码 `omes-dgw-web/nginx/default.conf` 后重新发包 |
| 改门户业务配置 | 改仓库 `config/config-box.properties` 或 `config-center.properties` 后重新发包；或现场改 `MOUNT_PATH/omes-dgw/config/` |
