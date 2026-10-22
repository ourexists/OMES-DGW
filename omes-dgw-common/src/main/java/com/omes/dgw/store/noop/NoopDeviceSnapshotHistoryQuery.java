package com.omes.dgw.store.noop;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 设备点位历史查询空实现。
 */
public final class NoopDeviceSnapshotHistoryQuery {

    public static final NoopDeviceSnapshotHistoryQuery INSTANCE = new NoopDeviceSnapshotHistoryQuery();

    private NoopDeviceSnapshotHistoryQuery() {
    }

    public List<?> queryPointSeries(
            String deviceId,
            Set<String> pointNames,
            Instant start,
            Instant end,
            String fluxEveryOrNull
    ) {
        return Collections.emptyList();
    }
}
