package com.whxbill.backend.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.common.api.ResultCode;
import com.whxbill.backend.modules.system.entity.SysUser;
import com.whxbill.backend.modules.system.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    /**
     * 每个 HTTP 请求都会经过这里。它负责从请求头提取 token，并把用户身份放进 Spring Security 上下文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 前端请求头格式固定为 Authorization: Bearer accessToken。
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            try {
                // 去掉 "Bearer " 前缀，只保留真正的 JWT 字符串。
                String token = authorization.substring(7);
                // Redis 中存在 token 才认为当前登录态有效；退出登录或过期后这里会失效。
                String cacheKey = "whx:bill:token:" + token;
                if (stringRedisTemplate.hasKey(cacheKey)) {
                    // 解析 token，取出用户 id、角色和权限。
                    LoginUser loginUser = jwtTokenProvider.parseLoginUser(token);
                    // 用户被管理员禁用后，即使 token 还没过期，也要立即拒绝继续访问。
                    if (!isUserEnabled(loginUser)) {
                        stringRedisTemplate.delete(cacheKey);
                        SecurityContextHolder.clearContext();
                        writeDisabledResponse(response);
                        return;
                    }
                    // 构造 Spring Security 的认证对象
                    UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    // 把请求来源等细节附加进去，Spring Security 的审计或扩展功能可以用到。
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // 这一步是关键：后续 @PreAuthorize 会从 SecurityContextHolder 里读取权限。
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            } catch (Exception ignored) {
                // token 解析失败时不抛出细节，清空认证信息，让后续权限链路统一返回未登录。
                SecurityContextHolder.clearContext();
            }
        }
        // 继续执行后面的过滤器和真正的 Controller。
        filterChain.doFilter(request, response);
    }

    /**
     * 每次请求都查一次用户状态，保证管理员禁用账号后立即生效。
     */
    private boolean isUserEnabled(LoginUser loginUser) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getId, loginUser.getUserId())
            .last("limit 1"));
        return user != null && Integer.valueOf(1).equals(user.getStatus());
    }

    /**
     * 被禁用账号访问接口时，直接返回统一 JSON，而不是继续进入 Controller。
     */
    private void writeDisabledResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
            ApiResponse.failure(ResultCode.UNAUTHORIZED.getCode(), "账号已被禁用，请联系管理员")
        ));
    }
}
