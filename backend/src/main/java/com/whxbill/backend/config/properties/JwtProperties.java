package com.whxbill.backend.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    @NotBlank(message = "JWT secret must be provided by WHX_JWT_SECRET")
    @Size(min = 32, message = "JWT secret length must be at least 32 characters")
    private String secret;
    private Long accessTokenExpireSeconds;
    private Long refreshTokenExpireSeconds;
    private String issuer;
}
