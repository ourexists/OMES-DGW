package com.omes.dgw.collectlog;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.protocol.modbus.ModbusPollRoundStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采集关键路径结构化日志（key=value，便于 grep / 日志平台解析）。
 */
@Slf4j
public final class StructuredCollectLog {

    public enum PollTimingMode {
        /** 每轮 INFO */
        always,
        /** 失败、降级、慢轮询、点位丢失时 INFO，成功轮 DEBUG */
        on_issue,
        /** 仅 DEBUG */
        debug_only,
        /** 关闭耗时分解日志 */
        off
    }

    private StructuredCollectLog() {
    }

    public static String format(String event, Object... kv) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("event", event);
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                if (kv[i] != null) {
                    fields.put(String.valueOf(kv[i]), kv[i + 1] != null ? String.valueOf(kv[i + 1]) : "");
                }
            }
        }
        return format(fields);
    }

    public static String format(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(128);
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) {
                sb.append(' ');
            }
            first = false;
            sb.append(e.getKey()).append('=');
            appendEscaped(sb, e.getValue());
        }
        return sb.toString();
    }

    private static void appendEscaped(StringBuilder sb, String value) {
        if (value == null) {
            return;
        }
        if (value.indexOf(' ') >= 0 || value.indexOf('=') >= 0) {
            sb.append('"').append(value.replace("\"", "\\\"")).append('"');
        } else {
            sb.append(value);
        }
    }

    public static void logPollRound(
            AppProperties.Collect.CollectDiagnostics cfg,
            String deviceId,
            String protocol,
            String connectId,
            PollRoundTiming timing,
            boolean success,
            int valuesRead,
            int totalPoints,
            boolean partialFailure,
            ModbusPollRoundStats modbusStats,
            int pollIntervalSec
    ) {
        PollTimingMode mode = cfg != null ? parseTimingMode(cfg.getPollTimingMode()) : PollTimingMode.on_issue;
        if (mode == PollTimingMode.off) {
            return;
        }
        boolean degraded = modbusStats != null && modbusStats.hasDegradationOrRetry();
        boolean slow = pollIntervalSec > 0 && timing != null
                && timing.getTotalMs() > pollIntervalSec * 800L;
        boolean onIssue = !success || partialFailure || degraded || slow;

        if (mode == PollTimingMode.debug_only) {
            if (log.isDebugEnabled()) {
                emitPollRound(deviceId, protocol, connectId, timing, success, valuesRead, totalPoints,
                        partialFailure, modbusStats, false);
            }
            return;
        }
        if (mode == PollTimingMode.always || onIssue) {
            emitPollRound(deviceId, protocol, connectId, timing, success, valuesRead, totalPoints,
                    partialFailure, modbusStats, false);
        } else if (log.isDebugEnabled()) {
            emitPollRound(deviceId, protocol, connectId, timing, success, valuesRead, totalPoints,
                    partialFailure, modbusStats, true);
        }
    }

    private static PollTimingMode parseTimingMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return PollTimingMode.on_issue;
        }
        try {
            return PollTimingMode.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return PollTimingMode.on_issue;
        }
    }

    private static void emitPollRound(
            String deviceId,
            String protocol,
            String connectId,
            PollRoundTiming timing,
            boolean success,
            int valuesRead,
            int totalPoints,
            boolean partialFailure,
            ModbusPollRoundStats modbusStats,
            boolean debug
    ) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("event", "poll_round");
        f.put("deviceId", deviceId);
        f.put("protocol", protocol);
        f.put("connectId", connectId);
        f.put("success", String.valueOf(success));
        f.put("partialFailure", String.valueOf(partialFailure));
        if (timing != null) {
            f.put("connectMs", String.valueOf(timing.getConnectMs()));
            f.put("readMs", String.valueOf(timing.getReadMs()));
            f.put("parseMs", String.valueOf(timing.getParseMs()));
            f.put("cacheMs", String.valueOf(timing.getCacheMs()));
            f.put("totalMs", String.valueOf(timing.getTotalMs()));
        }
        f.put("valuesRead", valuesRead + "/" + totalPoints);
        if (modbusStats != null) {
            f.put("plcTx", String.valueOf(modbusStats.plcTransactions()));
            f.put("logicalReads", String.valueOf(modbusStats.logicalReads()));
            f.put("chunkDegraded", String.valueOf(modbusStats.chunkDegraded()));
            f.put("readRetries", String.valueOf(modbusStats.readRetryAttempts()));
        }
        String line = format(f);
        if (debug) {
            log.debug(line);
        } else {
            log.info(line);
        }
    }
}
