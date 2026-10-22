package com.omes.dgw.deploy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 开发时统一使用仓库根目录；独立部署时使用进程工作目录。
 */
@Component
public class DeployPaths {

    private final Path configFile;

    public DeployPaths(
            @Value("${app.deploy.config-file:config/config-box.properties}") String configFile
    ) {
        Path root = rootDirectory(Path.of(""));
        this.configFile = root.resolve(configFile).normalize();
    }

    public static Path rootDirectory(Path workingDirectory) {
        Path working = workingDirectory.toAbsolutePath().normalize();
        for (Path directory = working; directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("pom.xml"))
                    && (Files.isDirectory(directory.resolve("omes-dgw-common"))
                    || Files.isDirectory(directory.resolve("omes-dgw-portal-box"))
                    || Files.isDirectory(directory.resolve("omes-dgw-portal-center")))) {
                return directory;
            }
        }
        return working;
    }

    public Path configFile() {
        return configFile;
    }
}
