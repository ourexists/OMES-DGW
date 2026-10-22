package com.omes.dgw.auth.controller;

import com.omes.dgw.common.Result;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.dto.UserInfoVo;
import com.omes.dgw.portal.PortalMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "系统管理")
@RestController
@RequestMapping("/system")
public class SystemController {

    private final AppProperties appProperties;

    public SystemController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Operation(summary = "配置用户列表（只读）")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public Result<List<UserInfoVo>> listUsers() {
        List<UserInfoVo> users = appProperties.getSecurity().getUsers().stream()
                .map(u -> UserInfoVo.builder()
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .roles(u.getRoles())
                        .build())
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    @Operation(summary = "门户运行时信息（含历史/指标查询模式，供前端双模式展示）")
    @GetMapping("/portal")
    public Result<Map<String, Object>> portal() {
        PortalMode mode = PortalMode.from(
                appProperties.getPortal() != null ? appProperties.getPortal().getMode() : null);
        var collect = appProperties.getCollect();
        String monitorType = collect != null ? collect.getMonitorType() : "none";
        boolean influxMonitor = "influxdb".equalsIgnoreCase(monitorType);
        boolean historyEnabled = collect != null && collect.getHistory() != null && collect.getHistory().isEnabled();
        boolean metricsPersist = collect != null && collect.getMetrics() != null
                && collect.getMetrics().isPersistToInflux();
        int metricsHistoryMinutes = collect != null && collect.getMetrics() != null
                ? Math.max(1, collect.getMetrics().getHistoryMinutes()) : 60;
        int sampleIntervalSec = collect != null && collect.getMetrics() != null
                ? Math.max(1, collect.getMetrics().getSampleIntervalSec()) : 10;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode.name().toLowerCase());
        body.put("collectEnabled", collect != null && collect.isEnabled());
        body.put("storeBackend", collect != null ? collect.getStoreBackend() : "caffeine");
        body.put("monitorType", monitorType);
        body.put("historyEnabled", historyEnabled);
        body.put("metricsPersistToInflux", metricsPersist);
        body.put("metricsHistoryMinutes", metricsHistoryMinutes);
        body.put("metricsSampleIntervalSec", sampleIntervalSec);
        // 点位历史曲线：仅 Influx；性能指标：Influx 或进程内环形缓冲
        body.put("pointHistoryMode", (influxMonitor && historyEnabled) ? "influx" : "unavailable");
        body.put("collectMetricsMode", (influxMonitor && metricsPersist) ? "influx" : "memory");
        return Result.ok(body);
    }
}
