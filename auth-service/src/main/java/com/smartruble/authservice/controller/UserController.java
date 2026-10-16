package com.smartruble.authservice.controller;

import com.smartruble.authservice.config.OpenApiConfig;
import com.smartruble.authservice.dto.ChangePasswordRequest;
import com.smartruble.authservice.dto.UserResponse;
import com.smartruble.authservice.entity.User;
import com.smartruble.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Текущий пользователь")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

    private final AuthService authService;

    @GetMapping("/me")
    @Operation(summary = "Данные текущего пользователя")
    public UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return UserResponse.from(user);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Сменить пароль (все refresh-токены будут отозваны)")
    public void changePassword(@Parameter(hidden = true) @AuthenticationPrincipal User user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user, request);
    }
}
