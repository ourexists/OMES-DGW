package com.omes.dgw.influx;

/**
 * 根据查询时间跨度与目标点数，计算 Influx {@code aggregateWindow} 的窗口宽度。
 */
public final class SnapshotChartAggregateResolver {

    private static final long[] NICE_STEP_SEC = {
            1, 2, 5, 10, 15, 30,
            60, 120, 300, 600, 900, 1800,
            3600, 7200, 14_400, 21_600, 43_200,
            86_400
    };

    private SnapshotChartAggregateResolver() {
    }

    /**
     * @param spanSeconds           查询时间跨度（秒）
     * @param collectIntervalSec    设备采集周期（秒）
     * @param maxPointsPerSeries    单序列折线图最大点数
     * @param fluxEveryOrNull       非聚合时为 null；聚合时为 Influx duration（如 {@code 30s}、{@code 5m}）
     */
    public record ResolveResult(boolean aggregated, int sampleIntervalSec, String fluxEveryOrNull) {
    }

    public static ResolveResult resolve(long spanSeconds, int collectIntervalSec, int maxPointsPerSeries) {
        return resolve(spanSeconds, collectIntervalSec, maxPointsPerSeries, null, null);
    }

    /**
     * @param requestAggregateIntervalSec 为 null 时自动计算；为 0 时不聚合；为正数时按该窗口聚合
     * @param requestMaxPointsPerSeries     仅自动模式生效，覆盖默认最大点数
     */
    public static ResolveResult resolve(
            long spanSeconds,
            int collectIntervalSec,
            int defaultMaxPointsPerSeries,
            Integer requestAggregateIntervalSec,
            Integer requestMaxPointsPerSeries
    ) {
        int collect = Math.max(1, collectIntervalSec);
        if (requestAggregateIntervalSec != null) {
            if (requestAggregateIntervalSec <= 0) {
                return new ResolveResult(false, collect, null);
            }
            long stepSec = alignNiceStep(Math.max(requestAggregateIntervalSec, collect));
            return new ResolveResult(true, (int) stepSec, toFluxDuration(stepSec));
        }

        int maxPoints = Math.max(10, requestMaxPointsPerSeries != null
                ? requestMaxPointsPerSeries
                : defaultMaxPointsPerSeries);
        long span = Math.max(1, spanSeconds);

        long estimatedRawPoints = span / collect;
        if (estimatedRawPoints <= maxPoints) {
            return new ResolveResult(false, collect, null);
        }

        long minStep = (long) Math.ceil((double) span / maxPoints);
        long stepSec = alignNiceStep(Math.max(minStep, collect));
        return new ResolveResult(true, (int) stepSec, toFluxDuration(stepSec));
    }

    static long alignNiceStep(long minStepSec) {
        for (long step : NICE_STEP_SEC) {
            if (step >= minStepSec) {
                return step;
            }
        }
        return minStepSec;
    }

    static String toFluxDuration(long stepSec) {
        if (stepSec <= 0) {
            return "1s";
        }
        if (stepSec % 86_400 == 0) {
            return (stepSec / 86_400) + "d";
        }
        if (stepSec % 3600 == 0) {
            return (stepSec / 3600) + "h";
        }
        if (stepSec % 60 == 0) {
            return (stepSec / 60) + "m";
        }
        return stepSec + "s";
    }
}
