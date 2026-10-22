package com.omes.dgw.auth.controller;

import com.omes.dgw.auth.service.AuthService;
import com.omes.dgw.common.Result;
import com.omes.dgw.device.dto.LoginRequest;
import com.omes.dgw.device.dto.LoginResponse;
import com.omes.dgw.device.dto.UserInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<UserInfoVo> me(Authentication authentication) {
        return Result.ok(authService.currentUser(authentication));
    }
}
