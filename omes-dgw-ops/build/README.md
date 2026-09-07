# 研发：Docker 镜像与实施包构建

将应用打成 3 个镜像 tar，供 `生成实施包.bat` 写入 `release/omes-dgw-deploy/images/`。

---

## 前置条件

- 已安装 **Docker Desktop** 或 Docker Engine（`docker version` 正常）
- 构建中间件镜像时需能访问 Docker Hub（`docker pull influxdb:2.7-alpine`）
- 后端 jar、前端 dist 已放入 `packages/`（见 [packages/README.md](./packages/README.md)）

---

## 一键流程

```bat
cd docker\build
打包全部.bat
cd ..
生成实施包.bat
```

`生成实施包.bat` 若发现 `images/` 缺 tar，会自动调用 `build-all.ps1`。

---

## 目录结构

```text
build/
├── packages/              # jar / 前端 dist 输入
├── images/                # 产出 *.tar
├── Dockerfile.backend
├── Dockerfile.front
└── scripts/
    ├── build-all.ps1
    ├── build-backend.ps1
    ├── build-frontend.ps1
    ├── build-middleware.ps1  # 仅 InfluxDB
    ├── publish.ps1
    ├── test-image-tars.ps1   # 检查 3 个 tar 是否存在
    └── _image-packages.ps1
```

---

## 脚本说明

| 脚本 | 说明 |
|------|------|
| `build-all.ps1` | 后端 + 前端 + 中间件 |
| `build-backend.ps1` / `build-frontend.ps1` | 分组件构建 |
| `build-middleware.ps1` | pull + save Influx 镜像 |
| `test-image-tars.ps1` | 检查 3 个 tar；缺失时 `生成实施包.bat` 触发 build-all |
| `publish.ps1` | 由 `生成实施包.bat` 调用，组装 release |

---

## 镜像产出

| 文件 | 镜像标签 | 说明 |
|------|----------|------|
| `omes-dgw-server-*.tar` | `omes-dgw-server:*` | 后端 |
| `omes-dgw-web-*.tar` | `omes-dgw-web:*` | 前端 |
| `influxdb-2.7-alpine.tar` | `influxdb:2.7-alpine` | 官方镜像 pull + save |

版本与文件名见 `deploy/scripts/_image-versions.ps1`。

---

## 发布检查

1. 校验 `build/images/` 下 3 个 tar 齐全
2. 同步 `config-templates`（config.properties / nginx / monitoring）
3. 复制到 `release/omes-dgw-deploy/`

详见上级 [README.md](../README.md)。
