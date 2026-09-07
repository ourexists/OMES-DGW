# omes-dgw-deploy 实施部署包

研发交付的 **现场部署目录模板**。经 `生成实施包.bat` 复制并填入镜像后，整个文件夹交给实施工程师使用。

**完整操作说明请阅读：[实施部署手册.md](./实施部署手册.md)**

---

## 这个包是什么

- 预构建的 **3 个 Docker 镜像 tar**（应用 + InfluxDB）
- **docker-compose** 编排与一键启停脚本
- **`.env`** 现场配置（SQLite 路径、Influx、数据目录、端口）
- **config-templates**：首次启动时同步到 `MOUNT_PATH` 的运行配置

**不包含**：Java/Vue 源码、Maven/npm、docker/build 打包工具。

---

## 目录结构

```text
omes-dgw-deploy/
├── images/                         # 3 个镜像 tar（勿改名）
│   ├── omes-dgw-server-1.0-SNAPSHOT.tar
│   ├── omes-dgw-web-1.0.0.tar
│   └── influxdb-2.7-alpine.tar
├── 01-初始化环境配置.bat
├── 02-初始化InfluxDB.bat           # 仅外用 Influx 时需要
├── 03-启动.bat
├── 04-停止.bat
├── .env.example
├── docker-compose.yml
├── 实施部署手册.md
├── InfluxDB说明.md
├── config-templates/               # 同步到 MOUNT_PATH 的模板
└── scripts/                        # 由 bat 调用，一般勿手改
```

---

## 快速步骤

| 步骤 | 操作 |
|------|------|
| 1 | 将整个文件夹拷到服务器（如 `D:\omes-dgw-deploy\`） |
| 2 | 确认 `images\` 内 **三个 tar 齐全** |
| 3 | `01-初始化环境配置.bat`（无 `.env` 时从 example 生成） |
| 4 | **编辑 `.env`**：`MOUNT_PATH`、`SQLITE_PATH`、Influx 地址与 Token |
| 5 | 外用 Influx 且未初始化：`02-初始化InfluxDB.bat`，将凭证写入 `.env` |
| 6 | `03-启动.bat`（`docker load` + 分阶段 `compose up`） |
| 7 | 浏览器访问 `http://服务器IP:12011` |

日常停止：`04-停止.bat`。修改 `.env` 或重要配置后，先 `04` 再 `03`。

---

## 默认访问地址

| 服务 | 默认端口 | 说明 |
|------|----------|------|
| 前端 | **12011** | 浏览器入口；`/api` 由 Nginx 转发后端 |
| 后端 API | **12010** | 健康检查：`/actuator/health` |
| InfluxDB | 8086 | 仅 `USE_BUNDLED_INFLUX=true` 时对外映射 |

---

## 两种中间件部署场景

### 场景 A：客户已有 Influx（默认）

`.env` 保持：

```properties
USE_BUNDLED_INFLUX=false
INFLUX_URL=http://host.docker.internal:8086
```

- `host.docker.internal`：访问**装 Docker 的本机**上的服务
- 中间件在其他机器：改为对应内网 IP
- 外用 Influx 首次需 `02-初始化InfluxDB.bat` 或向客户管理员索取 Token

### 场景 B：由 compose 自带 Influx

```properties
USE_BUNDLED_INFLUX=true
INFLUX_URL=http://influxdb:8086
```

镜像已从 `images/*.tar` 加载，**现场无需 `docker pull`**。

---

## 数据目录 MOUNT_PATH

`.env` 中 `MOUNT_PATH` 指向数据根目录（如 `D:/docker/collect`），首次 `03-启动.bat` 会创建：

| 路径 | 内容 |
|------|------|
| `omes-dgw/config/` | `config.properties`（启动时从 `.env` 同步 SQLite/Influx） |
| `omes-dgw/logs/` | 后端日志 |
| `omes-dgw/data/` | SQLite 与运行时数据 |
| `nginx/default.conf` | Nginx 配置（含 `/api` 代理，每次启动从模板同步） |
| `nginx/logs/` | 前端 Nginx 日志 |
| `influxdb2/` | 自带 InfluxDB 数据 |

部署包本身可只读；**生产数据在 MOUNT_PATH**，升级实施包时保留该目录即可。

---

## 脚本编号说明

| 脚本 | 用途 |
|------|------|
| `01-初始化环境配置.bat` | `.env.example` → `.env`（已存在则不覆盖） |
| `02-初始化InfluxDB.bat` | 连接外用 Influx，创建 org/bucket/token |
| `03-启动.bat` | 加载镜像 → 同步配置 → 先启中间件 → 再启应用 |
| `04-停止.bat` | `docker compose down`（含 profile 中间件） |

`03-启动.bat` 内部流程：

1. `03-load-images.ps1`：加载 3 个 tar（本地已有同名镜像则跳过）
2. 同步 `nginx/default.conf`、`config.properties` 到 `MOUNT_PATH`
3. 若 `USE_BUNDLED_INFLUX=true`，先 `--wait` 启动 influxdb
4. 等待 `omes-dgw-server` 健康后启动 `omes-dgw-web`
5. 重启 front 使 Nginx 配置生效

---

## 排障索引

| 现象 | 优先检查 |
|------|----------|
| 页面 405 / 接口不通 | `MOUNT_PATH/nginx/default.conf` 是否含 `/api` 代理；重跑 `03-启动.bat` |
| `omes-dgw-server` 不健康 | SQLite 数据目录、`SQLITE_PATH`、Influx 连通性；`omes-dgw/logs` |
| 登录失败 / 401 | 后端是否 healthy；SQLite 是否迁移成功 |
| 改 `.env` 不生效 | `04-停止.bat` 后 `03-启动.bat` |
| 自带 Influx 无数据 | `USE_BUNDLED_INFLUX=true`；`influxdb2/` 目录权限 |

更多见 **[实施部署手册.md](./实施部署手册.md)** 第六节。

---

## 研发与实施的边界

| 事项 | 研发（docker/build） | 实施（本目录） |
|------|----------------------|----------------|
| 打镜像、生成 tar | ✓ | |
| `docker load` / `compose up` | | ✓ |
| 改 `.env`、SQLite/Influx | | ✓ |
| 改 Flyway DDL | 改源码重新发包 | |
| 改端口 / compose | 研发改模板重新发包 | 一般只改 `.env` 端口项 |
