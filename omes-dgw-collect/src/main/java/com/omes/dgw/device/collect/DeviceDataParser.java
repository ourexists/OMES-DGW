package com.omes.dgw.device.collect;

import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.entity.DevicePoint;

import java.util.Map;

/**
 * 将协议采集原始数据解析为设备实时快照。
 */
public interface DeviceDataParser {

    /**
     * @param deviceBase   设备缓存快照（头信息 + 采集配置）
     * @param pointsByName 点位配置（key 为点位编码，与采集 tag 名一致；无编码时为名称）
     * @param rawValues    采集结果（key 与点位配置一致）
     * @param error        采集异常信息，无异常为 null
     */
    DeviceRealtimeObj parse(
            DeviceRealtimeObj deviceBase,
            Map<String, DevicePoint> pointsByName,
            Map<String, Object> rawValues,
            String error
    );

}
