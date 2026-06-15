package com.whxbill.backend.config;

import com.whxbill.backend.config.properties.JwtProperties;
import com.whxbill.backend.config.properties.OssProperties;
import com.whxbill.backend.config.properties.CorsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, OssProperties.class, CorsProperties.class})
public class AppPropertiesConfig {
}
