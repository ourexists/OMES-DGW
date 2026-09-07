package com.omes.dgw.deploy;

import com.omes.dgw.common.Result;
import com.omes.dgw.deploy.dto.PluginProbeResultVo;
import com.omes.dgw.deploy.dto.StorePluginConfigRequest;
import com.omes.dgw.deploy.dto.StorePluginStatusVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "插件管理")
@RestController
@RequestMapping("/system/store-plugin")
@PreAuthorize("hasRole('ADMIN')")
public class StorePluginController {

    private final StorePluginService storePluginService;

    public StorePluginController(StorePluginService storePluginService) {
        this.storePluginService = storePluginService;
    }

    @Operation(summary = "插件状态（Redis + Influx）")
    @GetMapping
    public Result<StorePluginStatusVo> status() {
        return Result.ok(storePluginService.status());
    }

    @Operation(summary = "导入插件 jar 到 plugins 目录")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<StorePluginStatusVo> upload(
            @RequestParam("type") String type,
            @RequestParam("file") MultipartFile file
    ) {
        return Result.ok(storePluginService.uploadPlugin(type, file));
    }

    @Operation(summary = "移除 plugins 目录中指定类型的插件 jar")
    @DeleteMapping
    public Result<StorePluginStatusVo> remove(@RequestParam("type") String type) {
        return Result.ok(storePluginService.removePlugin(type));
    }

    @Operation(summary = "保存插件配置到 config.properties")
    @PutMapping("/config")
    public Result<StorePluginStatusVo> saveConfig(@Valid @RequestBody StorePluginConfigRequest request) {
        return Result.ok(storePluginService.saveConfig(request));
    }

    @Operation(summary = "探测 Redis TCP 连通性（不校验账号密码）")
    @PostMapping("/probe/redis")
    public Result<PluginProbeResultVo> probeRedis(
            @RequestParam(value = "host", required = false) String host,
            @RequestParam(value = "port", required = false) Integer port
    ) {
        return Result.ok(storePluginService.probeRedis(host, port));
    }

    @Operation(summary = "探测 Influx HTTP 可达性（不校验 token）")
    @PostMapping("/probe/influx")
    public Result<PluginProbeResultVo> probeInflux(
            @RequestParam(value = "url", required = false) String url
    ) {
        return Result.ok(storePluginService.probeInflux(url));
    }

    /** @deprecated 兼容旧前端路径，等价于 /probe/redis */
    @Deprecated
    @Operation(summary = "探测 Redis（兼容旧路径）")
    @PostMapping("/probe")
    public Result<PluginProbeResultVo> probeLegacy(
            @RequestParam(value = "host", required = false) String host,
            @RequestParam(value = "port", required = false) Integer port
    ) {
        return Result.ok(storePluginService.probeRedis(host, port));
    }
}
