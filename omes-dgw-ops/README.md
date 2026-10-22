# OMES设备软边缘网关 - Docker 交付与部署

本目录用于将 **omes-dgw-portal-center**（中控电脑后端，默认）与 **omes-dgw-web**（前端）打成 Docker 镜像，并生成现场可直接使用的 **omes-dgw-deploy** 实施包。

产品入口：
- **边缘盒子**：`omes-dgw-portal-box`（内存、无采集）
- **中控电脑**：`omes-dgw-portal-center`（Redis+Influx+采集）— 实施包默认打此 jar

Docker 镜像/服务名仍可用 `omes-dgw-server`（兼容现场 compose），实际进程为 portal-center。

```
docker/
├── build/                  # 研发：组装镜像 tar
│   ├── packages/           # 输入：jar、前端 dist
│   ├── images/             # 输出：3 个 *.tar（git 忽略）
│   └── scripts/            # 构建与发布脚本
├── deploy/                 # 实施包模板（复制到 release 后交给现场）
│   ├── images/             # 发布时由 build/images 填入
│   ├── config-templates/   # 首次启动同步到 MOUNT_PATH
│   ├── scripts/            # 03/04 启动脚本内部逻辑
│   └── 实施部署手册.md      # 现场完整操作说明
├── release/                # 生成实施包.bat 产出（git 忽略）
└── 生成实施包.bat
```

---

## 角色与入口

| 角色 | 做什么 | 从哪里开始 |
|------|--------|------------|
| **研发** | 编译应用 → 打镜像 → 生成实施包 | 本文「研发流程」→ [build/README.md](./build/README.md) |
| **实施** | 拷包到服务器 → 改 `.env` → 启动 | [deploy/实施部署手册.md](./deploy/实施部署手册.md) |
| **运维** | 确认数据目录可写；表结构由应用 **Flyway** 首次启动自动迁移 | 无需本目录；SQLite 文件在 `MOUNT_PATH/omes-dgw/data` |

---

## 研发流程（概要）

### 1. 准备构建物

| 组件 | 来源 | 放入位置 |
|------|------|----------|
| 后端 jar | 根目录 `mvn -pl omes-dgw-portal-center -am -DskipTests package` | `build/packages/omes-dgw-portal-center-1.0-SNAPSHOT.jar` |
| 前端 dist | `omes-dgw-web` 执行 `npm run build` | `build/packages/dist/`（整目录） |

详见 [build/packages/README.md](./build/packages/README.md)。

### 2. 构建镜像并导出 tar

```bat
cd docker\build
打包全部.bat
```

产出 3 个文件到 `build/images/`：

| 文件 | 镜像标签 | 说明 |
|------|----------|------|
| `omes-dgw-server-1.0-SNAPSHOT.tar` | `omes-dgw-server:1.0-SNAPSHOT` | 应用后端 |
| `omes-dgw-web-1.0.0.tar` | `omes-dgw-web:1.0.0` | Nginx + 静态前端 |
| `influxdb-2.7-alpine.tar` | `influxdb:2.7-alpine` | 自带 InfluxDB 场景 |

中间件首次打包需联网 `docker pull`；现场部署**无需再拉远端镜像**。

也可分步执行：`打包后端.bat`、`打包前端.bat`，或单独 `build\scripts\build-middleware.ps1`。

### 3. 生成实施包

```bat
cd docker
生成实施包.bat
```

脚本会：

1. 检查 `build/images/` 下 3 个 tar 是否齐全（缺失则自动跑 `build-all`）
2. 将 `deploy/` 模板复制到 `release/omes-dgw-deploy/`
3. 从源码同步 `config-templates/`（`config.properties`、nginx、`monitoring`）
4. 复制镜像 tar 与 `.env`（若 `deploy/.env` 存在）

交付目录：**`release/omes-dgw-deploy/`**（整个文件夹拷给实施，不含源码）。

---

## 现场部署（概要）

实施工程师**只需** `omes-dgw-deploy` 文件夹：

```text
01-初始化环境配置.bat → 编辑 .env → [02-初始化InfluxDB.bat] → 03-启动.bat
```

| 默认端口 | 服务 |
|----------|------|
| 12011 | 前端（浏览器访问） |
| 12010 | 后端 API |
| 8086 | 自带 InfluxDB（`USE_BUNDLED_INFLUX=true`） |

前端 Nginx 已将 `/api/` 反向代理到后端，无需现场改前端 API 地址。

完整步骤、`.env` 场景说明、排障见 **[deploy/实施部署手册.md](./deploy/实施部署手册.md)**。

---

## 架构说明

```text
浏览器 :12011
    └── omes-dgw-web (Nginx)
            ├── /        → 静态页面
            └── /api/    → proxy → omes-dgw-server:12010
                                        ├── SQLite（本地文件）
                                        ├── Caffeine / 本地队列
                                        └── InfluxDB 2.x（可选）
```

- **SQLite DDL**：在 `omes-dgw-server/src/main/resources/db/migration/`，由后端服务执行。
- **数据持久化**：日志、配置、SQLite 库、Influx 数据写在 `.env` 的 `MOUNT_PATH`，与部署包目录分离。
- **中间件模式**：`.env` 中 `USE_BUNDLED_INFLUX` 控制 compose 是否拉起自带 Influx 容器。

---

## 版本与镜像名

统一维护于 `deploy/scripts/_image-versions.ps1`，需与 `deploy/docker-compose.yml` 中 `image:` 一致。

升级版本时同步修改上述两处，并重新 `打包全部.bat` → `生成实施包.bat`。

---

## 相关文档

| 文档 | 读者 |
|------|------|
| [build/README.md](./build/README.md) | 研发：脚本、Dockerfile、发布细节 |
| [build/packages/README.md](./build/packages/README.md) | 研发：jar/dist 准备 |
| [deploy/README.md](./deploy/README.md) | 实施：快速索引 |
| [deploy/实施部署手册.md](./deploy/实施部署手册.md) | 实施：完整手册 |
| [deploy/InfluxDB说明.md](./deploy/InfluxDB说明.md) | 实施：外用 Influx 初始化 |
| [deploy/config-templates/README.md](./deploy/config-templates/README.md) | 研发/实施：配置模板说明 |

---

## Git 忽略说明

见根目录 [.gitignore](./.gitignore)：忽略 `release/`、`*.tar`、`*.jar`、`.env`、前端 `dist` 构建物等；**保留** `.env.example` 与脚本模板。
