package com.omes.dgw.deploy.configui;

import com.omes.dgw.common.Result;
import com.omes.dgw.deploy.configui.dto.RuntimeConfigSaveRequest;
import com.omes.dgw.deploy.configui.dto.RuntimeConfigSectionVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/system/runtime-config")
@PreAuthorize("hasRole('ADMIN')")
public class RuntimeConfigController {

    private final RuntimeConfigService runtimeConfigService;

    public RuntimeConfigController(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @Operation(summary = "列出配置分区（可按 group 过滤：builtin / plugin-redis / plugin-influx）")
    @GetMapping
    public Result<List<RuntimeConfigSectionVo>> list(
            @RequestParam(value = "group", required = false) String group
    ) {
        return Result.ok(runtimeConfigService.listSections(group));
    }

    @Operation(summary = "获取单个配置分区")
    @GetMapping("/{id}")
    public Result<RuntimeConfigSectionVo> get(@PathVariable("id") String id) {
        return Result.ok(runtimeConfigService.getSection(id));
    }

    @Operation(summary = "保存单个配置分区到 config.properties")
    @PutMapping("/{id}")
    public Result<RuntimeConfigSectionVo> save(
            @PathVariable("id") String id,
            @Valid @RequestBody RuntimeConfigSaveRequest request
    ) {
        return Result.ok(runtimeConfigService.saveSection(id, request));
    }
}
