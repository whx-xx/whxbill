package com.whxbill.backend.security;

import com.whxbill.backend.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    // 保存 app.jwt 下的配置：secret、过期时间、issuer。
    private final JwtProperties jwtProperties;
    // JJWT 要求使用 SecretKey 参与 HMAC 签名，不能直接拿字符串签名。
    private final SecretKey secretKey;

    /**
     * 初始化 JWT 工具类时，把配置文件/环境变量里的 secret 转成可签名的密钥对象。
     */
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // 将配置中的 JWT secret 转成 HMAC 签名密钥；签发和验签必须使用同一把密钥。
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建访问令牌。前端每次访问受保护接口时，会把这个 token 放进 Authorization 请求头。
     */
    public String createAccessToken(LoginUser loginUser) {
        // 以当前时间为基准计算签发时间和过期时间。
        Instant now = Instant.now();
        return Jwts.builder()
            // issuer 标识签发系统，便于以后多系统接入时区分来源。
            .issuer(jwtProperties.getIssuer())
            // subject 通常放用户唯一标识，这里放用户名。
            .subject(loginUser.getUsername())
            // 自定义 claim 保存用户 id，后续业务代码经常需要用它查当前用户数据。
            .claim("userId", loginUser.getUserId())
            // nickname、roles、permissions 放进 accessToken，过滤器解析后可以直接构造 LoginUser。
            .claim("nickname", loginUser.getNickname())
            .claim("roles", loginUser.getRoles())
            .claim("permissions", loginUser.getPermissions())
            // jti 是 token 的唯一编号，便于排查和后续扩展黑名单。
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(jwtProperties.getAccessTokenExpireSeconds())))
            // accessToken 内放用户身份和权限，接口访问时由过滤器解析并放入 Spring Security 上下文。
            .signWith(secretKey)
            .compact();
    }

    /**
     * 创建刷新令牌。它不直接访问业务接口，只在 accessToken 过期时换新 token。
     */
    public String createRefreshToken(LoginUser loginUser) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(loginUser.getUsername())
            .claim("userId", loginUser.getUserId())
            // type 用于后端 refresh 接口校验，防止拿 accessToken 冒充 refreshToken。
            .claim("type", "refresh")
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(jwtProperties.getRefreshTokenExpireSeconds())))
            // refreshToken 只用于续期，后端会额外用 type=refresh 区分它和访问令牌。
            .signWith(secretKey)
            .compact();
    }

    /**
     * 解析 JWT 的载荷，同时完成签名校验和过期时间校验。
     */
    public Claims parseClaims(String token) {
        // verifyWith 会校验签名，token 被篡改或不是本系统 secret 签发时会解析失败。
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * 把 accessToken 中的 claims 还原成 Spring Security 能识别的 LoginUser。
     */
    @SuppressWarnings("unchecked")
    public LoginUser parseLoginUser(String token) {
        Claims claims = parseClaims(token);
        return LoginUser.builder()
            // JWT claim 取出来是 Object，所以这里统一转成字符串再转 Long。
            .userId(Long.valueOf(String.valueOf(claims.get("userId"))))
            .username(claims.getSubject())
            .nickname((String) claims.getOrDefault("nickname", claims.getSubject()))
            // token 认证场景不需要密码，留空即可。
            .password("")
            .roles((List<String>) claims.getOrDefault("roles", List.of()))
            .permissions((List<String>) claims.getOrDefault("permissions", List.of()))
            .status(1)
            .build();
    }
}
