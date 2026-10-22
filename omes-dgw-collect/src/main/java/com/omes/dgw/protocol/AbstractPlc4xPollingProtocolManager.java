package com.omes.dgw.protocol;

import com.omes.dgw.device.connect.ProtocolConnect;
import com.omes.dgw.device.connect.ProtocolWriteRequest;
import com.omes.dgw.device.connect.ProtocolWriteResult;
import com.omes.dgw.device.connect.DeviceProtocolConnectFactory;

import com.omes.dgw.collectlog.PlcResponseDiagnostics;
import com.omes.dgw.collectlog.PointValueChangeDetector;
import com.omes.dgw.collectlog.PollRoundContext;
import com.omes.dgw.collectlog.PollRoundTiming;
import com.omes.dgw.collectlog.StructuredCollectLog;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.RealtimeCacheSerializationException;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.collect.DeviceDataParser;
import com.omes.dgw.device.collect.PlcDeviceDataParser;
import com.omes.dgw.device.controller.core.DeviceConnectionFailureHandler;
import com.omes.dgw.device.enums.DeviceConnectionStatus;
import com.omes.dgw.device.dto.DeviceConnectionTestVo;
import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.metrics.ProtocolCollectMetrics;
import com.omes.dgw.metrics.ProtocolPollSkipMetrics;
import com.omes.dgw.codec.modbus.ModbusAddressHelper;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcConnectionManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PLC4X 轮询协议管理器：从设备台账加载点位，采集后写入本地实时缓存。
 */
@Slf4j
public abstract class AbstractPlc4xPollingProtocolManager implements ProtocolManager {

    private static final long WRITE_READ_DELAY_MS = 500L;
    private static final int DEFAULT_READ_CHUNK_SIZE = 100;
    private static final int MAX_CONNECTION_READ_RETRIES = 2;
    private static final long CONNECTION_RETRY_DELAY_MS = 300L;

    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();
    protected final Map<String, ConnectSpec> specMap = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> readExecutorMap = new ConcurrentHashMap<>();
    /** 避免 cron/写后读叠加重入，阻塞设备读线程队列。 */
    private final Map<String, AtomicBoolean> pollInProgress = new ConcurrentHashMap<>();
    /** 防重入连续跳过次数（成功开始新一轮采集时归零）。 */
    private final Map<String, AtomicInteger> pollGateSkipStreak = new ConcurrentHashMap<>();
    /** 轮询 watchdog：在独立线程上对 Future 做 get(timeout)，避免阻塞 cron 调度线程。 */
    private ExecutorService pollWatchdog;
    /** 超时后置 false，避免卡死线程稍后完成时重复记指标。 */
    private final Map<String, Boolean> pollMetricsPending = new ConcurrentHashMap<>();

    private static final long POLL_ROUND_TIMEOUT_BUFFER_SEC = 30L;
    private static final int DEFAULT_COLLECT_INTERVAL_SEC = 5;

    private static int resolveCollectIntervalSec(Integer intervalSec) {
        return intervalSec != null && intervalSec > 0 ? intervalSec : DEFAULT_COLLECT_INTERVAL_SEC;
    }

    private final DeviceRealtimeCache realtimeCacheService;
    private final DeviceDataParser deviceDataParser;
    private final ProtocolCollectMetrics collectMetrics;
    private final ProtocolPollSkipMetrics pollSkipMetrics;
    private final int pollSkipWarnThreshold;
    private final PlcConnectionManager connectionManager;
    private final DeviceConnectionFailureHandler connectionFailureHandler;
    private final int readTimeoutMs;
    private final String threadPrefix;
    private final int pollingSchedulerPoolSize;
    /** 单轮轮询 watchdog 超时（秒），基于连接租约 + 缓冲。 */
    private final int pollRoundTimeoutSec;
    private final AppProperties.Collect.CollectDiagnostics collectDiagnostics;

    protected AbstractPlc4xPollingProtocolManager(
            DeviceRealtimeCache realtimeCacheService,
            DeviceDataParser deviceDataParser,
            ProtocolCollectMetrics collectMetrics,
            ProtocolPollSkipMetrics pollSkipMetrics,
            DeviceConnectionFailureHandler connectionFailureHandler,
            int readTimeoutMs,
            String threadPrefix,
            AppProperties.Collect collectConfig) {
        AppProperties.Collect collect = collectConfig != null ? collectConfig : new AppProperties.Collect();
        AppProperties.Connection connectionCache = collect.getConnection();
        this.realtimeCacheService = realtimeCacheService;
        this.deviceDataParser = deviceDataParser;
        this.collectMetrics = collectMetrics;
        this.pollSkipMetrics = pollSkipMetrics;
        this.pollSkipWarnThreshold = Math.max(1, collect.getPollSkipWarnThreshold());
        this.connectionFailureHandler = connectionFailureHandler;
        this.readTimeoutMs = readTimeoutMs;
        this.threadPrefix = threadPrefix;
        this.pollingSchedulerPoolSize = Math.max(1, collect.getPollingSchedulerPoolSize());
        AppProperties.Connection cache = connectionCache != null
                ? connectionCache
                : new AppProperties.Connection();
        int leaseSec = Math.max(30, cache.getPlcConnectionMaxLeaseSec());
        this.pollRoundTimeoutSec = (int) (leaseSec + POLL_ROUND_TIMEOUT_BUFFER_SEC);
        this.connectionManager = PlcConnectionCacheFactory.create(
                cache.getPlcConnectionMaxLeaseSec(),
                cache.getPlcConnectionMaxWaitSec(),
                cache.getPlcConnectionMaxIdleSec());
        this.collectDiagnostics = collect.getDiagnostics() != null
                ? collect.getDiagnostics()
                : new AppProperties.Collect.CollectDiagnostics();
    }

    protected abstract ConnectSpec buildConnectSpec(ProtocolConnect connect, Map<String, String> tagAddresses);

    protected String convertAddress(String address) {
        return address;
    }

    /** 将台账点位地址转为协议读地址（Modbus 子类默认走 PLC4X 标签格式）。 */
    protected String resolveTagAddress(DevicePoint point) {
        return ModbusAddressHelper.toPlc4xAddress(point.getAddress(), point.getDataType());
    }

    /**
     * 按需读（如 scan-points）使用的连接池。默认与轮询共用缓存。
     */
    protected PlcConnectionManager connectionManagerForOnDemandRead() {
        return connectionManager;
    }

    @Override
    public DeviceConnectionTestVo testConnection(ProtocolConnect connect) {
        if (connect == null || !StringUtils.hasText(connect.getUri())) {
            return DeviceConnectionTestVo.fail("连接地址不能为空");
        }
        if (!supports(connect.getProtocol())) {
            return DeviceConnectionTestVo.fail("不支持的协议: " + connect.getProtocol());
        }
        ConnectSpec spec = buildConnectSpec(connect, Map.of());
        if (spec == null || !StringUtils.hasText(spec.connectionUrl)) {
            return DeviceConnectionTestVo.fail("无法解析连接参数");
        }
        long startNanos = System.nanoTime();
        try (PlcConnection connection = connectionManager.getConnection(spec.connectionUrl)) {
            if (!connection.isConnected()) {
                return DeviceConnectionTestVo.fail("无法建立连接");
            }
            if (!connection.getMetadata().isReadSupported()) {
                return DeviceConnectionTestVo.fail("连接已建立，但不支持读取");
            }
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            return DeviceConnectionTestVo.ok("连接成功", latencyMs);
        } catch (Exception e) {
            log.debug("{} test connection failed: {}", protocol(), e.getMessage());
            String message = e.getMessage() != null ? e.getMessage() : "连接失败";
            return DeviceConnectionTestVo.fail(message);
        }
    }

    @PostConstruct
    public void init() {
        pollWatchdog = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, threadPrefix + "watchdog");
            t.setDaemon(true);
            return t;
        });
        scheduler.setPoolSize(pollingSchedulerPoolSize);
        scheduler.setThreadNamePrefix(threadPrefix);
        scheduler.setDaemon(true);
        scheduler.initialize();
    }

    @PreDestroy
    public void destroy() {
        stopAll();
        readExecutorMap.forEach((id, ex) -> shutdownExecutorService(ex));
        readExecutorMap.clear();
        pollInProgress.clear();
        pollGateSkipStreak.clear();
        pollMetricsPending.clear();
        shutdownExecutorService(pollWatchdog);
        pollWatchdog = null;
        try {
            scheduler.shutdown();
        } catch (Exception ignored) {
        }
        closeConnectionManager(connectionManager);
    }

    private static void closeConnectionManager(PlcConnectionManager manager) {
        if (manager instanceof CachedPlcConnectionManager cached) {
            try {
                cached.close();
            } catch (Exception e) {
                log.debug("Close PLC connection cache failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized boolean start(ProtocolConnect connect) {
        return start(connect, Duration.ZERO);
    }

    /**
     * @param initialDelay 首次建连推迟；为 0 时保持原行为（立即探测一次，并按采集周期调度后续轮询）
     */
    public synchronized boolean start(ProtocolConnect connect, Duration initialDelay) {
        if (connect == null || !StringUtils.hasText(connect.getId())) {
            return false;
        }
        String connectId = connect.getId();
        if (taskMap.containsKey(connectId)) {
            log.debug("{} polling already running, connectId={}", protocol(), connectId);
            return false;
        }
        if (!StringUtils.hasText(connect.getUri())) {
            log.warn("{} polling skipped: uri empty, connectId={}", protocol(), connectId);
            return false;
        }
        if (!supports(connect.getProtocol())) {
            return false;
        }

        int pollIntervalSec = resolveCollectIntervalSec(connect.getCollectIntervalSec());
        if (pollIntervalSec <= 0) {
            log.warn("{} polling skipped: invalid collect interval, connectId={}", protocol(), connectId);
            return false;
        }

        ConnectSpec spec = buildDeviceConnectSpec(connect);
        if (spec == null) {
            log.warn("{} polling skipped: connect spec unavailable, connectId={}", protocol(), connectId);
            return false;
        }
        spec.pollIntervalSec = pollIntervalSec;

        ExecutorService readExecutor = createReadExecutor(connectId);
        readExecutorMap.put(connectId, readExecutor);

        Duration delay = initialDelay == null || initialDelay.isNegative() ? Duration.ZERO : initialDelay;
        Runnable trigger = () -> submitPollWithTimeout(connectId, spec);
        Duration period = Duration.ofSeconds(pollIntervalSec);
        Duration firstDelay = delay.isZero() ? period : delay;
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                trigger,
                Instant.now().plus(firstDelay),
                period
        );
        taskMap.put(connectId, future);
        specMap.put(connectId, spec);
        if (!connectionFailureHandler.isHalfOpen(connectId)) {
            connectionFailureHandler.reset(connectId);
        }
        if (spec.tagAddresses.isEmpty()) {
            log.info("{} polling started (connectivity-only, no points), connectId={}, intervalSec={}, firstDelayMs={}, connectionUrl={}",
                    protocol(), connectId, pollIntervalSec, firstDelay.toMillis(), redactConnectionUrl(spec.connectionUrl));
        } else {
            log.info("{} polling started, connectId={}, intervalSec={}, firstDelayMs={}, pointCount={}, connectionUrl={}",
                    protocol(), connectId, pollIntervalSec, firstDelay.toMillis(), spec.tagAddresses.size(), redactConnectionUrl(spec.connectionUrl));
        }
        persistConnectionEstablishedInCache(spec.deviceId);
        // 单台启用立即探测；批量启用靠 firstDelay 错开首次建连，避免同时打开大量 TCP
        if (delay.isZero()) {
            submitPollWithTimeout(connectId, spec);
        }
        return true;
    }

    public void pollOnce(String connectId) {
        ConnectSpec spec = specMap.get(connectId);
        if (spec == null) {
            spec = buildDeviceConnectSpec(connectId);
            if (spec == null) {
                return;
            }
        }
        executePoll(connectId, spec);
    }

    /**
     * 一次性从设备读取当前连接下全部配置点位的地址与值（不写失败态到 Redis）。
     *
     * @return 解析后的实时快照；连接/读失败时 online=false 且带 message
     */
    public DeviceRealtimeObj readAllPoints(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        ConnectSpec spec = specMap.get(deviceId);
        if (spec == null) {
            spec = buildDeviceConnectSpec(deviceId);
        }
        if (spec == null) {
            return null;
        }
        DeviceRealtimeObj cached = requireCachedDevice(deviceId);
        if (cached == null) {
            return null;
        }
        long startNanos = System.nanoTime();
        try {
            Map<String, Object> values = doRead(deviceId, spec, connectionManagerForOnDemandRead());
            DeviceRealtimeObj snapshot = deviceDataParser.parse(cached, spec.pointsByName, values, null);
            realtimeCacheService.save(snapshot, cached);
            recordPoll(spec, isPollSuccessful(spec, snapshot), startNanos);
            return snapshot;
        } catch (Exception e) {
            log.warn("{} readAllPoints failed deviceId={}: {}", protocol(), deviceId, e.getMessage());
            String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            DeviceRealtimeObj snapshot = deviceDataParser.parse(cached, spec.pointsByName, Map.of(), err);
            recordPoll(spec, false, startNanos);
            // 与轮询一致：读失败不写 Redis，避免误刷新在线状态
            return snapshot;
        }
    }

    /**
     * 协议轮询是否已启动（即采集连接是否处于开启状态）。
     */
    public boolean isConnectionOpen(String connectId) {
        return StringUtils.hasText(connectId) && taskMap.containsKey(connectId);
    }

    @Override
    public boolean stop(String connectId) {
        return stopPolling(connectId, false);
    }

    /**
     * 停止轮询。熔断触发的停轮询须 preserveCircuitRecovery=true，以保留退避后的半开恢复任务。
     * <p>先在短临界区摘掉调度任务与引用，再在锁外关连接 / 回收线程，避免 HTTP close
     * 在轮询阻塞读上死等（awaitTermination + 连接池锁）。
     */
    public boolean stopPolling(String connectId, boolean preserveCircuitRecovery) {
        DetachedPolling detached = detachPolling(connectId, preserveCircuitRecovery);
        if (detached == null) {
            return false;
        }
        finishStopPolling(detached);
        return detached.hadTask() || detached.spec() != null;
    }

    private synchronized DetachedPolling detachPolling(String connectId, boolean preserveCircuitRecovery) {
        if (!StringUtils.hasText(connectId)) {
            return null;
        }
        if (!preserveCircuitRecovery) {
            connectionFailureHandler.reset(connectId);
        }
        ConnectSpec spec = specMap.remove(connectId);
        ScheduledFuture<?> future = taskMap.remove(connectId);
        ExecutorService executor = readExecutorMap.remove(connectId);
        pollInProgress.remove(connectId);
        pollGateSkipStreak.remove(connectId);
        pollMetricsPending.remove(connectId);
        if (spec != null && spec.deviceId != null) {
            pollSkipMetrics.removeDevice(protocol(), spec.deviceId);
        }
        boolean hadTask = future != null;
        if (future != null) {
            future.cancel(true);
        }
        if (!hadTask && spec == null && executor == null) {
            return null;
        }
        return new DetachedPolling(connectId, spec, executor, hadTask);
    }

    private void finishStopPolling(DetachedPolling detached) {
        // 先 interrupt，再踢 socket 打断阻塞读；避免读失败后在 interrupt 生效前 withClient 重连。
        ExecutorService executor = detached.executor();
        if (executor != null) {
            executor.shutdownNow();
        }
        evictIfNoLongerUsed(detached.spec());
        shutdownExecutorService(executor);
        // 进行中的轮询可能在 evict 后重连，线程退出后再踢一次，避免 socket 留在连接池
        evictIfNoLongerUsed(detached.spec());
        if (detached.hadTask()) {
            log.info("{} polling stopped, connectId={}", protocol(), detached.connectId());
        }
    }

    /**
     * 停用时关闭底层连接；若同 URL 仍被其它设备轮询则保留，避免误杀共享 PLC 会话。
     */
    private void evictIfNoLongerUsed(ConnectSpec spec) {
        if (spec == null || !StringUtils.hasText(spec.connectionUrl)) {
            return;
        }
        for (ConnectSpec other : specMap.values()) {
            if (other != null && spec.connectionUrl.equals(other.connectionUrl)) {
                log.debug("{} skip connection evict: url still used by deviceId={}",
                        protocol(), other.deviceId);
                return;
            }
        }
        try {
            evictPollingConnection(spec);
        } catch (Exception e) {
            log.debug("{} evict on stop failed deviceId={}: {}",
                    protocol(), spec.deviceId, e.getMessage());
        }
    }

    @Override
    public void stopAll() {
        List<String> ids;
        synchronized (this) {
            ids = new ArrayList<>(taskMap.keySet());
        }
        ids.forEach(this::stop);
    }

    private record DetachedPolling(
            String connectId,
            ConnectSpec spec,
            ExecutorService executor,
            boolean hadTask
    ) {
    }

    @Override
    public ProtocolWriteResult write(String connectId, ProtocolWriteRequest request) {
        if (!StringUtils.hasText(connectId) || request == null
                || !StringUtils.hasText(request.address()) || request.value() == null) {
            return ProtocolWriteResult.fail("写入参数无效");
        }
        ConnectSpec spec = specMap.get(connectId);
        if (spec == null) {
            log.warn("{} write failed: no spec, connectId={}", protocol(), connectId);
            return ProtocolWriteResult.fail("设备未启动采集或连接未建立");
        }
        long startNanos = System.nanoTime();
        ProtocolWriteResult result = ProtocolWriteResult.fail("协议写入失败");
        try (PlcConnection connection = connectionManager.getConnection(spec.connectionUrl)) {
            result = performPlcWrite(connection, spec, request);
            if (result.success()) {
                onWriteSuccess(connectId);
            }
        } catch (Exception e) {
            log.error("{} write error connectId={}: {}", protocol(), connectId, e.getMessage());
            result = ProtocolWriteResult.fail(e.getMessage());
        } finally {
            recordWrite(spec, result.success(), startNanos);
        }
        return result;
    }

    /**
     * 在已建立的 PLC 连接上执行单次写入。
     */
    protected ProtocolWriteResult performPlcWrite(
            PlcConnection connection, ConnectSpec spec, ProtocolWriteRequest request
    ) throws Exception {
        if (!connection.getMetadata().isWriteSupported()) {
            return ProtocolWriteResult.fail("连接不支持写入");
        }
        PlcWriteRequest writeRequest = connection.writeRequestBuilder()
                .addTagAddress("ctrl", convertAddress(request.address()), request.value())
                .build();
        try {
            PlcWriteResponse response = PlcTagResponseSupport.awaitWriteResponse(
                    writeRequest, spec.requestTimeoutMs);
            return PlcTagResponseSupport.isTagOk(response, "ctrl")
                    ? ProtocolWriteResult.ok()
                    : ProtocolWriteResult.fail("PLC 写入失败");
        } catch (IllegalArgumentException e) {
            return ProtocolWriteResult.fail(e.getMessage());
        } catch (Exception e) {
            return ProtocolWriteResult.fail(PlcTagResponseSupport.toWriteFailureMessage(e));
        }
    }

    protected void onWriteSuccess(String connectId) {
        scheduleReadAfterWrite(connectId);
    }

    private void scheduleReadAfterWrite(String connectId) {
        ConnectSpec spec = specMap.get(connectId);
        ExecutorService executor = readExecutorMap.get(connectId);
        if (spec == null || executor == null) {
            return;
        }
        AtomicBoolean gate = pollInProgress.get(connectId);
        if (gate != null && gate.get()) {
            log.debug("{} write-follow read skipped: poll in progress, connectId={}", protocol(), connectId);
            return;
        }
        scheduler.schedule(() -> {
            if (!readExecutorMap.containsKey(connectId)) {
                return;
            }
            AtomicBoolean inProgress = pollInProgress.get(connectId);
            if (inProgress != null && inProgress.get()) {
                return;
            }
            submitPollWithTimeout(connectId, spec);
        }, Instant.now().plusMillis(WRITE_READ_DELAY_MS));
    }

    /**
     * 提交单轮轮询并在 watchdog 线程上 {@code future.get(timeout)}；超时则 cancel、回收读线程并记失败。
     */
    private void submitPollWithTimeout(String connectId, ConnectSpec spec) {
        if (!taskMap.containsKey(connectId) || spec == null) {
            return;
        }
        ExecutorService executor = readExecutorMap.computeIfAbsent(connectId, this::createReadExecutor);
        if (executor.isShutdown()) {
            executor = readExecutorMap.compute(connectId, (id, existing) -> {
                if (existing == null || existing.isShutdown()) {
                    return createReadExecutor(id);
                }
                return existing;
            });
        }
        if (executor == null || executor.isShutdown()) {
            return;
        }
        long startNanos = System.nanoTime();
        pollMetricsPending.put(connectId, Boolean.TRUE);
        final ExecutorService submitExecutor = executor;
        Future<?> pollFuture;
        try {
            pollFuture = submitExecutor.submit(() -> executePoll(connectId, spec));
        } catch (RejectedExecutionException e) {
            pollMetricsPending.remove(connectId);
            log.debug("{} poll submit rejected connectId={}: {}", protocol(), connectId, e.getMessage());
            return;
        }
        if (pollWatchdog == null || pollWatchdog.isShutdown()) {
            pollMetricsPending.remove(connectId);
            return;
        }
        pollWatchdog.execute(() -> awaitPollCompletion(connectId, spec, pollFuture, startNanos));
    }

    private void awaitPollCompletion(String connectId, ConnectSpec spec, Future<?> pollFuture, long startNanos) {
        long timeoutMs = pollRoundTimeoutMs(spec);
        try {
            pollFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            handlePollTimeout(connectId, spec, pollFuture, startNanos, timeoutMs);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            log.debug("{} poll execution error connectId={}: {}", protocol(), connectId,
                    cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pollFuture.cancel(true);
        } catch (CancellationException ignored) {
            // stop 或超时 cancel
        }
    }

    private void handlePollTimeout(
            String connectId,
            ConnectSpec spec,
            Future<?> pollFuture,
            long startNanos,
            long timeoutMs
    ) {
        if (!taskMap.containsKey(connectId)) {
            pollMetricsPending.remove(connectId);
            return;
        }
        log.error("{} poll round timed out after {}ms, connectId={}, recycling read executor",
                protocol(), timeoutMs, connectId);
        pollFuture.cancel(true);
        AtomicBoolean gate = pollInProgress.get(connectId);
        if (gate != null) {
            gate.set(false);
        }
        pollMetricsPending.put(connectId, Boolean.FALSE);
        recordPoll(spec, false, startNanos);
        evictPollingConnection(spec);
        recycleReadExecutor(connectId);
        String msg = "poll round timed out after " + timeoutMs + "ms";
        if (connectionFailureHandler.onPollFailure(connectId, protocol(), msg)) {
            scheduler.execute(() -> stopPolling(connectId, true));
        }
    }

    private long pollRoundTimeoutMs(ConnectSpec spec) {
        long timeoutMs = pollRoundTimeoutSec * 1000L;
        if (spec != null && spec.requestTimeoutMs > 0) {
            timeoutMs = Math.max(timeoutMs, spec.requestTimeoutMs * 2L);
        }
        return timeoutMs;
    }

    private ExecutorService createReadExecutor(String connectId) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadPrefix + connectId);
            t.setDaemon(true);
            return t;
        });
    }

    private void recycleReadExecutor(String connectId) {
        ExecutorService old = readExecutorMap.remove(connectId);
        if (old != null) {
            shutdownExecutorService(old);
        }
        if (taskMap.containsKey(connectId)) {
            readExecutorMap.put(connectId, createReadExecutor(connectId));
            log.warn("{} read executor recycled after poll timeout, connectId={}", protocol(), connectId);
        }
    }

    private void executePoll(String connectId, ConnectSpec spec) {
        if (!taskMap.containsKey(connectId)) {
            pollMetricsPending.remove(connectId);
            log.debug("{} poll ignored: polling stopped, connectId={}", protocol(), connectId);
            return;
        }
        AtomicBoolean gate = pollInProgress.computeIfAbsent(connectId, id -> new AtomicBoolean(false));
        if (!gate.compareAndSet(false, true)) {
            pollMetricsPending.remove(connectId);
            onPollGateSkipped(connectId, spec);
            return;
        }
        clearPollGateSkipStreak(connectId, spec);
        long startNanos = System.nanoTime();
        boolean success = false;
        PollRoundContext.begin(spec.deviceId, protocol());
        PollRoundTiming timing = PollRoundContext.timing();
        int totalPoints = spec.tagAddresses != null ? spec.tagAddresses.size() : 0;
        int valuesRead = 0;
        try {
            DeviceRealtimeObj cached = requireCachedDevice(spec.deviceId);
            if (cached == null) {
                log.warn("{} poll skipped: device cache miss, connectId={}", protocol(), connectId);
                return;
            }
            long readPhaseStart = System.nanoTime();
            Map<String, Object> values = doRead(connectId, spec);
            long readTotalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - readPhaseStart);
            timing.setReadMs(Math.max(0, readTotalMs - timing.getConnectMs()));
            valuesRead = values != null ? values.size() : 0;

            long parseStart = System.nanoTime();
            DeviceRealtimeObj snapshot = parsePollSnapshot(cached, spec, values, null);
            timing.setParseMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - parseStart));

            success = isPollSuccessful(spec, snapshot);
            if (success) {
                applyEstablishedConnectionStatus(snapshot);
                connectionFailureHandler.onPollSuccess(connectId);
            } else {
                String msg = snapshot.getMessage() != null ? snapshot.getMessage() : "全部点位读取失败";
                if (isPointAddressOnlyFailure(spec, msg)) {
                    applyConnectedWithPointErrors(snapshot, msg);
                    connectionFailureHandler.onPollSuccess(connectId);
                    success = true;
                    log.warn("{} poll point read failed but connection ok connectId={}: {}",
                            protocol(), connectId, msg);
                } else {
                    applyPollingFailedConnectionStatus(snapshot, msg);
                    log.warn("{} poll no data connectId={}: {}", protocol(), connectId, msg);
                    if (!isConfigurationPollFailure(msg)
                            && connectionFailureHandler.onPollFailure(connectId, protocol(), msg)) {
                        scheduler.execute(() -> stopPolling(connectId, true));
                    }
                }
            }

            PointValueChangeDetector.logValueLost(
                    collectDiagnostics.isPointValueChangeLogEnabled(),
                    spec.deviceId,
                    protocol(),
                    cached.getPoints(),
                    snapshot.getPoints());

            long cacheStart = System.nanoTime();
            publishRealtime(snapshot, cached);
            timing.setCacheMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cacheStart));
        } catch (RealtimeCacheSerializationException e) {
            log.error("{} poll realtime cache serialization failed connectId={}: {}",
                    protocol(), connectId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("{} poll interrupted (device stopped) connectId={}", protocol(), connectId);
        } catch (Exception e) {
            if (!taskMap.containsKey(connectId)) {
                log.debug("{} poll aborted after stop connectId={}: {}",
                        protocol(), connectId, e.getMessage());
            } else {
                String pollError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("{} poll failed connectId={}: {}", protocol(), connectId, pollError);
                if (isPointAddressOnlyFailure(spec, pollError)) {
                    publishPollFailureSnapshotAsConnected(spec, pollError);
                    connectionFailureHandler.onPollSuccess(connectId);
                    success = true;
                } else {
                    publishPollFailureSnapshot(spec, pollError);
                    if (!isConfigurationPollFailure(pollError)
                            && connectionFailureHandler.onPollFailure(connectId, protocol(), pollError)) {
                        scheduler.execute(() -> stopPolling(connectId, true));
                    }
                }
            }
        } finally {
            timing.setTotalMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            StructuredCollectLog.logPollRound(
                    collectDiagnostics,
                    spec.deviceId,
                    protocol(),
                    connectId,
                    timing,
                    success,
                    valuesRead,
                    totalPoints,
                    PollRoundContext.partialReadFailure(),
                    PollRoundContext.modbusStats(),
                    spec.pollIntervalSec);
            PlcResponseDiagnostics.clearLastReadResponse();
            PollRoundContext.clear();
            gate.set(false);
            if (Boolean.TRUE.equals(pollMetricsPending.remove(connectId))) {
                long durationMs = timing.getTotalMs();
                if (spec.deviceId != null) {
                    pollSkipMetrics.recordPollDurationMs(protocol(), spec.deviceId, durationMs);
                }
                recordPoll(spec, success, startNanos);
            }
            if (!taskMap.containsKey(connectId)) {
                evictIfNoLongerUsed(spec);
            }
        }
    }

    private void onPollGateSkipped(String connectId, ConnectSpec spec) {
        int streak = pollGateSkipStreak
                .computeIfAbsent(connectId, id -> new AtomicInteger(0))
                .incrementAndGet();
        String deviceId = spec != null ? spec.deviceId : null;
        if (deviceId != null) {
            pollSkipMetrics.recordGateSkipped(protocol(), deviceId, streak);
        }
        if (streak >= pollSkipWarnThreshold) {
            if (streak == pollSkipWarnThreshold || streak % 10 == 0) {
                int intervalSec = spec != null && spec.pollIntervalSec > 0 ? spec.pollIntervalSec : 0;
                log.warn(
                        "{} poll gate skipped {} times consecutively (interval {}s shorter than poll round), connectId={}, deviceId={}. "
                                + "Check collect.protocol.poll.last_duration_ms or increase collectIntervalSec.",
                        protocol(), streak, intervalSec, connectId, deviceId);
            }
        } else {
            log.debug("{} poll skipped: previous run still in progress, connectId={}, consecutiveSkips={}",
                    protocol(), connectId, streak);
        }
    }

    private void clearPollGateSkipStreak(String connectId, ConnectSpec spec) {
        AtomicInteger streak = pollGateSkipStreak.get(connectId);
        if (streak != null) {
            streak.set(0);
        }
        if (spec != null && spec.deviceId != null) {
            pollSkipMetrics.clearGateSkipStreak(protocol(), spec.deviceId);
        }
    }

    /**
     * 点位/地址配置类失败：连接可能正常，不应触发熔断停轮询。
     */
    protected boolean isConfigurationPollFailure(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        return reason.contains("全部点位读取失败")
                || reason.contains("全部 FINS 点位读取失败")
                || reason.contains("endCode=0x")
                || reason.contains("FINS 读失败")
                || reason.contains("FINS 位读失败")
                || reason.contains("不支持的 FINS 地址")
                || reason.contains("FINS 地址不能为空")
                || reason.contains("不支持的 S7 地址")
                || reason.contains("S7 地址不能为空")
                || reason.contains("全部 S7 点位读取失败")
                || reason.contains("未读到点位")
                || reason.contains("块读失败")
                || reason.contains("点读批次失败");
    }

    /**
     * 通讯已建立、仅点位/地址读失败：保持在线与轮询，不熔断。
     */
    protected boolean isPointAddressOnlyFailure(ConnectSpec spec, String reason) {
        if (PollTransportFailures.isTransportFailure(reason)
                || PollTransportFailures.hasTransportTagReadError(
                        spec != null ? spec.lastTagReadErrors : null)) {
            return false;
        }
        if (isConfigurationPollFailure(reason)) {
            return true;
        }
        if (spec != null && spec.lastTagReadErrors != null && !spec.lastTagReadErrors.isEmpty()) {
            return true;
        }
        return PollRoundContext.partialReadFailure()
                && spec != null
                && spec.tagAddresses != null
                && !spec.tagAddresses.isEmpty();
    }

    protected DeviceRealtimeObj getDeviceCache(String deviceId) {
        return requireCachedDevice(deviceId);
    }

    /**
     * 是否视为本轮采集成功：无点位时仅探活；有点位时要求解析后 online（至少一个点有值）。
     */
    private static boolean isPollSuccessful(ConnectSpec spec, DeviceRealtimeObj snapshot) {
        if (spec.tagAddresses == null || spec.tagAddresses.isEmpty()) {
            return true;
        }
        return snapshot != null && snapshot.isOnline();
    }

    private ConnectSpec buildDeviceConnectSpec(ProtocolConnect connect) {
        DeviceRealtimeObj cached = requireCachedDevice(connect.getId());
        if (cached == null) {
            log.warn("{} connect spec build failed: device cache miss, deviceId={}",
                    protocol(), connect.getId());
            return null;
        }
        return buildDeviceConnectSpec(connect.getId(), connect, cached);
    }

    private ConnectSpec buildDeviceConnectSpec(String deviceId) {
        DeviceRealtimeObj cached = requireCachedDevice(deviceId);
        if (cached == null) {
            log.warn("{} connect spec build failed: device cache miss, deviceId={}", protocol(), deviceId);
            return null;
        }
        ProtocolConnect connect = DeviceProtocolConnectFactory.from(cached);
        if (connect == null) {
            return null;
        }
        return buildDeviceConnectSpec(deviceId, connect, cached);
    }

    private ConnectSpec buildDeviceConnectSpec(String deviceId, ProtocolConnect connect, DeviceRealtimeObj cached) {
        Map<String, DevicePoint> pointsByName = DevicePointConfigSupport.toPointsByName(cached);
        Map<String, DevicePoint> writablePointsByName = DevicePointConfigSupport.toWritablePointsByName(cached);
        Map<String, String> tagAddresses = new LinkedHashMap<>();
        DeviceCollectConfig collectConfig = DevicePointConfigSupport.normalize(cached.getCollectConfig());
        for (Map.Entry<String, DevicePoint> entry : pointsByName.entrySet()) {
            tagAddresses.put(entry.getKey(), resolveTagAddress(entry.getValue()));
        }
        ConnectSpec spec = buildConnectSpec(connect, tagAddresses);
        if (spec != null) {
            spec.deviceId = deviceId;
            spec.pointsByName = pointsByName;
            spec.writablePointsByName = writablePointsByName;
            spec.modbusByteOrder = collectConfig.getModbusByteOrder();
            spec.reusePollingConnection = Boolean.TRUE.equals(collectConfig.getReusePollingConnection());
            spec = finalizeConnectSpec(spec, deviceId, connect, cached);
        }
        return spec;
    }

    /**
     * 子类在连接规格就绪后预计算读计划、索引等（如 Modbus 块分组）。
     */
    protected ConnectSpec finalizeConnectSpec(
            ConnectSpec spec,
            String deviceId,
            ProtocolConnect connect,
            DeviceRealtimeObj cached
    ) {
        return spec;
    }

    private DeviceRealtimeObj parsePollSnapshot(
            DeviceRealtimeObj cached,
            ConnectSpec spec,
            Map<String, Object> values,
            String error
    ) {
        Map<String, DevicePoint> pointsByName = spec.pointsByName;
        if (deviceDataParser instanceof PlcDeviceDataParser plcParser) {
            return plcParser.parseWithTagErrors(
                    cached,
                    pointsByName,
                    values,
                    error,
                    spec.lastTagReadErrors);
        }
        return deviceDataParser.parse(cached, pointsByName, values, error);
    }

    private void publishPollFailureSnapshot(ConnectSpec spec, String pollError) {
        publishPollFailureSnapshotInternal(spec, pollError, false);
    }

    private void publishPollFailureSnapshotAsConnected(ConnectSpec spec, String pollError) {
        publishPollFailureSnapshotInternal(spec, pollError, true);
    }

    private void publishPollFailureSnapshotInternal(ConnectSpec spec, String pollError, boolean connectionOk) {
        if (spec == null || !StringUtils.hasText(spec.deviceId)) {
            return;
        }
        try {
            DeviceRealtimeObj cached = requireCachedDevice(spec.deviceId);
            if (cached == null) {
                return;
            }
            Map<String, DevicePoint> pointsByName = spec.pointsByName;
            if (pointsByName == null || pointsByName.isEmpty()) {
                pointsByName = DevicePointConfigSupport.toPointsByName(cached);
            }
            DeviceRealtimeObj snapshot = parsePollSnapshot(cached, spec, Map.of(), connectionOk ? null : pollError);
            if (connectionOk) {
                applyConnectedWithPointErrors(snapshot, pollError);
            } else {
                applyPollingFailedConnectionStatus(snapshot, pollError);
            }
            publishRealtime(snapshot, cached);
        } catch (Exception ex) {
            log.debug("{} publish poll failure snapshot failed deviceId={}: {}",
                    protocol(), spec.deviceId, ex.getMessage());
        }
    }

    private void publishRealtime(DeviceRealtimeObj snapshot, DeviceRealtimeObj previous) {
        if (snapshot == null) {
            return;
        }
        String deviceId = snapshot.getDeviceId();
        // stop 使用 cancel(false)，进行中的轮询可能在 Redis 已清理后仍写回；停用后禁止复活缓存。
        if (!StringUtils.hasText(deviceId) || !taskMap.containsKey(deviceId)) {
            log.debug("{} skip publish: polling already stopped, deviceId={}", protocol(), deviceId);
            return;
        }
        realtimeCacheService.save(snapshot, previous);
    }

    private void persistConnectionEstablishedInCache(String deviceId) {
        DeviceRealtimeObj cached = requireCachedDevice(deviceId);
        if (cached == null) {
            return;
        }
        cached.setConnectionStatus(DeviceConnectionStatus.HALF_OPEN);
        cached.setConnectionStatusLabel(DeviceConnectionStatus.HALF_OPEN.getLabel());
        cached.setConnectionStatusMessage("连接探测中");
        realtimeCacheService.save(cached);
    }

    private static void applyEstablishedConnectionStatus(DeviceRealtimeObj snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.setConnectionStatus(DeviceConnectionStatus.CONNECTED);
        snapshot.setConnectionStatusLabel(DeviceConnectionStatus.CONNECTED.getLabel());
        snapshot.setConnectionStatusMessage(null);
    }

    private static void applyPollingFailedConnectionStatus(DeviceRealtimeObj snapshot, String message) {
        if (snapshot == null) {
            return;
        }
        snapshot.setConnectionStatus(DeviceConnectionStatus.DISCONNECTED);
        snapshot.setConnectionStatusLabel(DeviceConnectionStatus.DISCONNECTED.getLabel());
        snapshot.setConnectionStatusMessage(message);
    }

    private static void applyConnectedWithPointErrors(DeviceRealtimeObj snapshot, String message) {
        if (snapshot == null) {
            return;
        }
        applyEstablishedConnectionStatus(snapshot);
        snapshot.setOnline(true);
        snapshot.setOfflineReason(null);
        snapshot.setMessage(message);
        snapshot.setLastUpdateTime(LocalDateTime.now());
    }

    private DeviceRealtimeObj requireCachedDevice(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        return realtimeCacheService.getByDeviceId(deviceId);
    }

    protected Map<String, Object> readOnce(PlcConnection connection, ConnectSpec spec) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map.Entry<String, String>> entries = new ArrayList<>(spec.tagAddresses.entrySet());
        int chunkSize = getReadChunkSize();

        for (int from = 0; from < entries.size(); from += chunkSize) {
            int to = Math.min(from + chunkSize, entries.size());
            PlcReadRequest.Builder builder = connection.readRequestBuilder();
            List<Map.Entry<String, String>> chunk = entries.subList(from, to);
            for (Map.Entry<String, String> e : chunk) {
                builder.addTagAddress(e.getKey(), e.getValue());
            }
            long startNanos = System.nanoTime();
            boolean chunkSuccess = false;
            try {
                PlcReadResponse response = builder.build().execute().get(spec.requestTimeoutMs, TimeUnit.MILLISECONDS);
                chunkSuccess = chunk.stream()
                        .allMatch(e -> PlcTagResponseSupport.isTagOk(response, e.getKey()));
                for (Map.Entry<String, String> e : chunk) {
                    String tagName = e.getKey();
                    Object value = PlcTagResponseSupport.getObject(response, tagName);
                    if (value != null) {
                        result.put(tagName, value);
                    }
                }
            } finally {
                recordRead(spec, chunkSuccess, startNanos);
            }
        }
        return result;
    }

    protected void recordRead(ConnectSpec spec, boolean success, long startNanos) {
        if (collectMetrics == null || spec == null || spec.deviceId == null) {
            return;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        collectMetrics.recordRead(protocol(), spec.deviceId, success, durationMs);
    }

    private void recordPoll(ConnectSpec spec, boolean success, long startNanos) {
        if (collectMetrics == null || spec == null || spec.deviceId == null) {
            return;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        collectMetrics.recordPoll(protocol(), spec.deviceId, success, durationMs);
    }

    protected void recordWrite(ConnectSpec spec, boolean success, long startNanos) {
        if (collectMetrics == null || spec == null || spec.deviceId == null) {
            return;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        collectMetrics.recordWrite(protocol(), spec.deviceId, success, durationMs);
    }

    protected int getReadChunkSize() {
        return DEFAULT_READ_CHUNK_SIZE;
    }

    private Map<String, Object> doRead(String connectId, ConnectSpec spec) throws Exception {
        return doRead(connectId, spec, connectionManager);
    }

    /**
     * 连接级读重试上限；Modbus 等已在 {@code readOnce} 内做块/批重试时可覆写降低次数。
     */
    protected int maxConnectionReadRetries() {
        return MAX_CONNECTION_READ_RETRIES;
    }

    /**
     * 块/批读已在协议层重试后的逻辑失败，不再做连接级 sleep 重试。
     */
    protected boolean isChunkLevelReadFailure(Exception e) {
        if (!(e instanceof IllegalStateException)) {
            return false;
        }
        String msg = e.getMessage();
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        return msg.contains("块读失败") || msg.contains("点读批次失败") || msg.contains("未读到点位");
    }

    /**
     * 读失败时是否踢出缓存连接。块/点级逻辑失败（坏地址等）默认不踢，避免无谓重连。
     */
    protected boolean shouldEvictConnectionOnReadFailure(Exception e) {
        if (e instanceof PlcConnectionFaultException) {
            return true;
        }
        if (PlcTagResponseSupport.isTransportOrConnectionFailure(e)) {
            return true;
        }
        return !isChunkLevelReadFailure(e);
    }

    protected Map<String, Object> doRead(String connectId, ConnectSpec spec, PlcConnectionManager manager)
            throws Exception {
        int maxAttempts = maxConnectionReadRetries();
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            abortReadIfInterrupted();
            long connStartNanos = System.nanoTime();
            try (PlcConnection connection = manager.getConnection(spec.connectionUrl)) {
                PollRoundContext.timing().addConnectMs(
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connStartNanos));
                if (!connection.isConnected()) {
                    throw new IllegalStateException("PLC connection is not connected");
                }
                if (!connection.getMetadata().isReadSupported()) {
                    throw new IllegalStateException("PLC connection does not support read");
                }
                return readOnce(connection, spec);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("polling interrupted");
                }
                PollRoundContext.timing().addConnectMs(
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connStartNanos));
                lastException = e;
                if (shouldEvictConnectionOnReadFailure(e)) {
                    evictCachedConnection(manager, spec.connectionUrl);
                }
                boolean chunkFailure = isChunkLevelReadFailure(e);
                PlcResponseDiagnostics.logConnectionFailure(
                        spec.deviceId,
                        protocol(),
                        connectId,
                        attempt,
                        maxAttempts,
                        e,
                        PlcResponseDiagnostics.lastReadResponse(),
                        null);
                log.warn("{} connection read {}/{} failed connectId={} chunkRetried={}: {}",
                        protocol(), attempt, maxAttempts, connectId, chunkFailure, e.getMessage());
                if (chunkFailure || attempt >= maxAttempts) {
                    break;
                }
                abortReadIfInterrupted();
                awaitConnectionRetryDelay(connectId);
            }
        }
        throw lastException;
    }

    /**
     * 停用会 shutdownNow 读线程；重试建连前若已中断则放弃，避免踢掉 socket 后又立刻重连。
     */
    protected final void abortReadIfInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("polling interrupted");
        }
    }

    /**
     * 协议读重试间隔：用调度器计时，避免在 {@code readExecutor} 上 {@link Thread#sleep} 占满队列。
     */
    protected void awaitRetryDelay(long delayMs) throws InterruptedException {
        if (delayMs <= 0) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.schedule(latch::countDown, Instant.now().plusMillis(delayMs));
        boolean done = latch.await(delayMs + 500L, TimeUnit.MILLISECONDS);
        if (!done) {
            log.debug("{} retry delay timed out delayMs={}", protocol(), delayMs);
        }
    }

    private void awaitConnectionRetryDelay(String connectId) throws InterruptedException {
        awaitRetryDelay(CONNECTION_RETRY_DELAY_MS);
    }

    /**
     * 轮询停止、超时或读失败时踢出协议层缓存连接；FINS 等原生协议可覆写。
     */
    protected void evictPollingConnection(ConnectSpec spec) {
        if (spec == null || !StringUtils.hasText(spec.connectionUrl)) {
            return;
        }
        evictCachedConnection(connectionManager, spec.connectionUrl);
    }

    public static String redactConnectionUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        return url.replaceAll("(?i)(password=)[^&]*", "$1***");
    }

    private static void evictCachedConnection(PlcConnectionManager manager, String connectionUrl) {
        if (!StringUtils.hasText(connectionUrl) || !(manager instanceof CachedPlcConnectionManager cached)) {
            return;
        }
        try {
            cached.removeCachedConnection(connectionUrl);
        } catch (Exception e) {
            log.debug("Evict cached PLC connection failed url={}: {}", redactConnectionUrl(connectionUrl), e.getMessage());
        }
    }

    private void shutdownExecutor(String connectId) {
        ExecutorService ex = readExecutorMap.remove(connectId);
        shutdownExecutorService(ex);
        pollMetricsPending.remove(connectId);
    }

    private void shutdownExecutorService(ExecutorService ex) {
        if (ex == null) {
            return;
        }
        // 先 interrupt，避免对卡住的 PLC 读做长时间 awaitTermination（会拖死 close 接口）
        ex.shutdownNow();
        try {
            if (!ex.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                log.debug("{} read executor did not terminate within 500ms", protocol());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static class ConnectSpec {
        String connectionUrl;
        String deviceId;
        /** 固定速率调度间隔（秒） */
        int pollIntervalSec;
        int requestTimeoutMs;
        ModbusByteOrder modbusByteOrder;
        boolean reusePollingConnection;
        Map<String, String> tagAddresses = Map.of();
        Map<String, DevicePoint> pointsByName = Map.of();
        Map<String, DevicePoint> writablePointsByName = Map.of();
        /** 本轮轮询各点位读失败原因（通讯正常、地址/权限错误等） */
        Map<String, String> lastTagReadErrors = new LinkedHashMap<>();

        public ConnectSpec(String connectionUrl, Map<String, String> tagAddresses, int requestTimeoutMs) {
            this.connectionUrl = connectionUrl;
            this.tagAddresses = tagAddresses;
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }
}
