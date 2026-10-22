package com.omes.dgw.timeseries;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 设备点位快照历史查询 SPI。
 */
public interface DeviceSnapshotHistoryQuery {

    /**
     * @param pointNames 为空则查询该设备全部点位；非空则仅查询指定点位（Influx point_name 标签）
     */
    List<SnapshotPoint> queryPointSeries(
            String deviceId,
            Set<String> pointNames,
            Instant start,
            Instant end,
            String fluxEveryOrNull
    );

    record SnapshotPoint(Instant time, String pointName, String address, double value) {
    }
}
