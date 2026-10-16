package com.smartruble.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "+79001234567")
        @NotBlank
        @Pattern(regexp = ValidationPatterns.PHONE, message = "Телефон в формате +79001234567")
        String phoneNumber,

        @Schema(example = "user@example.com")
        @Email
        @Size(max = 100)
        String email,

        @Schema(example = "password123")
        @NotBlank
        @Size(min = 8, max = 100, message = "Пароль от 8 до 100 символов")
        String password
) {
}
