# 系统重启

管理员在运行配置页面点击「系统重启」会重新启动 Java 进程，加载配置及 plugins 目录中的插件。

- 本地 JAR：使用 ZIP 布局的 `*-exec.jar`。新进程保留 JVM 参数和应用参数，插件由运行时管理器加载；IDE 调试 agent 不会继承。
- IDEA 直接运行 App：新进程使用原 classpath。重启后新进程不再属于 IDEA 调试会话，控制台输出追加到工作目录的 `logs/system-restart.log`。需要调试时请停止新进程后重新从 IDEA 启动。
- Docker Compose：仓库配置启用了 `APP_DEPLOY_RESTART_SUPERVISED=true` 和 `restart: unless-stopped`，应用退出后由 Docker 重新拉起。
- systemd 等托管部署：设置 `APP_DEPLOY_RESTART_SUPERVISED=true`，并在进程管理器配置退出后自动拉起（例如 systemd `Restart=always`）。只有配置了外部拉起策略才能启用该模式。

本地替代进程会等待旧进程退出后才启动 Spring，避免端口和数据库冲突。等待超过 90 秒则取消启动；启动日志见 `logs/system-restart.log`。页面通过新实例 ID 确认重启完成。启动失败需要根据日志修正配置并手动恢复。

首次部署本次代码仍需手动重启。插件加载失败时请检查插件包与连接参数，或先禁用对应后端。插件更新和配置保存无需重启，见 [热插拔说明](../docs/store-plugin-hot-swap.md)。
