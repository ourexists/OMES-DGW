package com.omes.dgw.influx;

/**
 * Influx 运行时能力 SPI：由 {@code omes-dgw-plugin-influx} 提供实现。
 * 主应用不得直接依赖 {@code com.influxdb.*}。
 */
public interface InfluxRuntime {

    /** 客户端是否已就绪（插件已加载且 bean 已注册）。 */
    default boolean available() {
        return true;
    }

    /** 探测 InfluxDB 健康（HTTP health）。 */
    boolean ping();

    String org();

    String bucket();
}
