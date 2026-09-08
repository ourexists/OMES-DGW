package com.omes.dgw.timeseries;

import com.omes.dgw.device.cache.DeviceRealtimeObj;

import java.util.List;

/**
 * 设备实时快照历史写入 SPI（Influx measurement 由插件实现）。
 */
public interface DeviceRealtimeHistoryWriter {

    String MEASUREMENT_DEVICE = "device_realtime";
    String MEASUREMENT_POINT = "device_point";

    void writeSnapshots(List<DeviceRealtimeObj> snapshots);
}
