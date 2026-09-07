package com.omes.dgw.auth.controller;

import com.omes.dgw.common.Result;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.controller.core.DeviceLifecycleManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "工作台")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DeviceLifecycleManager deviceLifecycleManager;

    public DashboardController(DeviceLifecycleManager deviceLifecycleManager) {
        this.deviceLifecycleManager = deviceLifecycleManager;
    }

    @Operation(summary = "工作台概览")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        List<DeviceRealtimeObj> realtime = deviceLifecycleManager.listEnabledRealtime();
        long online = realtime.stream().filter(DeviceRealtimeObj::isOnline).count();
        Map<String, Object> body = new HashMap<>();
        body.put("serverTime", Instant.now().toString());
        body.put("status", "running");
        body.put("message", "管理平台基础服务已就绪");
        body.put("deviceTotal", realtime.size());
        body.put("deviceOnline", online);
        body.put("deviceOffline", realtime.size() - online);
        return Result.ok(body);
    }
}
