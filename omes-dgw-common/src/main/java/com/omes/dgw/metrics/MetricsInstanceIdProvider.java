package com.omes.dgw.metrics;

import com.omes.dgw.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 本实例稳定标识，用于 Influx 指标 tag（多副本区分来源）。
 */
@Component
public class MetricsInstanceIdProvider {

    private final String instanceId;

    public MetricsInstanceIdProvider(AppProperties appProperties) {
        String configured = appProperties.getCollect().getMetrics().getInstanceId();
        if (StringUtils.hasText(configured)) {
            this.instanceId = configured.trim();
        } else {
            this.instanceId = resolveDefaultInstanceId();
        }
    }

    public String instanceId() {
        return instanceId;
    }

    private static String resolveDefaultInstanceId() {
        String host = System.getenv("HOSTNAME");
        if (!StringUtils.hasText(host)) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ignored) {
                host = "unknown-host";
            }
        }
        return host + "-" + ProcessHandle.current().pid();
    }
}
