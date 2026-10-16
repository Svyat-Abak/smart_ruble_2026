package com.smartruble.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "+79001234567") @NotBlank String phoneNumber,
        @Schema(example = "password123") @NotBlank String password
) {
}
