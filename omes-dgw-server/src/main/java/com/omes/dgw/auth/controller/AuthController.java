package com.omes.dgw.auth.controller;

import com.omes.dgw.common.Result;
import com.omes.dgw.device.dto.LoginRequest;
import com.omes.dgw.device.dto.LoginResponse;
import com.omes.dgw.device.dto.MenuVo;
import com.omes.dgw.device.dto.UserInfoVo;
import com.omes.dgw.auth.service.AuthService;
import com.omes.dgw.auth.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final MenuService menuService;

    public AuthController(AuthService authService, MenuService menuService) {
        this.authService = authService;
        this.menuService = menuService;
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

    @Operation(summary = "当前用户菜单")
    @GetMapping("/menus")
    public Result<List<MenuVo>> menus(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());
        return Result.ok(menuService.menusForRoles(roles));
    }
}
