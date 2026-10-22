package com.omes.dgw.collectlog;

import com.omes.dgw.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定期输出 JVM 堆内存与 GC 统计，便于关联采集卡顿与内存压力。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.collect.diagnostics", name = "memory-log-enabled", havingValue = "true", matchIfMissing = true)
public class JvmDiagnosticsScheduler {

    private final int intervalSec;

    public JvmDiagnosticsScheduler(AppProperties appProperties) {
        int configured = appProperties.getCollect().getDiagnostics().getMemoryLogIntervalSec();
        this.intervalSec = Math.max(60, configured);
    }

    @Scheduled(fixedDelayString = "${app.collect.diagnostics.memory-log-interval-sec:300}000")
    public void logMemoryAndGc() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        Runtime runtime = Runtime.getRuntime();

        long heapUsedMb = heap.getUsed() / (1024 * 1024);
        long heapMaxMb = heap.getMax() > 0 ? heap.getMax() / (1024 * 1024) : runtime.maxMemory() / (1024 * 1024);
        long nonHeapUsedMb = nonHeap.getUsed() / (1024 * 1024);
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();

        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        String gcSummary = gcs.stream()
                .map(g -> g.getName() + ":count=" + g.getCollectionCount() + ",timeMs=" + g.getCollectionTime())
                .collect(Collectors.joining("; "));

        log.info(StructuredCollectLog.format(
                "event=jvm_memory",
                "heapUsedMb", heapUsedMb,
                "heapMaxMb", heapMaxMb,
                "nonHeapUsedMb", nonHeapUsedMb,
                "threads", threads,
                "gc", gcSummary,
                "intervalSec", intervalSec
        ));
    }
}
