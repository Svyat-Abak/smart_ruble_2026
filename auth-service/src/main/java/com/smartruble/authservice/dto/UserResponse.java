package com.smartruble.authservice.dto;

import com.smartruble.authservice.entity.User;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String phoneNumber,
        String email,
        boolean active,
        List<String> roles,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.isActive(),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList(),
                user.getCreatedAt());
    }
}
