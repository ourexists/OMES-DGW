package com.omes.dgw.device.write;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.entity.DevicePointWriteCommand;
import com.omes.dgw.device.service.DevicePointWriteService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按任务划分顺序执行：同一任务内严格按 SEQ_NO 串行，不同任务可交错调度。
 * <p>
 * 停机重启恢复：进程退出前将未完成的 RUNNING 置回 PENDING；启动时再次扫描并唤醒有待执行的任务
 * （异常杀进程时依赖启动阶段恢复）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.collect.point-write", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DevicePointWriteExecutor implements ApplicationRunner {

    private final DevicePointWriteService writeService;
    private final long idlePollIntervalMs;
    private final BlockingQueue<String> signal = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, Boolean> signaledTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger roundRobin = new AtomicInteger();
    private Thread worker;

    public DevicePointWriteExecutor(DevicePointWriteService writeService, AppProperties appProperties) {
        this.writeService = writeService;
        AppProperties.PointWrite cfg = appProperties.getCollect().getPointWrite();
        this.idlePollIntervalMs = Math.max(100L, cfg.getIdlePollIntervalMs());
    }

    @Override
    public void run(ApplicationArguments args) {
        recoverOnRestart();
        worker = Thread.ofVirtual().name("device-point-write-executor").start(this::loop);
        log.info("设备控制点位写入执行线程已启动（按任务顺序）, idlePollIntervalMs={}", idlePollIntervalMs);
        wakeRunnableTasks();
    }

    public void wake(String taskId) {
        if (StringUtils.hasText(taskId)) {
            signaledTasks.put(taskId, Boolean.TRUE);
            signal.offer(taskId);
        } else {
            signal.offer("");
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        wake(null);
        if (worker != null) {
            try {
                worker.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int recovered = writeService.recoverStaleRunningCommands();
        if (recovered > 0) {
            log.info("控制点位写入优雅停机: 已重置 {} 条未完成的 RUNNING 指令，重启后将续跑", recovered);
        }
    }

    /** 启动恢复：清理上次进程中断遗留的 RUNNING，并唤醒仍有 PENDING 的任务。 */
    private void recoverOnRestart() {
        int recovered = writeService.recoverStaleRunningCommands();
        if (recovered > 0) {
            log.info("控制点位写入启动恢复: 已处理 {} 条中断指令", recovered);
        }
    }

    private void wakeRunnableTasks() {
        List<String> runnableTaskIds = writeService.listRunnableTaskIds();
        if (runnableTaskIds.isEmpty()) {
            wake(null);
            return;
        }
        for (String taskId : runnableTaskIds) {
            wake(taskId);
        }
    }

    private void loop() {
        while (running.get()) {
            try {
                if (!drainOne()) {
                    signal.poll(idlePollIntervalMs, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("控制点位写入执行异常: {}", e.getMessage(), e);
            }
        }
    }

    private boolean drainOne() {
        List<String> runnableTaskIds = writeService.listRunnableTaskIds();
        if (runnableTaskIds.isEmpty()) {
            signaledTasks.clear();
            return false;
        }
        String taskId = pickTask(runnableTaskIds);
        Optional<DevicePointWriteCommand> claimed = writeService.claimNextPending(taskId);
        if (claimed.isEmpty()) {
            signaledTasks.remove(taskId);
            return false;
        }
        DevicePointWriteCommand command = claimed.get();
        writeService.execute(command);
        signaledTasks.remove(command.getTaskId());
        if (writeService.listRunnableTaskIds().contains(command.getTaskId())) {
            wake(command.getTaskId());
        }
        return true;
    }

    private String pickTask(List<String> runnableTaskIds) {
        for (String signaled : signaledTasks.keySet()) {
            if (runnableTaskIds.contains(signaled)) {
                return signaled;
            }
        }
        int index = Math.floorMod(roundRobin.getAndIncrement(), runnableTaskIds.size());
        return runnableTaskIds.get(index);
    }
}
