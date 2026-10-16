package com.smartruble.authservice.dto;

import java.util.List;
import java.util.UUID;

/** Ответ для API-gateway / других сервисов: кому принадлежит токен и какие у него роли. */
public record TokenValidationResponse(
        boolean valid,
        UUID userId,
        String phoneNumber,
        List<String> roles
) {
}
