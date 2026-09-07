package com.omes.dgw.protocol.modbus;

/**
 * 单轮 Modbus 采集读的运行时统计（指标对齐「逻辑读」、日志对齐 PLC 事务与降级）。
 */
public final class ModbusPollRoundStats {

    private final int plannedChunks;
    private final int plannedFallbackTags;

    private int plcTransactions;
    private int logicalReads;
    private int chunkDegraded;
    private int chunkBatches;
    private int fallbackBatches;
    private int degradeBatches;
    private int singleTagReads;
    /** 块/批读重试中除首次外的尝试次数（仅观测，不计入 {@link #logicalReads}）。 */
    private int readRetryAttempts;

    public ModbusPollRoundStats(int plannedChunks, int plannedFallbackTags) {
        this.plannedChunks = plannedChunks;
        this.plannedFallbackTags = plannedFallbackTags;
    }

    public int plannedChunks() {
        return plannedChunks;
    }

    public int plannedFallbackTags() {
        return plannedFallbackTags;
    }

    public int plannedFallbackBatches(int batchSize) {
        if (plannedFallbackTags <= 0 || batchSize <= 0) {
            return 0;
        }
        return (plannedFallbackTags + batchSize - 1) / batchSize;
    }

    public int plannedChunkBatches(int batchSize) {
        if (plannedChunks <= 0 || batchSize <= 0) {
            return 0;
        }
        return (plannedChunks + batchSize - 1) / batchSize;
    }

    public void onPlcTransaction() {
        plcTransactions++;
    }

    public void onLogicalRead() {
        logicalReads++;
    }

    public void onChunkDegraded() {
        chunkDegraded++;
    }

    public void onChunkBatch() {
        chunkBatches++;
    }

    public void onFallbackBatch() {
        fallbackBatches++;
    }

    public void onDegradeBatch() {
        degradeBatches++;
    }

    public void onSingleTagRead() {
        singleTagReads++;
    }

    public void onReadRetryAttempt() {
        readRetryAttempts++;
    }

    public int plcTransactions() {
        return plcTransactions;
    }

    public int logicalReads() {
        return logicalReads;
    }

    public int chunkDegraded() {
        return chunkDegraded;
    }

    public int chunkBatches() {
        return chunkBatches;
    }

    public int fallbackBatches() {
        return fallbackBatches;
    }

    public int degradeBatches() {
        return degradeBatches;
    }

    public int singleTagReads() {
        return singleTagReads;
    }

    public int readRetryAttempts() {
        return readRetryAttempts;
    }

    public boolean hasDegradationOrRetry() {
        return chunkDegraded > 0 || degradeBatches > 0 || singleTagReads > 0 || readRetryAttempts > 0;
    }
}
