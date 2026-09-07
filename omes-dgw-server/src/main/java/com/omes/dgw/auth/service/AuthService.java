package com.omes.dgw.auth.service;

import com.omes.dgw.config.AppProperties;
import com.omes.dgw.device.dto.LoginRequest;
import com.omes.dgw.device.dto.LoginResponse;
import com.omes.dgw.device.dto.MenuVo;
import com.omes.dgw.device.dto.UserInfoVo;
import com.omes.dgw.auth.security.ConfigUserDetailsService;
import com.omes.dgw.auth.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final ConfigUserDetailsService userDetailsService;
    private final MenuService menuService;
    private final AppProperties appProperties;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            ConfigUserDetailsService userDetailsService,
            MenuService menuService,
            AppProperties appProperties
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.menuService = menuService;
        this.appProperties = appProperties;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        var userDetails = userDetailsService.loadUserByUsername(authentication.getName());
        String token = jwtTokenProvider.createToken(userDetails);
        Set<String> roles = extractRoleNames(authentication);
        UserInfoVo user = buildUserInfo(authentication.getName(), roles);
        List<MenuVo> menus = menuService.menusForRoles(roles);
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInMs(appProperties.getSecurity().getJwt().getExpirationMs())
                .user(user)
                .menus(menus)
                .build();
    }

    public UserInfoVo currentUser(Authentication authentication) {
        Set<String> roles = extractRoleNames(authentication);
        return buildUserInfo(authentication.getName(), roles);
    }

    private UserInfoVo buildUserInfo(String username, Set<String> roles) {
        AppProperties.AdminUser config = userDetailsService.findConfigUser(username);
        String nickname = config != null ? config.getNickname() : username;
        return UserInfoVo.builder()
                .username(username)
                .nickname(nickname)
                .roles(roles.stream().sorted().collect(Collectors.toList()))
                .build();
    }

    private Set<String> extractRoleNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());
    }
}
