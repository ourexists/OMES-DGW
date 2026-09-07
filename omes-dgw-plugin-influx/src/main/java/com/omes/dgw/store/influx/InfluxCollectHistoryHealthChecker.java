package com.omes.dgw.store.influx;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.influx.DeviceRealtimeHistoryWriter;
import com.omes.dgw.influx.InfluxRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;

/**
 * 启动时检查 Influx 连通性，并提示快照历史写入链路是否就绪。
 */
@Slf4j
@Order(100)
@ConditionalOnProperty(prefix = "app.influx", name = "enabled", havingValue = "true")
@ConditionalOnBean(InfluxRuntime.class)
public class InfluxCollectHistoryHealthChecker implements ApplicationRunner {

    private final InfluxRuntime influx;
    private final AppProperties appProperties;

    public InfluxCollectHistoryHealthChecker(
            InfluxRuntime influx,
            AppProperties appProperties
    ) {
        this.influx = influx;
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Influx cfg = appProperties.getInflux();
        boolean historyEnabled = appProperties.getCollect().getHistory().isEnabled();
        boolean pingOk = influx.ping();
        if (pingOk) {
            log.info(
                    "InfluxDB 连接正常 url={}, org={}, bucket={}, historyEnabled={}",
                    cfg.getUrl(),
                    cfg.getOrg(),
                    cfg.getBucket(),
                    historyEnabled
            );
        } else {
            log.error(
                    "InfluxDB 连接失败，快照历史将无法写入 url={}, org={}, bucket={}, historyEnabled={}",
                    cfg.getUrl(),
                    cfg.getOrg(),
                    cfg.getBucket(),
                    historyEnabled
            );
        }
        if (historyEnabled) {
            log.info(
                    "快照历史写入 measurement: {}（设备在线态）, {}（点位值）；"
                            + "请确认 bucket 已创建且 token 有写权限",
                    DeviceRealtimeHistoryWriter.MEASUREMENT_DEVICE,
                    DeviceRealtimeHistoryWriter.MEASUREMENT_POINT
            );
        } else {
            log.warn("app.collect.history.enabled=false，实时快照不会写入 Influx");
        }
    }
}
