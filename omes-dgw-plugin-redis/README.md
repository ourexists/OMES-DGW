# omes-dgw-plugin-redis

OMES设备软边缘网关的 Redis 存储插件：在插件管理中上传并配置本模块后，将 `COLLECT_STORE_BACKEND=redis` 即可启用 Redis 实时缓存与指标选主。

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
3. 在管理页面启用 Redis，保存与更新在线生效。主服务启动使用：

```bash
java -jar omes-dgw-server-1.0-SNAPSHOT-exec.jar
```

不要使用 `loader.path` 或把插件加入主服务 classpath。停用时迁移快照到 Caffeine；待写队列始终由主服务持有。完整说明见 [热插拔说明](../docs/store-plugin-hot-swap.md)。

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
