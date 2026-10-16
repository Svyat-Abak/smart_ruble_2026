package com.smartruble.authservice.service;

import com.smartruble.authservice.config.JwtProperties;
import com.smartruble.authservice.entity.RefreshToken;
import com.smartruble.authservice.entity.User;
import com.smartruble.authservice.exception.ApiException;
import com.smartruble.authservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Refresh-токены — непрозрачные случайные строки, хранятся в БД.
 * При каждом обновлении старый токен удаляется и выдаётся новый (rotation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public RefreshToken create(User user) {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenExpiration()))
                .build();
        return refreshTokenRepository.save(token);
    }

    /**
     * Проверяет refresh-токен и удаляет его (одноразовый). Возвращает владельца.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public User consume(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> ApiException.unauthorized("Refresh-токен недействителен"));
        refreshTokenRepository.delete(token);
        if (token.isExpired()) {
            throw ApiException.unauthorized("Срок действия refresh-токена истёк");
        }
        return token.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.deleteByToken(rawToken);
    }

    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.deleteAllByUser(user);
    }

    /** Раз в сутки чистим протухшие токены. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        int removed = refreshTokenRepository.deleteExpired(Instant.now());
        log.info("Удалено устаревших refresh-токенов: {}", removed);
    }
}
