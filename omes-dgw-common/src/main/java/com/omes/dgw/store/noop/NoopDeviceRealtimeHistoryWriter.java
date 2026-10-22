package com.omes.dgw.store.noop;

import java.util.List;

/**
 * 设备快照历史写入空实现（memory 门户或不启用 Influx 时）。
 */
public final class NoopDeviceRealtimeHistoryWriter {

    public static final NoopDeviceRealtimeHistoryWriter INSTANCE = new NoopDeviceRealtimeHistoryWriter();

    private NoopDeviceRealtimeHistoryWriter() {
    }

    public void writeSnapshots(List<?> snapshots) {
        // no-op
    }
}
