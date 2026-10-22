package com.omes.dgw.config;

import com.omes.dgw.auth.security.ConfigAuthenticationProvider;
import com.omes.dgw.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityJsonHandlers securityJsonHandlers;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            SecurityJsonHandlers securityJsonHandlers
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.securityJsonHandlers = securityJsonHandlers;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/login",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/actuator/metrics",
                                "/actuator/metrics/**"
                        ).permitAll()
                        // 盒子门户内嵌前端（hash 路由）；中控分离部署时这些路径通常不存在
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/assets/**"
                        ).permitAll()
                        .requestMatchers(
                                "/*.js",
                                "/*.css",
                                "/*.map",
                                "/*.ico",
                                "/*.svg",
                                "/*.png",
                                "/*.jpg",
                                "/*.jpeg",
                                "/*.gif",
                                "/*.webp",
                                "/*.woff",
                                "/*.woff2",
                                "/*.ttf"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityJsonHandlers)
                        .accessDeniedHandler(securityJsonHandlers))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(ConfigAuthenticationProvider configAuthenticationProvider) {
        return new ProviderManager(List.of(configAuthenticationProvider));
    }
}
