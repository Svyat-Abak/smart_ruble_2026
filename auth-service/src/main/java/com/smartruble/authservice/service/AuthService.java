package com.smartruble.authservice.service;

import com.smartruble.authservice.dto.*;
import com.smartruble.authservice.entity.Role;
import com.smartruble.authservice.entity.RoleName;
import com.smartruble.authservice.entity.User;
import com.smartruble.authservice.exception.ApiException;
import com.smartruble.authservice.repository.RoleRepository;
import com.smartruble.authservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw ApiException.conflict("Пользователь с таким телефоном уже существует");
        }
        String email = normalizeEmail(request.email());
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Пользователь с таким email уже существует");
        }
        Role userRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("В БД нет роли ROLE_EMPLOYEE"));

        User user = userRepository.saveAndFlush(User.builder()
                .phoneNumber(request.phoneNumber())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(new HashSet<>(Set.of(userRole)))
                .build());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.phoneNumber(), request.password()));
        return issueTokens((User) authentication.getPrincipal());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        User user = refreshTokenService.consume(request.refreshToken());
        if (!user.isEnabled()) {
            throw new DisabledException("Учётная запись заблокирована");
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional
    public void changePassword(User principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Текущий пароль указан неверно");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // после смены пароля разлогиниваем все сессии
        refreshTokenService.revokeAll(user);
    }

    public TokenValidationResponse validate(String token) {
        return jwtService.parseAndValidate(token)
                .filter(claims -> userRepository.findById(jwtService.extractUserId(claims))
                        .map(User::isActive).orElse(false))
                .map(this::toValidationResponse)
                .orElseGet(() -> new TokenValidationResponse(false, null, null, null));
    }

    private TokenValidationResponse toValidationResponse(Claims claims) {
        return new TokenValidationResponse(
                true,
                jwtService.extractUserId(claims),
                claims.get(JwtService.CLAIM_PHONE, String.class),
                jwtService.extractRoles(claims));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.create(user).getToken();
        return AuthResponse.of(accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds(), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }
}
