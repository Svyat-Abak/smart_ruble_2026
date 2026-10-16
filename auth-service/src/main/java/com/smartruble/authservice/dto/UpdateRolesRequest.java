package com.smartruble.authservice.dto;

import com.smartruble.authservice.entity.RoleName;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<RoleName> roles) {
}
