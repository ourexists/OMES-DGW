package com.omes.dgw.device.controller.core;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单设备连接熔断上下文（内存态）。
 */
final class DeviceConnectionCircuitState {

    enum Phase {
        /** 正常轮询 */
        CLOSED,
        /** 已熔断停轮询，等待退避后半开重试 */
        OPEN,
        /** 半开探测：已重新启动轮询，等待成功/失败判定 */
        HALF_OPEN,
        /** 超过最大自动重试次数，停止调度，仅支持人工开启连接 */
        ABANDONED
    }

    final Object lock = new Object();
    volatile Phase phase = Phase.CLOSED;
    final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    /** 熔断打开次数（用于指数退避） */
    final AtomicInteger openCount = new AtomicInteger(0);
    volatile long nextRetryAtEpochMs;
    volatile ScheduledFuture<?> recoveryTask;
    volatile String lastProtocol;
    volatile String lastReason;
    /** 本轮轮询是否至少成功读过一次（用于区分「探测中」与「已连通」） */
    volatile boolean pollConfirmed;
}
