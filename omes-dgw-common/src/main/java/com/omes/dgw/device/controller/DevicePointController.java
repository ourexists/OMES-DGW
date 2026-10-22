package com.omes.dgw.device.controller;

import com.omes.dgw.common.PageResult;
import com.omes.dgw.common.Result;
import com.omes.dgw.device.dto.DevicePointCopyRequest;
import com.omes.dgw.device.dto.DevicePointDto;
import com.omes.dgw.device.dto.DevicePointWriteBatchRequest;
import com.omes.dgw.device.dto.DevicePointWriteCommandQuery;
import com.omes.dgw.device.dto.DevicePointWriteCommandVo;
import com.omes.dgw.device.dto.DevicePointWriteRequest;
import com.omes.dgw.device.dto.DevicePointWriteTaskHistoryVo;
import com.omes.dgw.device.dto.DevicePointWriteTaskVo;
import com.omes.dgw.device.dto.ProtocolOptionVo;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.device.service.DevicePointService;
import com.omes.dgw.device.service.DevicePointWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "设备点位（读写设备 collectConfig.points）")
@RestController
@RequestMapping("/device/points")
public class DevicePointController {

    private final DevicePointService devicePointService;
    private final DevicePointWriteService devicePointWriteService;

    public DevicePointController(
            DevicePointService devicePointService,
            DevicePointWriteService devicePointWriteService
    ) {
        this.devicePointService = devicePointService;
        this.devicePointWriteService = devicePointWriteService;
    }

    @Operation(summary = "列表查询（按设备，不分页）")
    @GetMapping("/list")
    public Result<List<DevicePointConfigItem>> list(@RequestParam String deviceId) {
        return Result.ok(devicePointService.listByDeviceId(deviceId));
    }

    @Operation(summary = "数据类型枚举列表（与设备协议对齐，需传 deviceId 或 protocol）")
    @GetMapping("/data-types")
    public Result<List<ProtocolOptionVo>> dataTypes(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) DeviceProtocol protocol) {
        return Result.ok(devicePointService.listDataTypes(deviceId, protocol));
    }

    @Operation(summary = "读写控制类型枚举列表")
    @GetMapping("/access-types")
    public Result<List<ProtocolOptionVo>> accessTypes() {
        return Result.ok(devicePointService.listAccessTypes());
    }

    @Operation(summary = "字节序枚举列表（Modbus/FINS/SLMP；需传 deviceId 或 protocol；其它协议返回空列表）")
    @GetMapping("/byte-orders")
    public Result<List<ProtocolOptionVo>> byteOrders(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) DeviceProtocol protocol) {
        return Result.ok(devicePointService.listByteOrders(deviceId, protocol));
    }

    @Operation(summary = "新增（写入设备 collectConfig.points）")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody DevicePointDto dto) {
        devicePointService.save(dto);
        return Result.ok();
    }

    @Operation(
            summary = "从其他设备复制点位",
            description = "将源设备点位全量覆盖到目标设备；须同协议，目标设备须停用。需要 ADMIN 或 OPERATOR 角色"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/copy")
    public Result<Integer> copy(@Valid @RequestBody DevicePointCopyRequest request) {
        return Result.ok(devicePointService.copy(request));
    }

    @Operation(summary = "单条控制写入", description = """
            支持两种写法：
            1) 按点位编码：传 code + value（从设备采集配置解析地址/数据类型/字节序/缩放系数）
            2) 按地址：传 address + dataType + value（可选 byteOrder/scaleFactor）
            """)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/write")
    public Result<DevicePointWriteTaskVo> write(@Valid @RequestBody DevicePointWriteRequest request) {
        return Result.ok(devicePointWriteService.enqueue(request));
    }

    @Operation(summary = "批量控制写入", description = """
            任务内按 seqNo/提交顺序串行执行，可跨设备。
            每条同样支持 code 或 address+dataType 两种写法。
            """)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/write/batch")
    public Result<DevicePointWriteTaskVo> writeBatch(
            @Valid @RequestBody DevicePointWriteBatchRequest request) {
        return Result.ok(devicePointWriteService.enqueueBatch(request));
    }

    @Operation(summary = "控制写入指令历史（分页）")
    @PostMapping("/write/history")
    public Result<PageResult<DevicePointWriteCommandVo>> writeHistory(
            @RequestBody(required = false) DevicePointWriteCommandQuery query) {
        return Result.ok(devicePointWriteService.pageHistory(query));
    }

    @Operation(summary = "控制写入任务历史（按 taskId 聚合分页）")
    @PostMapping("/write/history/tasks")
    public Result<PageResult<DevicePointWriteTaskHistoryVo>> writeTaskHistory(
            @RequestBody(required = false) DevicePointWriteCommandQuery query) {
        return Result.ok(devicePointWriteService.pageTaskHistory(query));
    }
}
