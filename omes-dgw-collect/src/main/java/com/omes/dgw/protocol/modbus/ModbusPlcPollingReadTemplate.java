package com.omes.dgw.protocol.modbus;

import com.omes.dgw.collectlog.PlcResponseDiagnostics;
import com.omes.dgw.collectlog.PollRoundContext;
import com.omes.dgw.protocol.PlcConnectionFaultException;
import com.omes.dgw.protocol.PlcTagResponseSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Modbus 轮询读公共模板：多 tag 一次 {@link PlcReadRequest}、可选重试、严格/宽松成功判定。
 */
@Slf4j
public final class ModbusPlcPollingReadTemplate {

    private ModbusPlcPollingReadTemplate() {
    }

    /** 单次 PLC 读尝试（由外层决定是否重试、记指标）。 */
    @FunctionalInterface
    public interface ReadOnceAttempt {
        boolean execute() throws Exception;
    }

    /** 每轮读指标回调（通常对接 {@code recordRead}）。 */
    @FunctionalInterface
    public interface ReadMetricsHook {
        void record(boolean success, long startNanos);
    }

    /** 重试间隔（通常对接 {@code awaitRetryDelay}）。 */
    @FunctionalInterface
    public interface RetryDelayHook {
        void await(long delayMs) throws InterruptedException;
    }

    /** 多 tag 批读中的一条。 */
    public record TagReadOp(String tagName, String plcAddress) {
    }

    /** 从响应块解码为点位值；返回 null 表示该 tag 未成功。 */
    @FunctionalInterface
    public interface TagBlockDecoder {
        Object decode(TagReadOp op, Object block);
    }

    /** 批读成功判定：全部解码成功 vs 至少新增一个点位。 */
    public enum BatchSuccessMode {
        ALL_DECODED,
        ANY_NEW_VALUE
    }

    public static boolean withRetry(
            int maxAttempts,
            long retryDelayMs,
            ReadMetricsHook metrics,
            RetryDelayHook retryDelay,
            String retrySubject,
            String deviceId,
            ReadOnceAttempt attempt,
            ModbusPollRoundStats roundStats
    ) throws Exception {
        long overallStartNanos = System.nanoTime();
        boolean finalSuccess = false;
        try {
            for (int attemptNo = 1; attemptNo <= maxAttempts; attemptNo++) {
                if (attemptNo > 1) {
                    if (roundStats != null) {
                        roundStats.onReadRetryAttempt();
                    }
                }
                try {
                    finalSuccess = attempt.execute();
                    if (finalSuccess) {
                        return true;
                    }
                } catch (Exception e) {
                    if (attemptNo >= maxAttempts) {
                        throw e;
                    }
                    log.debug("Modbus {} read error attempt {}/{} deviceId={}: {}",
                            retrySubject, attemptNo, maxAttempts, deviceId, e.getMessage());
                }
                if (attemptNo < maxAttempts) {
                    retryDelay.await(retryDelayMs);
                }
            }
            return false;
        } finally {
            if (metrics != null) {
                metrics.record(finalSuccess, overallStartNanos);
            }
        }
    }

    /**
     * 一次提交多个 tag 的 PLC 读。
     *
     * @param recordMetrics 为 true 时在 finally 中调用 {@code metrics}（重试路径应传 false，由 {@link #withRetry} 记）
     */
    public static boolean readTagBatch(
            PlcConnection connection,
            int requestTimeoutMs,
            String deviceId,
            List<TagReadOp> ops,
            TagBlockDecoder decoder,
            BatchSuccessMode successMode,
            boolean swallowException,
            String failureLog,
            Map<String, Object> result,
            boolean recordMetrics,
            ReadMetricsHook metrics,
            ModbusPollRoundStats roundStats
    ) throws Exception {
        if (ops.isEmpty()) {
            return false;
        }
        long startNanos = System.nanoTime();
        boolean anyDecoded = false;
        int before = result.size();
        try {
            PlcReadRequest.Builder builder = connection.readRequestBuilder();
            for (TagReadOp op : ops) {
                builder.addTagAddress(op.tagName(), op.plcAddress());
            }
            if (roundStats != null) {
                roundStats.onPlcTransaction();
            }
            PlcReadResponse response = builder.build().execute().get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            PlcResponseDiagnostics.stashLastReadResponse(response);
            List<String> tagNames = new ArrayList<>(ops.size());
            for (TagReadOp op : ops) {
                tagNames.add(op.tagName());
            }
            PlcResponseDiagnostics.logNonOkTags(
                    deviceId, PollRoundContext.protocol(), response, tagNames, null);
            if (PlcTagResponseSupport.batchReadIndicatesConnectionFault(response, tagNames)) {
                throw new PlcConnectionFaultException("Modbus 批读返回设备/协议级异常响应，将重建连接");
            }
            boolean allDecoded = true;
            for (TagReadOp op : ops) {
                Object block = PlcTagResponseSupport.getObjectLenient(response, op.tagName());
                if (block == null && !PlcTagResponseSupport.isTagOk(response, op.tagName())) {
                    allDecoded = false;
                    continue;
                }
                Object value = decoder.decode(op, block);
                if (value != null) {
                    result.put(op.tagName(), value);
                    anyDecoded = true;
                } else {
                    allDecoded = false;
                }
            }
            return successMode == BatchSuccessMode.ALL_DECODED ? allDecoded : result.size() > before;
        } catch (Exception e) {
            if (!swallowException || e instanceof PlcConnectionFaultException) {
                throw e;
            }
            log.debug("{} deviceId={}: {}", failureLog, deviceId, e.getMessage());
            return false;
        } finally {
            if (recordMetrics && metrics != null) {
                metrics.record(anyDecoded, startNanos);
            }
        }
    }

    public static boolean readSingleTag(
            PlcConnection connection,
            int requestTimeoutMs,
            String deviceId,
            TagReadOp op,
            TagBlockDecoder decoder,
            String traceFailureLog,
            Map<String, Object> result,
            ReadMetricsHook metrics,
            ModbusPollRoundStats roundStats
    ) throws Exception {
        long startNanos = System.nanoTime();
        boolean success = false;
        try {
            if (roundStats != null) {
                roundStats.onPlcTransaction();
            }
            PlcReadResponse response = connection.readRequestBuilder()
                    .addTagAddress(op.tagName(), op.plcAddress())
                    .build()
                    .execute()
                    .get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            PlcResponseDiagnostics.stashLastReadResponse(response);
            PlcResponseDiagnostics.logNonOkTags(
                    deviceId,
                    PollRoundContext.protocol(),
                    response,
                    List.of(op.tagName()),
                    Map.of(op.tagName(), op.plcAddress()));
            if (PlcTagResponseSupport.batchReadIndicatesConnectionFault(response, List.of(op.tagName()))) {
                throw new PlcConnectionFaultException(
                        "Modbus 点读返回设备/协议级异常响应 tag=" + op.tagName());
            }
            Object block = PlcTagResponseSupport.getObjectLenient(response, op.tagName());
            if (block == null && !PlcTagResponseSupport.isTagOk(response, op.tagName())) {
                return false;
            }
            Object value = decoder.decode(op, block);
            if (value != null) {
                result.put(op.tagName(), value);
                success = true;
            }
            return success;
        } catch (PlcConnectionFaultException e) {
            throw e;
        } catch (Exception e) {
            log.trace("{} tag={} deviceId={}: {}", traceFailureLog, op.tagName(), deviceId, e.getMessage());
            return false;
        } finally {
            if (metrics != null) {
                metrics.record(success, startNanos);
            }
        }
    }
}
