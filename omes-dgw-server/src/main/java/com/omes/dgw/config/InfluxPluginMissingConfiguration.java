package com.omes.dgw.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 配置了 app.collect.monitor-type=influxdb 但 classpath 无 Influx 插件时快速失败。
 */
@ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "influxdb")
@ConditionalOnMissingClass("com.omes.dgw.store.influx.InfluxStoreAutoConfiguration")
public class InfluxPluginMissingConfiguration {

    @PostConstruct
    public void failFast() {
        throw new IllegalStateException(
                "app.collect.monitor-type=influxdb，但未找到 Influx 插件。"
                        + "请将 omes-dgw-plugin-influx 插件 jar（推荐 *-plugin.jar）加入 classpath（如 -Dloader.path=plugins）后再启动。"
                        + "详见 omes-dgw-plugin-influx/README.md"
        );
    }
}
