package com.omes.dgw.store.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.omes.dgw.config.AppProperties;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(prefix = "app.collect", name = "monitor-type", havingValue = "influxdb")
public class InfluxConfig {

    @Bean(destroyMethod = "close")
    public InfluxDBClient influxDBClient(AppProperties appProperties) {
        AppProperties.Influx influx = appProperties.getInflux();
        if (!StringUtils.hasText(influx.getUrl())) {
            throw new IllegalStateException("app.influx.url 未配置");
        }
        if (!StringUtils.hasText(influx.getToken())) {
            throw new IllegalStateException("app.influx.token 未配置");
        }
        if (!StringUtils.hasText(influx.getOrg())) {
            throw new IllegalStateException("app.influx.org 未配置");
        }
        if (!StringUtils.hasText(influx.getBucket())) {
            throw new IllegalStateException("app.influx.bucket 未配置");
        }

        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder()
                .connectTimeout(influx.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(influx.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(influx.getWriteTimeoutMs(), TimeUnit.MILLISECONDS);

        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                .url(influx.getUrl().trim())
                .authenticateToken(influx.getToken().toCharArray())
                .org(influx.getOrg().trim())
                .bucket(influx.getBucket().trim())
                .okHttpClient(httpBuilder)
                .build();

        return InfluxDBClientFactory.create(options);
    }

    @Bean
    public WriteApiBlocking influxWriteApi(InfluxDBClient influxDBClient) {
        return influxDBClient.getWriteApiBlocking();
    }

    @Bean
    public WritePrecision influxWritePrecision() {
        return WritePrecision.MS;
    }
}
