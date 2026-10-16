package com.smartruble.authservice.controller;

import com.smartruble.authservice.config.OpenApiConfig;
import com.smartruble.authservice.dto.UpdateRolesRequest;
import com.smartruble.authservice.dto.UpdateStatusRequest;
import com.smartruble.authservice.dto.UserResponse;
import com.smartruble.authservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Управление пользователями и ролями (только ROLE_ADMIN)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Список пользователей")
    public Page<UserResponse> findAll(@ParameterObject
                                      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                      Pageable pageable) {
        return userService.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Пользователь по id")
    public UserResponse findById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Назначить роли пользователю")
    public UserResponse updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(id, request.roles());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Заблокировать / разблокировать пользователя")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return userService.updateStatus(id, request.active());
    }
}
