package com.smartruble.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        boolean enabled,
        String phoneNumber,
        String email,
        String password
) {
}
