package com.smartruble.apigateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Настройки из секции app.* в application.yaml. */
@Validated
@ConfigurationProperties(prefix = "app")
public record GatewayProperties(Jwt jwt, RateLimit rateLimit, Cors cors) {

    /** Должны совпадать с настройками auth-service, иначе подпись токена не сойдётся. */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer) {
    }

    /** Token bucket: capacity — запас запросов, refillPerSecond — скорость пополнения. */
    public record RateLimit(boolean enabled, int capacity, int refillPerSecond) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
