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

    @Operation(summary = "门户运行时信息（mode / collectEnabled）")
    @GetMapping("/portal")
    public Result<Map<String, Object>> portal() {
        PortalMode mode = PortalMode.from(
                appProperties.getPortal() != null ? appProperties.getPortal().getMode() : null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode.name().toLowerCase());
        body.put("collectEnabled", appProperties.getCollect() != null && appProperties.getCollect().isEnabled());
        body.put("storeBackend", appProperties.getCollect() != null
                ? appProperties.getCollect().getStoreBackend() : "caffeine");
        body.put("monitorType", appProperties.getCollect() != null
                ? appProperties.getCollect().getMonitorType() : "none");
        return Result.ok(body);
    }
}
