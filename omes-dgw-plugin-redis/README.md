# omes-dgw-plugin-redis

OMES设备软边缘网关的 Redis 存储插件：在主应用 **classpath** 中引入本模块后，将 `COLLECT_STORE_BACKEND=redis` 即可启用 Redis 实时缓存与有界队列。

核心服务 `omes-dgw-server` **默认仅含 Caffeine**，不依赖 Redis。

## 构建

在仓库根目录：

```bash
mvn -pl omes-dgw-plugin-redis -am package -DskipTests
```

产出：

| 文件 | 说明 |
|------|------|
| `omes-dgw-plugin-redis/target/omes-dgw-plugin-redis-*-plugin.jar` | **推荐**：shade fat jar，已含 lettuce / commons-pool2 / spring-data-redis 等 |
| `omes-dgw-plugin-redis/target/omes-dgw-plugin-redis-*.jar` | 普通 jar（不含传递依赖，需自行补齐 classpath） |

## 导入插件（推荐：单文件 plugin jar）

1. 管理端登录后打开菜单 **插件管理**，上传 `omes-dgw-plugin-redis-*-plugin.jar`，填写 Redis 连接并保存。
2. 或以手动方式将 jar 放到部署目录 `plugins/`。
3. 使用支持 `loader.path` 的启动方式（可执行包为 ZIP/PropertiesLauncher）：

```bash
java -Dloader.path=plugins -jar omes-dgw-server-1.0-SNAPSHOT-exec.jar
```

Docker 镜像默认已带 `-Dloader.path=/app/plugins`。导入/改配置后需**重启**进程。

也可与主应用同一 JVM classpath 启动：

```bash
java -cp "omes-dgw-server-1.0-SNAPSHOT.jar;plugins/omes-dgw-plugin-redis-1.0-SNAPSHOT-plugin.jar" \
  com.omes.dgw.App
```

若使用解压后的主应用或 IDE 运行，把 plugin jar 加入 `-cp` / Run Configuration 即可。

> 主应用可执行包为 `omes-dgw-server-*-exec.jar`。开发联调可将 plugin jar 与主应用 **普通 jar**（无 `-exec`）及依赖一并放入 `-cp`，主类 `com.omes.dgw.App`。

## 配置

```bash
COLLECT_STORE_BACKEND=redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0
# 可选
COLLECT_REDIS_KEY_PREFIX=collect:device:realtime
```

未引入插件却配置 `store-backend=redis` 时，主应用会启动失败并提示加入 `omes-dgw-plugin-redis`。

## 备选：普通 jar + 依赖目录

若不用 shade plugin jar：

```bash
mvn -pl omes-dgw-plugin-redis -am dependency:copy-dependencies \
  -DincludeScope=runtime \
  -DoutputDirectory=plugins/redis-lib
# 再将 omes-dgw-plugin-redis-*.jar 与 plugins/redis-lib/* 一并加入 classpath
```

## 已知限制

- 管理端「插件管理」菜单可导入 jar 并写 `config.properties`；**重启**后由 `-Dloader.path=plugins` 加载。
- plugin jar **不** 打入 `omes-dgw-server`（provided）与主应用已有的 Spring Boot / Jackson / 日志库。
- 主应用 `App` 仍排除默认 `RedisAutoConfiguration`；仅本插件在 `store-backend=redis` 时按需 Import。
