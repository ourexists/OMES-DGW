package com.omes.dgw.modscan.controller;

import com.omes.dgw.common.Result;
import com.omes.dgw.modscan.dto.ModscanRegisterAreaOptionVo;
import com.omes.dgw.modscan.dto.ModscanScanRequest;
import com.omes.dgw.modscan.dto.ModscanScanVo;
import com.omes.dgw.modscan.dto.ModscanSessionCloseRequest;
import com.omes.dgw.modscan.dto.ModscanSessionOpenRequest;
import com.omes.dgw.modscan.dto.ModscanSessionVo;
import com.omes.dgw.modscan.service.ModscanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "ModScan", description = "Modbus 远程地址扫描（DefaultPlcDriverManager 自建会话，与采集连接池隔离）")
@RestController
@RequestMapping("/modscan")
public class ModscanController {

    private final ModscanService modscanService;

    public ModscanController(ModscanService modscanService) {
        this.modscanService = modscanService;
    }

    @Operation(
            summary = "打开扫描会话",
            description = "使用 DefaultPlcDriverManager 建立并保持 TCP 连接，返回 sessionId；"
                    + "无扫描请求 1 小时后自动关闭"
    )
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/session/open")
    public Result<ModscanSessionVo> openSession(@Valid @RequestBody ModscanSessionOpenRequest request) {
        return Result.ok(modscanService.openSession(request));
    }

    @Operation(summary = "寄存器区扫描", description = "在已打开会话上读取，复用同一条连接")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/scan")
    public Result<ModscanScanVo> scan(@Valid @RequestBody ModscanScanRequest request) {
        return Result.ok(modscanService.scan(request));
    }

    @Operation(summary = "关闭扫描会话", description = "关闭 DefaultPlcDriverManager 连接并释放会话")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/session/close")
    public Result<Void> closeSession(@Valid @RequestBody ModscanSessionCloseRequest request) {
        modscanService.closeSession(request);
        return Result.ok();
    }

    @Operation(summary = "寄存器区枚举")
    @GetMapping("/areas")
    public Result<List<ModscanRegisterAreaOptionVo>> registerAreas() {
        return Result.ok(modscanService.listRegisterAreas());
    }
}
