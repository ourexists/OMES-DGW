package com.omes.dgw.collectlog;

import com.omes.dgw.protocol.modbus.ModbusPollRoundStats;

/**
 * 当前轮询线程上的耗时上下文（{@link com.omes.dgw.protocol.AbstractPlc4xPollingProtocolManager#executePoll}）。
 */
public final class PollRoundContext {

    private static final ThreadLocal<PollRoundTiming> TIMING = new ThreadLocal<>();
    private static final ThreadLocal<String> DEVICE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> PROTOCOL = new ThreadLocal<>();
    private static final ThreadLocal<ModbusPollRoundStats> MODBUS_STATS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PARTIAL_READ_FAILURE = ThreadLocal.withInitial(() -> false);

    private PollRoundContext() {
    }

    public static void begin(String deviceId, String protocol) {
        clear();
        TIMING.set(new PollRoundTiming());
        DEVICE_ID.set(deviceId);
        PROTOCOL.set(protocol);
    }

    public static PollRoundTiming timing() {
        PollRoundTiming t = TIMING.get();
        if (t == null) {
            t = new PollRoundTiming();
            TIMING.set(t);
        }
        return t;
    }

    public static String deviceId() {
        return DEVICE_ID.get();
    }

    public static String protocol() {
        return PROTOCOL.get();
    }

    public static void setModbusStats(ModbusPollRoundStats stats) {
        MODBUS_STATS.set(stats);
    }

    public static ModbusPollRoundStats modbusStats() {
        return MODBUS_STATS.get();
    }

    public static void markPartialReadFailure() {
        PARTIAL_READ_FAILURE.set(true);
    }

    public static boolean partialReadFailure() {
        return Boolean.TRUE.equals(PARTIAL_READ_FAILURE.get());
    }

    public static void clear() {
        TIMING.remove();
        DEVICE_ID.remove();
        PROTOCOL.remove();
        MODBUS_STATS.remove();
        PARTIAL_READ_FAILURE.remove();
    }
}
