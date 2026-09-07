package com.omes.dgw.device.controller;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.common.Result;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.controller.core.DeviceLifecycleManager;
import com.omes.dgw.device.dto.DeviceBatchLifecycleVo;
import com.omes.dgw.device.dto.DeviceConnectionVo;
import com.omes.dgw.device.dto.DeviceIdsRequest;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.protocol.ProtocolExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "设备实时状态", description = "本地实时缓存查询、手动刷新及协议轮询启停")
@RestController
@RequestMapping("/device/realtime")
public class DeviceRealtimeController {

    private final DeviceRealtimeCache cacheService;
    private final DeviceMapper deviceMapper;
    private final ProtocolExecutor protocolExecutor;
    private final DeviceLifecycleManager protocolLifecycleService;

    public DeviceRealtimeController(
            DeviceRealtimeCache cacheService,
            DeviceMapper deviceMapper,
            ProtocolExecutor protocolExecutor,
            DeviceLifecycleManager protocolLifecycleService
    ) {
        this.cacheService = cacheService;
        this.deviceMapper = deviceMapper;
        this.protocolExecutor = protocolExecutor;
        this.protocolLifecycleService = protocolLifecycleService;
    }

    @Operation(summary = "全部设备实时状态", description = "从本地实时缓存读取所有已缓存设备的实时快照（含连接状态），不做启用过滤，便于观测缓存")
    @GetMapping("/list")
    public Result<List<DeviceRealtimeObj>> list() {
        return Result.ok(cacheService.listAll());
    }

    @Operation(
            summary = "单设备实时状态",
            description = "deviceId 与 deviceCode 二选一；优先使用 deviceId"
    )
    @GetMapping("/detail")
    public Result<DeviceRealtimeObj> detail(
            @Parameter(description = "设备主键 ID") @RequestParam(required = false) String deviceId,
            @Parameter(description = "设备编码") @RequestParam(required = false) String deviceCode
    ) {
        DeviceRealtimeObj vo = deviceId != null
                ? cacheService.getByDeviceId(deviceId)
                : cacheService.getByDeviceCode(deviceCode);
        return Result.ok(vo);
    }

    @Operation(
            summary = "立即采集并刷新缓存",
            description = "对指定设备执行一次协议轮询，并返回更新后的本地实时快照；设备须已启用且已开启连接"
    )
    @PostMapping("/refresh")
    public Result<DeviceRealtimeObj> refresh(
            @Parameter(description = "设备主键 ID", required = true) @RequestParam String deviceId
    ) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        if (!DeviceLifecycleManager.isEnabled(device)) {
            throw new BusinessException("设备未启用");
        }
        if (device.getProtocol() != null) {
            protocolExecutor.pollOnce(device.getProtocol().getCode(), deviceId);
        }
        return Result.ok(cacheService.getByDeviceId(deviceId));
    }

    @Operation(summary = "启用设备", description = "设置 enabled=true、初始化实时缓存并启动协议连接（等同启用+建连）")
    @GetMapping("/enable")
    public Result<Void> enable(
            @Parameter(description = "设备主键 ID", required = true) @RequestParam String deviceId
    ) {
        protocolLifecycleService.enable(deviceId);
        return Result.ok();
    }

    @Operation(summary = "停止设备", description = "设置 enabled=false，并关闭连接、清理实时缓存")
    @GetMapping("/stop")
    public Result<Void> stop(
            @Parameter(description = "设备主键 ID", required = true) @RequestParam String deviceId
    ) {
        protocolLifecycleService.stop(deviceId);
        return Result.ok();
    }

    @Operation(
            summary = "批量启用设备",
            description = "按设备 ID 循环调用启用；已启用的跳过。首次建连按配置错开，避免同时打开大量 TCP 连接"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/enable/batch")
    public Result<DeviceBatchLifecycleVo> enableBatch(@Valid @RequestBody DeviceIdsRequest request) {
        return Result.ok(protocolLifecycleService.enableBatch(request.getDeviceIds()));
    }

    @Operation(summary = "批量停止设备", description = "按设备 ID 循环调用停用；已停用的跳过并仍清理残留缓存")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/stop/batch")
    public Result<DeviceBatchLifecycleVo> stopBatch(@Valid @RequestBody DeviceIdsRequest request) {
        return Result.ok(protocolLifecycleService.stopBatch(request.getDeviceIds()));
    }

    @Operation(summary = "开启连接", description = "单独启动协议轮询连接；设备须已启用（启用接口已含建连，断连后可调此接口重连）")
    @GetMapping("/connection/open")
    public Result<DeviceConnectionVo> openConnection(
            @Parameter(description = "设备主键 ID", required = true) @RequestParam String deviceId
    ) {
        return Result.ok(protocolLifecycleService.openConnection(deviceId));
    }

    @Operation(summary = "关闭连接", description = "停止协议轮询连接；不改变 enabled 状态")
    @GetMapping("/connection/close")
    public Result<DeviceConnectionVo> closeConnection(
            @Parameter(description = "设备主键 ID", required = true) @RequestParam String deviceId
    ) {
        return Result.ok(protocolLifecycleService.closeConnection(deviceId));
    }
}
