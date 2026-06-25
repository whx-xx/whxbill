package com.whxbill.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.common.api.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService customUserDetailsService;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 这里定义整套接口安全规则：哪些接口放行，哪些接口要登录，未登录/无权限时返回统一 JSON。
        http
            .csrf(csrf -> csrf.disable()) // 禁用 CSRF，使用 JWT，不依赖传统 Session 表单
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) //后端不创建 Session，每次请求都靠 token 证明身份
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/ws/**").permitAll() // 放行
                .anyRequest().authenticated()) // 其他都需要认证
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class) // 加入 JWT 过滤器，在 Spring Security 默认的用户名密码过滤器之前，先执行自己的 JWT 过滤器
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(ResultCode.UNAUTHORIZED)));
                }) // 未登录 401
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(ResultCode.FORBIDDEN)));
                })); // 已登录但是没有权限 403
        return http.build();
    }

    // 认证提供者
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // 账号密码登录时，Spring Security 会走这里；实际用户信息由自定义的 UserDetailsService 提供。
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService); // 负责根据用户名查数据库
        provider.setPasswordEncoder(passwordEncoder()); // 负责校验密码是否匹配
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用 Spring 官方推荐的委托式密码编码器，默认支持 bcrypt 等多种算法。
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
