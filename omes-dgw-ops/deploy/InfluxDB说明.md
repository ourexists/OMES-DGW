# InfluxDB 初始化（实施包根目录）

与 `03-启动.bat` 同级。用于**客户已有 InfluxDB 2.x**、需为本系统准备 org / bucket / 应用 Token 的现场。

若由 compose **自带 Influx**（`USE_BUNDLED_INFLUX=true`），首次空库时由容器环境变量自动初始化，**无需**运行本脚本。

## 前置条件

- 目标 InfluxDB 2.x 可从本机访问（HTTP API，默认 `8086`）
- 已具备**管理员 Token**（可创建 org、bucket、authorization）
- 本机可执行 PowerShell（`02-初始化InfluxDB.bat` 已包含）

## 现场步骤

1. 可选：`copy init-influx.env.example init-influx.env`（仅默认 URL / org / bucket / 保留期）
2. 双击 **`02-初始化InfluxDB.bat`**，按提示输入：
   - InfluxDB 地址
   - 管理员 Token
   - 组织名、桶名、数据保留期（默认 `30d`）
3. 将生成的 `influx-app-credentials.env` 中的 `INFLUX_*` 写入 `.env`

## 脚本行为

| 步骤 | 说明 |
|------|------|
| 健康检查 | `GET /health` |
| 创建 org | 不存在则创建，已存在则跳过 |
| 创建 bucket | 不存在则创建（含保留期），已存在则跳过 |
| 创建应用 Token | 仅对该 bucket 读写权限 |

InfluxDB 为 schema-less，**无需 DDL**。应用写入的 measurement：

- `collect_protocol_metrics` — 协议采集指标
- `device_realtime` — 设备在线态快照
- `device_point` — 点位实时值历史

## 文件

| 文件 | 作用 |
|------|------|
| `02-初始化InfluxDB.bat` | 交互入口 |
| `02-初始化InfluxDB.ps1` | 内部逻辑（Influx HTTP API） |
| `init-influx.env.example` | 可选默认参数 |
| `influx-app-credentials.env` | 运行后生成，勿提交版本库 |
