package com.omes.dgw.store.noop;

/**
 * Influx 不可用时的空实现：{@code available()/ping()} 恒为 false。
 * 由门户或 server 的 {@code @ConditionalOnMissingBean} 适配为 {@code InfluxRuntime} SPI。
 */
public final class NoopInfluxRuntime {

    public static final NoopInfluxRuntime INSTANCE = new NoopInfluxRuntime();

    private NoopInfluxRuntime() {
    }

    public boolean available() {
        return false;
    }

    public boolean ping() {
        return false;
    }

    public String org() {
        return "";
    }

    public String bucket() {
        return "";
    }
}
