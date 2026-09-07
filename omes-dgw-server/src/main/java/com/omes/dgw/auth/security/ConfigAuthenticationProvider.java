package com.omes.dgw.auth.security;

import com.omes.dgw.config.AppProperties;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 配置用户明文认证，避免 PasswordEncoder 升级导致缓存密码被改写后二次登录失败。
 */
@Component
public class ConfigAuthenticationProvider implements AuthenticationProvider {

    private final AppProperties appProperties;

    public ConfigAuthenticationProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String presentedPassword = authentication.getCredentials() == null
                ? null
                : authentication.getCredentials().toString();

        AppProperties.AdminUser config = appProperties.getSecurity().getUsers().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (!config.isEnabled()) {
            throw new BadCredentialsException("用户已禁用");
        }
        if (presentedPassword == null || !presentedPassword.equals(config.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        List<SimpleGrantedAuthority> authorities = config.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
