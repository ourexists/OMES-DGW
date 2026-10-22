package com.omes.dgw.device.controller;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.common.IdRequest;
import com.omes.dgw.common.PageResult;
import com.omes.dgw.common.Result;
import com.omes.dgw.device.dto.DeviceConnectionTestVo;
import com.omes.dgw.device.dto.DeviceCopyRequest;
import com.omes.dgw.device.dto.DevicePointScanVo;
import com.omes.dgw.device.dto.DeviceDto;
import com.omes.dgw.device.dto.ProtocolOptionVo;
import com.omes.dgw.device.dto.DeviceQuery;
import com.omes.dgw.device.dto.DeviceVo;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.dto.DeviceImportResultVo;
import com.omes.dgw.device.service.DeviceImportExportService;
import com.omes.dgw.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Tag(name = "设备管理", description = "设备 CRUD、分页查询及协议枚举")
@RestController
@RequestMapping("/device/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceImportExportService deviceImportExportService;

    public DeviceController(DeviceService deviceService, DeviceImportExportService deviceImportExportService) {
        this.deviceService = deviceService;
        this.deviceImportExportService = deviceImportExportService;
    }

    @Operation(
            summary = "分页查询",
            description = "按名称、编码、协议、分组、启用状态筛选；includeRealtime=true 时每条记录附带本地实时状态（realtime 字段）"
    )
    @PostMapping("/page")
    public Result<PageResult<DeviceVo>> page(@RequestBody DeviceQuery query) {
        return Result.ok(deviceService.pageQuery(query));
    }

    @Operation(summary = "协议枚举列表", description = "返回所有 DeviceProtocol 的 code 与 label，供下拉选用")
    @GetMapping("/protocols")
    public Result<List<ProtocolOptionVo>> protocols() {
        List<ProtocolOptionVo> options = Arrays.stream(DeviceProtocol.values())
                .map(p -> new ProtocolOptionVo(p.getCode(), p.getLabel(), p.isSupported()))
                .toList();
        return Result.ok(options);
    }

    @Operation(summary = "详情", description = "按主键查询设备基础信息，不含实时状态")
    @GetMapping("/detail")
    public Result<DeviceVo> detail(
            @Parameter(description = "设备主键 ID", required = true) @RequestParam String id
    ) {
        return Result.ok(deviceService.getDetail(id));
    }

    @Operation(summary = "新增", description = "需要 ADMIN 或 OPERATOR 角色")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/create")
    public Result<String> create(@Valid @RequestBody DeviceDto dto) {
        return Result.ok(deviceService.create(dto));
    }

    @Operation(
            summary = "复制设备",
            description = "基于源设备创建新设备：协议与点位配置一并复制，连接参数取请求体；新设备强制停用。需要 ADMIN 或 OPERATOR 角色"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/copy")
    public Result<String> copy(@Valid @RequestBody DeviceCopyRequest request) {
        return Result.ok(deviceService.copy(request));
    }

    @Operation(summary = "修改", description = "请求体 id 必填；需要 ADMIN 或 OPERATOR 角色")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody DeviceDto dto) {
        if (!StringUtils.hasText(dto.getId())) {
            throw new BusinessException("ID不能为空");
        }
        deviceService.update(dto.getId(), dto);
        return Result.ok();
    }

    @Operation(summary = "删除", description = "物理删除；仅 enabled=false 的设备可删；需 ADMIN 角色")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody IdRequest request) {
        deviceService.remove(request.getId());
        return Result.ok();
    }

    @Operation(
            summary = "测试连接（按配置）",
            description = "根据请求体中的 IP、端口、协议、超时等参数探测连接，不写实时缓存、不触发采集；适用于新增/编辑表单"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/test-connection")
    public Result<DeviceConnectionTestVo> testConnection(@Valid @RequestBody DeviceDto dto) {
        return Result.ok(deviceService.testConnection(dto));
    }

    @Operation(
            summary = "测试连接（按设备 ID）",
            description = "使用库中已保存的设备连接参数探测，不写实时缓存、不触发采集"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @GetMapping("/test-connection")
    public Result<DeviceConnectionTestVo> testConnectionById(
            @Parameter(description = "设备主键 ID", required = true) @RequestParam String id
    ) {
        return Result.ok(deviceService.testConnection(id));
    }

    @Operation(
            summary = "导出设备（含点位）",
            description = "按查询条件导出 Excel，含「设备信息」「点位」两个工作表；"
                    + "只读填实际值点位，只写填设定值点位，读写两者都填；枚举字段为中文 label"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/export")
    public void exportExcel(@RequestBody(required = false) DeviceQuery query, HttpServletResponse response)
            throws IOException {
        byte[] bytes = deviceImportExportService.exportExcel(query != null ? query : new DeviceQuery());
        writeExcelResponse(response, "devices-export.xlsx", bytes);
    }

    @Operation(
            summary = "下载导入模板/示例",
            description = "下载带示例数据的 Excel 模板（设备信息 / 点位）；"
                    + "枚举取值均为 label（如 Modbus TCP、只读、只写、读写、Real）；"
                    + "只读填实际值点位，只写填设定值点位，读写两者都填"
    )
    @GetMapping("/import/template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        byte[] bytes = deviceImportExportService.downloadTemplate();
        writeExcelResponse(response, "device-import-template.xlsx", bytes);
    }

    @Operation(
            summary = "导入设备（含点位）",
            description = "按设备编码 upsert；同编码设备的点位列表以 Excel 为准全量覆盖；"
                    + "「点位」表按读写类型填写实际值点位/设定值点位；读写类型填 只读/读写/只写"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DeviceImportResultVo> importExcel(
            @Parameter(description = "Excel 文件（.xlsx/.xls）", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        return Result.ok(deviceImportExportService.importExcel(file));
    }

    @Operation(
            summary = "扫描点位（一次性读取全部地址与值）",
            description = "设备须已启用；按 collectConfig 中配置的点位地址批量读取原始值与工程值；"
                    + "Modbus 使用独立 TCP 连接池，与采集轮询完全隔离；成功时同步刷新本地实时缓存"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/scan-points")
    public Result<DevicePointScanVo> scanPoints(
            @Parameter(description = "设备主键 ID", required = true) @RequestParam String id
    ) {
        return Result.ok(deviceService.scanPoints(id));
    }

    private static void writeExcelResponse(HttpServletResponse response, String filename, byte[] bytes)
            throws IOException {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

}
