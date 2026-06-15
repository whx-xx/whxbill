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

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(LoginUser loginUser) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(loginUser.getUsername())
            .claim("userId", loginUser.getUserId())
            .claim("nickname", loginUser.getNickname())
            .claim("roles", loginUser.getRoles())
            .claim("permissions", loginUser.getPermissions())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(jwtProperties.getAccessTokenExpireSeconds())))
            .signWith(secretKey)
            .compact();
    }

    public String createRefreshToken(LoginUser loginUser) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(loginUser.getUsername())
            .claim("userId", loginUser.getUserId())
            .claim("type", "refresh")
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(jwtProperties.getRefreshTokenExpireSeconds())))
            .signWith(secretKey)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    @SuppressWarnings("unchecked")
    public LoginUser parseLoginUser(String token) {
        Claims claims = parseClaims(token);
        return LoginUser.builder()
            .userId(Long.valueOf(String.valueOf(claims.get("userId"))))
            .username(claims.getSubject())
            .nickname((String) claims.getOrDefault("nickname", claims.getSubject()))
            .password("")
            .roles((List<String>) claims.getOrDefault("roles", List.of()))
            .permissions((List<String>) claims.getOrDefault("permissions", List.of()))
            .status(1)
            .build();
    }
}
