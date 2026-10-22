package com.omes.dgw.device.controller.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.dto.DeviceBatchLifecycleItemVo;
import com.omes.dgw.device.dto.DeviceBatchLifecycleVo;
import com.omes.dgw.device.dto.DeviceConnectionVo;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.enums.DeviceConnectionStatus;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.device.connect.DeviceProtocolConnectFactory;
import com.omes.dgw.device.connect.DeviceProtocolGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备生命周期管理器：启用/停用与协议连接启停分离。
 */
@Slf4j
@Service
@Order(100)
public class DeviceLifecycleManager implements ApplicationRunner {

    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private ObjectProvider<DeviceProtocolGateway> protocolGatewayProvider;
    @Autowired
    private DeviceRealtimeCache realtimeCacheService;
    @Autowired
    private ObjectProvider<DeviceOfflineWatcher> offlineWatchService;
    @Autowired
    private DeviceConnectionFailureHandler connectionFailureHandler;
    @Autowired
    private AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        purgeDisabledRealtimeCache();
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().eq(Device::getEnabled, true));
        int synced = 0;
        int startedConnections = 0;
        boolean collectEnabled = isCollectEnabled();
        long staggerMs = collectEnabled ? resolveBatchStartStaggerMs() : 0L;
        for (Device device : devices) {
            ensureRealtimeCacheHeaders(device);
            if (!collectEnabled) {
                syncConnectionStatusToCache(device);
                synced++;
                continue;
            }
            if (DeviceProtocol.isSupported(device.getProtocol())) {
                pushConnectionEstablishedToCache(device);
                startProtocol(device, Duration.ofMillis(startedConnections * staggerMs));
                startedConnections++;
            } else {
                syncConnectionStatusToCache(device);
            }
            synced++;
        }
        if (collectEnabled) {
            log.info("系统启动：已同步启用设备缓存与连接, synced={}/{}, staggerMs={}",
                    synced, devices.size(), staggerMs);
        } else {
            log.info("系统启动：采集已关闭(app.collect.enabled=false)，仅同步台账/缓存头, synced={}",
                    synced);
        }
    }

    /** 是否允许启动协议轮询；memory 门户通常为 false。 */
    public boolean isCollectEnabled() {
        return appProperties == null
                || appProperties.getCollect() == null
                || appProperties.getCollect().isEnabled();
    }

    /**
     * 返回启用设备的实时快照；顺带清理停用/无效设备残留的本地实时缓存。
     */
    public List<DeviceRealtimeObj> listEnabledRealtime() {
        List<DeviceRealtimeObj> all = realtimeCacheService.listAll();
        if (all.isEmpty()) {
            return all;
        }
        Set<String> enabledIds = deviceMapper.selectList(
                        new LambdaQueryWrapper<Device>()
                                .eq(Device::getEnabled, true)
                                .select(Device::getId))
                .stream()
                .map(Device::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        List<DeviceRealtimeObj> result = new ArrayList<>(all.size());
        for (DeviceRealtimeObj vo : all) {
            if (vo == null || !StringUtils.hasText(vo.getDeviceId())) {
                continue;
            }
            if (enabledIds.contains(vo.getDeviceId())) {
                result.add(vo);
            } else {
                realtimeCacheService.remove(vo.getDeviceId(), vo.getDeviceCode());
                log.info("已清理停用/无效设备残留缓存 deviceId={}, code={}",
                        vo.getDeviceId(), vo.getDeviceCode());
            }
        }
        return result;
    }

    /** 启动时清理：Redis 中存在但 DB 已停用或不存在的设备快照。 */
    private void purgeDisabledRealtimeCache() {
        int removed = realtimeCacheService.listAll().size() - listEnabledRealtime().size();
        if (removed > 0) {
            log.info("系统启动：已清理停用设备残留实时缓存 removed={}", removed);
        }
    }

    /**
     * 启用设备：enabled=true、初始化实时缓存并启动协议连接（与改造前一致）。
     */
    public void enable(String deviceId) {
        enableInternal(requireDevice(deviceId), Duration.ZERO);
    }

    /**
     * 按设备 ID 循环调用启用；首次建连按 {@code batchStartStaggerMs} 错开，避免同时打开大量 TCP。
     */
    public DeviceBatchLifecycleVo enableBatch(List<String> deviceIds) {
        List<String> ids = distinctIds(deviceIds);
        long staggerMs = resolveBatchStartStaggerMs();
        DeviceBatchLifecycleVo vo = new DeviceBatchLifecycleVo();
        int startedConnections = 0;
        for (String id : ids) {
            DeviceBatchLifecycleItemVo item = new DeviceBatchLifecycleItemVo();
            item.setDeviceId(id);
            try {
                Device device = requireDevice(id);
                item.setDeviceCode(device.getCode());
                item.setDeviceName(device.getName());
                if (isEnabled(device)) {
                    item.setSuccess(true);
                    item.setSkipped(true);
                    item.setMessage("已是启用状态");
                    vo.setSkipped(vo.getSkipped() + 1);
                } else {
                    Duration delay = Duration.ofMillis(startedConnections * staggerMs);
                    boolean enabled = enableInternal(device, delay);
                    if (!enabled) {
                        item.setSuccess(true);
                        item.setSkipped(true);
                        item.setMessage("已是启用状态");
                        vo.setSkipped(vo.getSkipped() + 1);
                    } else {
                        item.setSuccess(true);
                        item.setMessage(delay.isZero()
                                ? "已启用"
                                : "已启用，将在 " + delay.toMillis() + "ms 后建连");
                        vo.setSuccess(vo.getSuccess() + 1);
                        if (DeviceProtocol.isSupported(device.getProtocol())) {
                            startedConnections++;
                        }
                    }
                }
            } catch (BusinessException e) {
                item.setSuccess(false);
                item.setMessage(e.getMessage());
                vo.setFailed(vo.getFailed() + 1);
            } catch (Exception e) {
                item.setSuccess(false);
                item.setMessage(e.getMessage());
                vo.setFailed(vo.getFailed() + 1);
                log.warn("批量启用失败 deviceId={}: {}", id, e.getMessage());
            }
            vo.getItems().add(item);
        }
        vo.setTotal(ids.size());
        vo.setMessage(summarizeBatch("启用", vo));
        log.info("批量启用完成 total={}, success={}, skipped={}, failed={}, staggerMs={}",
                vo.getTotal(), vo.getSuccess(), vo.getSkipped(), vo.getFailed(), staggerMs);
        return vo;
    }

    private boolean enableInternal(Device device, Duration protocolStartDelay) {
        String deviceId = device.getId();
        int u = deviceMapper.update(new LambdaUpdateWrapper<Device>()
                .set(Device::getEnabled, true)
                .eq(Device::getId, deviceId)
                .eq(Device::getEnabled, false)
        );
        if (u < 1) {
            return false;
        }
        device.setEnabled(true);
        ensureRealtimeCacheHeaders(device);
        if (DeviceProtocol.isSupported(device.getProtocol()) && isCollectEnabled()) {
            pushConnectionEstablishedToCache(device);
            startProtocol(device, protocolStartDelay);
        } else {
            syncConnectionStatusToCache(device);
        }
        log.info("设备已启用 deviceId={}, code={}, protocol={}, collectEnabled={}, startDelayMs={}",
                deviceId, device.getCode(), device.getProtocol(), isCollectEnabled(),
                protocolStartDelay == null ? 0 : protocolStartDelay.toMillis());
        return true;
    }

    /**
     * 停止设备：enabled=false，并关闭连接、清理实时缓存。
     * <p>即使库中已是停用（例如被其它路径误写 enabled=false），仍强制清理 Redis，
     * 避免总览残留。
     */
    public void stop(String deviceId) {
        stopInternal(requireDevice(deviceId));
    }

    private boolean stopInternal(Device device) {
        String deviceId = device.getId();
        int u = deviceMapper.update(new LambdaUpdateWrapper<Device>()
                .set(Device::getEnabled, false)
                .eq(Device::getId, deviceId)
                .eq(Device::getEnabled, true)
        );
        device.setEnabled(false);
        syncOnRemove(device);
        if (u < 1) {
            log.info("设备已是停用状态，仍已清理轮询与缓存 deviceId={}, code={}",
                    deviceId, device.getCode());
            return false;
        }
        log.info("设备已停止 deviceId={}, code={}", deviceId, device.getCode());
        return true;
    }

    /**
     * 按设备 ID 循环调用停用。
     */
    public DeviceBatchLifecycleVo stopBatch(List<String> deviceIds) {
        List<String> ids = distinctIds(deviceIds);
        DeviceBatchLifecycleVo vo = new DeviceBatchLifecycleVo();
        for (String id : ids) {
            DeviceBatchLifecycleItemVo item = new DeviceBatchLifecycleItemVo();
            item.setDeviceId(id);
            try {
                Device device = requireDevice(id);
                item.setDeviceCode(device.getCode());
                item.setDeviceName(device.getName());
                boolean stopped = stopInternal(device);
                if (stopped) {
                    item.setSuccess(true);
                    item.setMessage("已停用");
                    vo.setSuccess(vo.getSuccess() + 1);
                } else {
                    item.setSuccess(true);
                    item.setSkipped(true);
                    item.setMessage("已是停用状态");
                    vo.setSkipped(vo.getSkipped() + 1);
                }
            } catch (BusinessException e) {
                item.setSuccess(false);
                item.setMessage(e.getMessage());
                vo.setFailed(vo.getFailed() + 1);
            } catch (Exception e) {
                item.setSuccess(false);
                item.setMessage(e.getMessage());
                vo.setFailed(vo.getFailed() + 1);
                log.warn("批量停用失败 deviceId={}: {}", id, e.getMessage());
            }
            vo.getItems().add(item);
        }
        vo.setTotal(ids.size());
        vo.setMessage(summarizeBatch("停用", vo));
        log.info("批量停用完成 total={}, success={}, skipped={}, failed={}",
                vo.getTotal(), vo.getSuccess(), vo.getSkipped(), vo.getFailed());
        return vo;
    }

    /**
     * 点位或采集配置变更后：刷新本地实时缓存并重载轮询（使新点位进入 specMap）。
     * <p>停用设备只改 DB 配置，不写 Redis，并清掉可能残留的实时缓存。
     */
    public void onCollectConfigChanged(String deviceId) {
        Device device = requireDevice(deviceId);
        if (!isEnabled(device)) {
            realtimeCacheService.remove(device.getId(), device.getCode());
            log.info("采集配置已变更（设备未启用，已跳过写缓存） deviceId={}", deviceId);
            return;
        }
        if (!DeviceProtocol.isSupported(device.getProtocol())) {
            DeviceRealtimeObj vo = loadOrCreateRealtimeCache(device);
            device.mergeIntoRealtimeVo(vo);
            realtimeCacheService.save(vo);
            syncConnectionStatusToCache(device);
            return;
        }
        DeviceRealtimeObj vo = loadOrCreateRealtimeCache(device);
        device.mergeIntoRealtimeVo(vo);
        realtimeCacheService.save(vo);
        String protocolCode = device.getProtocol().getCode();
        if ((protocolGatewayOrNull() != null && protocolGatewayOrNull().isConnectionOpen(device))) {
            requireProtocolGateway().stop(protocolCode, deviceId);
        }
        connectionFailureHandler.reset(deviceId);
        pushConnectionEstablishedToCache(device);
        startProtocol(device);
        log.info("采集配置已变更，已重载轮询 deviceId={}", deviceId);
    }

    /**
     * 开启连接：启动协议轮询（设备须已启用）。
     */
    public DeviceConnectionVo openConnection(String deviceId) {
        Device device = requireDevice(deviceId);
        if (!isEnabled(device)) {
            throw new BusinessException("设备未启用，请先启用设备");
        }
        long startNanos = System.nanoTime();
        boolean alreadyOpen = (protocolGatewayOrNull() != null && protocolGatewayOrNull().isConnectionOpen(device));
        DeviceConnectionStatus statusBeforeReset = alreadyOpen ? resolveConnectionStatus(device) : null;
        connectionFailureHandler.reset(deviceId);
        ensureRealtimeCacheHeaders(device);
        if (!DeviceProtocol.isSupported(device.getProtocol())) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            String label = device.getProtocol() != null ? device.getProtocol().getLabel() : "未知";
            String message = "协议不支持[" + label + "]";
            syncConnectionStatusToCache(device, message);
            log.info("设备连接未启动：{} deviceId={}", message, deviceId);
            return DeviceConnectionVo.ok(deviceId, message, latencyMs);
        }
        if (alreadyOpen) {
            if (statusBeforeReset == DeviceConnectionStatus.CONNECTED) {
                syncConnectionStatusToCache(device, null);
                long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
                log.info("设备连接已在运行 deviceId={}", deviceId);
                return DeviceConnectionVo.ok(deviceId, "连接已在运行中", latencyMs);
            }
            log.info("设备连接异常({})，重启轮询 deviceId={}",
                    statusBeforeReset != null ? statusBeforeReset.getLabel() : "未知", deviceId);
            requireProtocolGateway().stop(device.getProtocol().getCode(), deviceId);
        }
        pushConnectionEstablishedToCache(device);
        boolean started = startProtocol(device);
        if (!started) {
            syncConnectionStatusToCache(device);
        } else {
            syncConnectionStatusToCache(device, "连接已开启");
        }
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        String message = started ? "连接已开启" : "连接启动失败";
        log.info("设备连接开启 deviceId={}, started={}", deviceId, started);
        return DeviceConnectionVo.ok(deviceId, message, latencyMs);
    }

    /**
     * 关闭连接：停止协议轮询，不修改 enabled、不清理本地实时缓存。
     */
    public DeviceConnectionVo closeConnection(String deviceId) {
        Device device = requireDevice(deviceId);
        long startNanos = System.nanoTime();
        stopProtocol(device);
        syncConnectionStatusToCache(device);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("设备连接已关闭 deviceId={}, code={}", deviceId, device.getCode());
        return DeviceConnectionVo.ok(deviceId, "连接已关闭", latencyMs);
    }

    /**
     * 熔断退避结束后半开恢复：重新启动轮询探测。
     */
    public void attemptConnectionRecovery(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            connectionFailureHandler.reset(deviceId);
            return;
        }
        if (!isEnabled(device)) {
            connectionFailureHandler.reset(deviceId);
            log.debug("半开恢复跳过：设备未启用 deviceId={}", deviceId);
            return;
        }
        if (!DeviceProtocol.isSupported(device.getProtocol())) {
            return;
        }
        String protocolCode = device.getProtocol().getCode();
        requireProtocolGateway().stopPollingPreserveCircuit(protocolCode, deviceId);
        connectionFailureHandler.enterHalfOpen(deviceId);
        boolean started = startProtocol(device);
        if (!started) {
            log.warn("半开恢复启动轮询失败 deviceId={}, code={}", deviceId, device.getCode());
            connectionFailureHandler.onPollFailure(deviceId, protocolCode, "半开恢复启动轮询失败");
            return;
        }
        log.info("半开恢复已启动轮询 deviceId={}, code={}", deviceId, device.getCode());
    }

    /**
     * 删除设备前：停止轮询并清理实时缓存。
     */
    public void onDeviceRemoved(Device device) {
        syncOnRemove(device);
    }

    private Device requireDevice(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private void prepareRealtimeCache(Device device) {
        DeviceRealtimeObj vo = loadOrCreateRealtimeCache(device);
        applyConnectionStatus(vo, device, null);
        realtimeCacheService.save(vo);
    }

    /** 仅同步台账头信息，供开启连接前保证缓存存在且不重置运行时字段。 */
    private void ensureRealtimeCacheHeaders(Device device) {
        if (device == null || !StringUtils.hasText(device.getId())) {
            return;
        }
        DeviceRealtimeObj vo = loadOrCreateRealtimeCache(device);
        realtimeCacheService.save(vo);
    }

    private DeviceRealtimeObj loadOrCreateRealtimeCache(Device device) {
        DeviceRealtimeObj existing = realtimeCacheService.getByDeviceId(device.getId());
        if (existing != null) {
            device.mergeIntoRealtimeVo(existing);
            return existing;
        }
        return device.toRealtimeVo();
    }

    private void syncConnectionStatusToCache(Device device) {
        syncConnectionStatusToCache(device, null);
    }

    private void syncConnectionStatusToCache(Device device, String connectionMessage) {
        if (device == null || !StringUtils.hasText(device.getId())) {
            return;
        }
        DeviceRealtimeObj cached = realtimeCacheService.getByDeviceId(device.getId());
        if (cached == null) {
            prepareRealtimeCache(device);
            if (connectionMessage != null) {
                cached = realtimeCacheService.getByDeviceId(device.getId());
                if (cached != null) {
                    cached.setConnectionStatusMessage(connectionMessage);
                    realtimeCacheService.save(cached);
                }
            }
            return;
        }
        applyConnectionStatus(cached, device, connectionMessage);
        realtimeCacheService.save(cached);
    }

    private void applyConnectionStatus(DeviceRealtimeObj vo, Device device, String connectionMessage) {
        DeviceConnectionStatus status = resolveConnectionStatus(device);
        vo.setConnectionStatus(status);
        vo.setConnectionStatusLabel(status.getLabel());
        if (connectionMessage != null) {
            vo.setConnectionStatusMessage(connectionMessage);
        }
    }

    /** 轮询启动前写入「探测中」，避免首轮异步采集完成前误显示「连接建立」。 */
    private void pushConnectionEstablishedToCache(Device device) {
        if (device == null || !StringUtils.hasText(device.getId())) {
            return;
        }
        DeviceRealtimeObj vo = loadOrCreateRealtimeCache(device);
        vo.setConnectionStatus(DeviceConnectionStatus.HALF_OPEN);
        vo.setConnectionStatusLabel(DeviceConnectionStatus.HALF_OPEN.getLabel());
        vo.setConnectionStatusMessage("连接探测中");
        realtimeCacheService.save(vo);
    }

    private boolean startProtocol(Device device) {
        return startProtocol(device, Duration.ZERO);
    }

    private boolean startProtocol(Device device, Duration initialDelay) {
        if (device == null || !StringUtils.hasText(device.getId()) || device.getProtocol() == null) {
            return false;
        }
        DeviceProtocolGateway gateway = protocolGatewayOrNull();
        if (gateway == null) {
            syncConnectionStatusToCache(device);
            return false;
        }
        boolean started = gateway.start(DeviceProtocolConnectFactory.from(device), initialDelay);
        syncConnectionStatusToCache(device);
        return started;
    }

    private void syncOnRemove(Device device) {
        if (device == null) {
            return;
        }
        stopProtocol(device);
        realtimeCacheService.remove(device.getId(), device.getCode());
        log.info("设备已清理轮询与缓存 deviceId={}, code={}", device.getId(), device.getCode());
    }

    private void stopProtocol(Device device) {
        if (device == null || !StringUtils.hasText(device.getId()) || device.getProtocol() == null) {
            return;
        }
        connectionFailureHandler.reset(device.getId());
        offlineWatchService.ifAvailable(watch -> watch.cancelWatch(device.getId()));
        DeviceProtocolGateway gateway = protocolGatewayOrNull();
        if (gateway == null) {
            return;
        }
        boolean stopped = gateway.stop(device.getProtocol().getCode(), device.getId());
        log.info("停止协议轮询 deviceId={}, stopped={}", device.getId(), stopped);
    }

    public static boolean isEnabled(Device device) {
        return device != null && Boolean.TRUE.equals(device.getEnabled());
    }

    private long resolveBatchStartStaggerMs() {
        AppProperties.Connection connection = appProperties != null && appProperties.getCollect() != null
                ? appProperties.getCollect().getConnection()
                : null;
        if (connection == null) {
            return 300L;
        }
        return Math.max(0L, connection.getBatchStartStaggerMs());
    }

    private static List<String> distinctIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new BusinessException("设备ID列表不能为空");
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String id : deviceIds) {
            if (StringUtils.hasText(id)) {
                ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException("设备ID列表不能为空");
        }
        return new ArrayList<>(ids);
    }

    private static String summarizeBatch(String action, DeviceBatchLifecycleVo vo) {
        return action + "完成：成功 " + vo.getSuccess()
                + " 台，跳过 " + vo.getSkipped()
                + " 台，失败 " + vo.getFailed() + " 台";
    }

    /**
     * 解析设备协议采集连接状态，供前端展示与按钮控制。
     */
    public DeviceConnectionStatus resolveConnectionStatus(Device device) {
        if (!isEnabled(device)) {
            return DeviceConnectionStatus.DISABLED;
        }
        if (!DeviceProtocol.isSupported(device.getProtocol())) {
            return DeviceConnectionStatus.UNSUPPORTED;
        }
        String deviceId = device.getId();
        if (connectionFailureHandler.isAbandoned(deviceId)) {
            return DeviceConnectionStatus.CIRCUIT_ABANDONED;
        }
        if (connectionFailureHandler.isHalfOpen(deviceId)) {
            return DeviceConnectionStatus.HALF_OPEN;
        }
        if (connectionFailureHandler.isOpenWaiting(deviceId)) {
            return DeviceConnectionStatus.DISCONNECTED;
        }
        if ((protocolGatewayOrNull() != null && protocolGatewayOrNull().isConnectionOpen(device))) {
            if (connectionFailureHandler.hasAccumulatedFailures(deviceId)) {
                return DeviceConnectionStatus.DISCONNECTED;
            }
            if (connectionFailureHandler.isPollUnconfirmed(deviceId)) {
                return DeviceConnectionStatus.HALF_OPEN;
            }
            return DeviceConnectionStatus.CONNECTED;
        }
        return DeviceConnectionStatus.DISCONNECTED;
    }

    private DeviceProtocolGateway requireProtocolGateway() {
        DeviceProtocolGateway gateway = protocolGatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new BusinessException("当前门户未启用协议采集（缺少 omes-dgw-collect）");
        }
        return gateway;
    }

    private DeviceProtocolGateway protocolGatewayOrNull() {
        return protocolGatewayProvider.getIfAvailable();
    }
}
