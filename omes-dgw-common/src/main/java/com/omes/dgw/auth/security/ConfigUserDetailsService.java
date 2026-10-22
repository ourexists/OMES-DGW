package com.omes.dgw.auth.security;

import com.omes.dgw.config.AppProperties;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConfigUserDetailsService implements UserDetailsService {

    private final AppProperties appProperties;

    public ConfigUserDetailsService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppProperties.AdminUser config = findConfigUser(username);
        if (config == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return toUserDetails(config);
    }

    public AppProperties.AdminUser findConfigUser(String username) {
        return appProperties.getSecurity().getUsers().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    private UserDetails toUserDetails(AppProperties.AdminUser config) {
        List<SimpleGrantedAuthority> authorities = config.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return User.builder()
                .username(config.getUsername())
                .password(config.getPassword())
                .disabled(!config.isEnabled())
                .authorities(authorities)
                .build();
    }
}
