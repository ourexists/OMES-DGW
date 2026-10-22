package com.omes.dgw.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 在 DataSource / Flyway 建连之前创建 SQLite 库文件父目录。
 * JDBC 只会创建 .db 文件本身，不会创建目录；{@code @PostConstruct} 过晚。
 */
@Configuration
public class SqliteDataSourceConfig implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(SqliteDataSourceConfig.class);

    private Environment environment;

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        ensureParentDirectory(resolveSqlitePath(environment));
    }

    static String resolveSqlitePath(Environment environment) {
        String path = environment.getProperty("SQLITE_PATH");
        if (path != null && !path.isBlank()) {
            return path.trim();
        }
        String url = environment.getProperty("spring.datasource.url", "");
        if (url.startsWith("jdbc:sqlite:")) {
            String rest = url.substring("jdbc:sqlite:".length());
            int q = rest.indexOf('?');
            return (q >= 0 ? rest.substring(0, q) : rest).trim();
        }
        return "./data/omes-dgw.db";
    }

    static void ensureParentDirectory(String sqlitePath) {
        try {
            Path dbPath = Path.of(sqlitePath).toAbsolutePath().normalize();
            Path parent = dbPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                log.info("Created SQLite data directory: {}", parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create SQLite parent directory for path: " + sqlitePath, e);
        }
    }
}
