package com.omes.dgw.protocol.fins;

import java.util.List;

/** 预计算的一块 FINS 合并读（同内存区、连续字地址或同字内多位）。 */
public record FinsPollingChunk(
        int memoryAreaCode,
        int startWordAddress,
        int wordCount,
        List<FinsPollingTag> tags
) {
}
