package com.omes.dgw.deploy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 部署旁路路径（相对进程工作目录，与 Docker /app 布局一致）。
 */
@Component
public class DeployPaths {

    private final Path configFile;
    private final Path pluginsDir;

    public DeployPaths(
            @Value("${app.deploy.config-file:config/config.properties}") String configFile,
            @Value("${app.deploy.plugins-dir:plugins}") String pluginsDir
    ) {
        this.configFile = Path.of(configFile).toAbsolutePath().normalize();
        this.pluginsDir = Path.of(pluginsDir).toAbsolutePath().normalize();
    }

    public Path configFile() {
        return configFile;
    }

    public Path pluginsDir() {
        return pluginsDir;
    }
}
