package com.omes.dgw.protocol.slmp;

import com.omes.dgw.device.entity.DevicePoint;

/** 预计算的 SLMP 轮询点位（含设备码、地址、跨度）。 */
public record SlmpPollingTag(
        String tagName,
        int deviceCode,
        int deviceNumber,
        boolean bitAccess,
        /** 字访问时为寄存器字数；位访问时为 1 */
        int spanUnits,
        DevicePoint point,
        /** 字内位 0–15；非字内位为 -1 */
        int bitInWord
) {

    public SlmpPollingTag(
            String tagName, int deviceCode, int deviceNumber, boolean bitAccess, int spanUnits, DevicePoint point
    ) {
        this(tagName, deviceCode, deviceNumber, bitAccess, spanUnits, point, -1);
    }

    public int endDeviceNumber() {
        return bitAccess ? deviceNumber : deviceNumber + spanUnits - 1;
    }
}
