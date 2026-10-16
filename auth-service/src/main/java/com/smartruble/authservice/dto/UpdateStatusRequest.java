package com.smartruble.authservice.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull Boolean active) {
}
