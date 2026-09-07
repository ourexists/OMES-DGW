package com.omes.dgw.protocol.slmp;

import java.util.List;

/** 预计算的一块 SLMP 合并读（同设备码、同字/位访问模式、连续地址）。 */
public record SlmpPollingChunk(
        int deviceCode,
        boolean bitAccess,
        int startDeviceNumber,
        int count,
        List<SlmpPollingTag> tags
) {
}
