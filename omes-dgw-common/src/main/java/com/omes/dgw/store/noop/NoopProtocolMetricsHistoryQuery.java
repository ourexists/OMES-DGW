package com.omes.dgw.store.noop;

import java.util.Collections;
import java.util.List;

/**
 * 协议指标历史查询空实现。
 */
public final class NoopProtocolMetricsHistoryQuery {

    public static final NoopProtocolMetricsHistoryQuery INSTANCE = new NoopProtocolMetricsHistoryQuery();

    private NoopProtocolMetricsHistoryQuery() {
    }

    public List<?> queryDeviceSeries(String deviceId, int minutes) {
        return Collections.emptyList();
    }

    public List<?> querySummarySeries(int minutes) {
        return Collections.emptyList();
    }

    public List<?> queryLatestPerDevice(int minutes) {
        return Collections.emptyList();
    }
}
