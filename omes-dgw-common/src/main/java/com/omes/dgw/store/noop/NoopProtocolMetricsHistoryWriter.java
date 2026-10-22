package com.omes.dgw.store.noop;

import java.util.List;

/**
 * 协议指标历史写入空实现。
 */
public final class NoopProtocolMetricsHistoryWriter {

    public static final NoopProtocolMetricsHistoryWriter INSTANCE = new NoopProtocolMetricsHistoryWriter();

    private NoopProtocolMetricsHistoryWriter() {
    }

    public void writeMetrics(List<?> points) {
        // no-op
    }
}
