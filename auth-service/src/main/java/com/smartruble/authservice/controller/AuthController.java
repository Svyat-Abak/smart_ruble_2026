package com.smartruble.authservice.controller;

import com.smartruble.authservice.config.OpenApiConfig;
import com.smartruble.authservice.dto.*;
import com.smartruble.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация, вход, обновление и проверка токенов")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Регистрация нового пользователя (роль ROLE_EMPLOYEE)")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Вход по телефону и паролю")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Получить новую пару токенов по refresh-токену")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Выход: отзыв refresh-токена")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    @GetMapping("/validate")
    @Operation(summary = "Проверка access-токена (для API-gateway и других сервисов)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public ResponseEntity<TokenValidationResponse> validate(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        TokenValidationResponse response = authorization != null && authorization.startsWith("Bearer ")
                ? authService.validate(authorization.substring(7).trim())
                : new TokenValidationResponse(false, null, null, null);
        return ResponseEntity.status(response.valid() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).body(response);
    }
}
