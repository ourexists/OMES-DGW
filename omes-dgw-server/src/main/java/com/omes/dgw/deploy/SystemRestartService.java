package com.omes.dgw.deploy;

import com.omes.dgw.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Restart the JVM so uploaded plugins are loaded by a fresh class loader. */
@Service
public class SystemRestartService {
    private static final Logger log = LoggerFactory.getLogger(SystemRestartService.class);
    private static final AtomicBoolean RESTARTING = new AtomicBoolean();
    private final String instanceId = UUID.randomUUID().toString();
    private final ConfigurableApplicationContext context;
    private final String[] args;
    private final DeployPaths paths;
    private final boolean supervised;
    private volatile String failure;

    public SystemRestartService(ConfigurableApplicationContext context, ApplicationArguments arguments,
                                DeployPaths paths,
                                @Value("${app.deploy.restart-supervised:false}") boolean supervised) {
        this.context = context;
        this.args = arguments.getSourceArgs().clone();
        this.paths = paths;
        this.supervised = supervised;
    }

    public RestartStatus status() {
        return new RestartStatus(instanceId, RESTARTING.get(), failure);
    }

    public RestartStatus restart(String username) {
        if (RESTARTING.compareAndSet(false, true)) {
            failure = null;
            final Process replacement;
            try {
                if (!supervised && ProcessHandle.current().pid() == 1) {
                    throw new IllegalStateException("容器内请配置 app.deploy.restart-supervised=true 并启用容器自动重启策略");
                }
                if (supervised) {
                    replacement = null;
                } else {
                    var command = RestartProcess.command(paths.pluginsDir(), args);
                    Path output = Path.of("logs", "system-restart.log").toAbsolutePath();
                    Files.createDirectories(output.getParent());
                    replacement = new ProcessBuilder(command)
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.appendTo(output.toFile())).start();
                }
            } catch (Exception ex) {
                RESTARTING.set(false);
                throw new BusinessException("无法准备重启，当前服务保持运行：" + ex.getMessage());
            }
            Thread worker = new Thread(() -> {
                try {
                    // Allow the HTTP acknowledgement to reach the client before closing Tomcat.
                    Thread.sleep(1500);
                    if (replacement != null && !replacement.isAlive()) {
                        throw new IllegalStateException("替代进程启动失败，请检查 logs/system-restart.log");
                    }
                    log.warn("Application restart requested by {}", username);
                    context.close();
                    System.exit(0);
                } catch (InterruptedException ex) {
                    failure = "重启被中断，请检查服务日志";
                    Thread.currentThread().interrupt();
                    log.error("Application restart interrupted", ex);
                } catch (Exception ex) {
                    failure = ex.getMessage();
                    log.error("Application restart failed; manual recovery may be required", ex);
                } finally {
                    if (replacement != null && replacement.isAlive()) replacement.destroy();
                    RESTARTING.set(false);
                }
            }, "system-restart");
            worker.setDaemon(false);
            worker.start();
        }
        return status();
    }

    public record RestartStatus(String instanceId, boolean restarting, String failure) { }
}
