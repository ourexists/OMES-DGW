package com.omes.dgw.collectlog;

import lombok.Getter;
import lombok.Setter;

/**
 * 单轮采集各阶段耗时（毫秒），供结构化日志输出。
 */
@Getter
@Setter
public class PollRoundTiming {

    private long connectMs;
    private long readMs;
    private long parseMs;
    private long cacheMs;
    private long totalMs;

    public void addConnectMs(long ms) {
        connectMs += Math.max(0, ms);
    }
}
