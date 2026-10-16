package com.smartruble.authservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret                 Base64-ключ для HMAC-SHA подписи (минимум 256 бит)
 * @param issuer                 значение claim "iss"
 * @param accessTokenExpiration  время жизни access-токена
 * @param refreshTokenExpiration время жизни refresh-токена
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration
) {
}
