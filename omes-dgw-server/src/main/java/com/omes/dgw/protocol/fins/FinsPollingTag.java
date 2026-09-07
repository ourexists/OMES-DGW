package com.omes.dgw.protocol.fins;

import com.omes.dgw.device.entity.DevicePoint;

/** 预计算的 FINS 轮询点位。 */
public record FinsPollingTag(
        String tagName,
        int memoryAreaCode,
        int wordAddress,
        Integer bitOffset,
        boolean bitAccess,
        int spanWords,
        DevicePoint point
) {

    public int endWordAddress() {
        return bitAccess ? wordAddress : wordAddress + spanWords - 1;
    }

    /** 位访问时用于合并读的字区码（如 DM 位 0x02 → 字区 0x82）。 */
    public int wordAreaCodeForRead() {
        return FinsAddressHelper.wordAreaCodeForBitArea(memoryAreaCode);
    }
}
