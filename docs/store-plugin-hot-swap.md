# 存储插件热插拔

主服务通过 PF4J 在独立类加载器中装载 Redis / InfluxDB 插件，插件操作不需要重启 JVM。

## 构建和启动

```sh
mvn -pl omes-dgw-plugin-redis,omes-dgw-plugin-influx -am package
java -jar omes-dgw-server/target/omes-dgw-server-1.0-SNAPSHOT-exec.jar
```

上传各插件 `target/omes-dgw-plugin-*-1.0-SNAPSHOT-plugin.jar`（含客户端依赖的包）。不要把插件放进主服务 classpath，不再使用 `-Dloader.path=plugins`。Docker 启动命令已同步调整；部署目录仍挂载 `plugins/`。

已有部署首次升级主服务需要重启一次。之后可在插件管理页面完成上传、启用、停用、在线升级和卸载。旧自动配置插件包缺少 PF4J manifest，必须重新构建。

## 操作行为

- 上传未启用插件：校验 JAR、插件 ID、入口与 API 版本，保存安装包；填写连接信息后点击启用。
- 上传已启用插件：创建运行副本、初始化并检查连接，等待当前调用结束后切换，关闭旧资源和类加载器。新版本失败则保留旧版本。
- 保存配置：在线重新创建受影响的插件；准备失败不保存配置。配置文件使用同目录原子替换。
- 停用 Redis：先把实时快照迁移到 Caffeine，再关闭 Redis 连接。读取或迁移失败时拒绝切换，保留原实例。
- 停用 InfluxDB：等待在途写入/查询完成；停止新历史入队，待写队列保留，重新启用后继续写入。
- 卸载：先停用、保存禁用状态，再删除安装包，保留连接参数。
- 启动：从 `plugins/` 和配置恢复启用状态。配置启用的插件缺失或无法初始化时启动失败，需修正插件包或连接配置。

## 数据与并发边界

有界待写队列固定由主服务持有，使用进程内队列，Redis 插件提供实时缓存与指标选主。插件停用/升级不会销毁队列；进程崩溃或重启时队列不提供持久性。改变 Influx bucket/地址后，保留的数据会写入新配置的目标。

稳定代理和消费者批次持有读锁，插件切换持有写锁。初始化、缓存迁移期间相关存储调用可能短暂等待；无需停止整个主服务，但不承诺零延迟。批量条数、队列上限、历史/指标开关和刷写周期在线读取；刷写调度每秒检查一次配置。

不提供跨 Redis 实例的分布式迁移事务；建议单网关实例使用。远端缓存迁移可能已产生部分写入，即使后续配置提交失败，当前主服务仍保留旧路由。

## 开发约定

`omes-dgw-plugin-api` 定义生命周期 API；插件继承 `StorePlugin`，提供 `initialize`、`services`、`checkHealth` 与幂等 `stop`。API 版本为 `1.0.0`，使用 manifest 的 `Plugin-Id` / `Plugin-Class` / `Plugin-Version` / `Plugin-Requires` 校验。

当前业务 SPI 和 DTO 仍由主服务提供，插件以 `provided` 编译依赖主服务；插件与主服务应配套构建。API、Spring、Jackson、日志库不得重复打入插件包。类加载器优先共享主服务的公共类；客户端依赖随插件部署。

插件 JAR 是具有主服务权限的可信代码；管理接口沿用 ADMIN 权限。插件版本运行副本位于 `plugins/.runtime/`，正常停用时关闭并删除，避免 Windows 文件占用阻止更新。

## 验证

```sh
mvn -pl omes-dgw-plugin-redis,omes-dgw-plugin-influx -am test
cd omes-dgw-web
npm run build
```

生命周期测试使用真实 PF4J JAR 装载，覆盖快照迁移、在线升级、失败配置回滚、文件提交失败回滚、等待在途请求、非法安装包与资源关闭。
