package com.omes.dgw.deploy;

import com.omes.dgw.common.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/restart")
@PreAuthorize("hasRole('ADMIN')")
public class SystemRestartController {
    private final SystemRestartService service;

    public SystemRestartController(SystemRestartService service) {
        this.service = service;
    }

    @GetMapping
    public Result<SystemRestartService.RestartStatus> status() {
        return Result.ok(service.status());
    }

    @PostMapping
    public Result<SystemRestartService.RestartStatus> restart(Authentication authentication) {
        return Result.ok(service.restart(authentication.getName()));
    }
}
