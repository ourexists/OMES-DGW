# build/packages 构建输入

本目录放置后端 jar 与前端 dist，供 Docker 构建脚本使用。

## 后端

```bat
cd omes-dgw-server
mvn -DskipTests package
copy target\omes-dgw-server-1.0-SNAPSHOT.jar ..\omes-dgw-ops\build\packages\
```

放入：`omes-dgw-server-1.0-SNAPSHOT.jar`（文件名需与 `_image-versions.ps1` 一致）。

## 前端

```bat
cd omes-dgw-web
npm run build
xcopy /E /I /Y dist ..\omes-dgw-ops\build\packages\dist\
```

放入：`packages/dist/` 整目录。

## 中间件（InfluxDB）

不放在 `packages/`，由 `build-middleware.ps1` 从 Docker Hub 拉取并 save：

| 文件 | 镜像 |
|------|------|
| `../images/influxdb-2.7-alpine.tar` | `influxdb:2.7-alpine` |

```bat
cd docker\build\scripts
powershell -File build-middleware.ps1
```

或随 `打包全部.bat` 一并执行。tar 已存在则跳过，删除 tar 可强制重新 pull。

## 校验清单（提交实施包前）

- [ ] `packages/omes-dgw-server-*.jar` 为最新 `mvn package` 产出
- [ ] `packages/dist/` 为最新 `npm run build` 产出
- [ ] `build/images/` 下 **3 个 tar** 齐全且为本次构建
