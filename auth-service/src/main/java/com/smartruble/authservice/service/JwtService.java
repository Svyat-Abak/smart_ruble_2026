package com.smartruble.authservice.service;

import com.smartruble.authservice.config.JwtProperties;
import com.smartruble.authservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Выпуск и проверка access-токенов (JWT, HS256).
 * <p>
 * Формат токена: sub = id пользователя (UUID), phone, email, roles = ["ROLE_EMPLOYEE", ...].
 * Остальные микросервисы могут проверять его тем же секретом без обращения к auth-service.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PHONE = "phone";
    public static final String CLAIM_EMAIL = "email";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_PHONE, user.getPhoneNumber())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.accessTokenExpiration())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Проверяет подпись, срок действия и issuer.
     *
     * @return claims токена или пусто, если токен невалиден
     */
    public Optional<Claims> parseAndValidate(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }

    public long getAccessTokenTtlSeconds() {
        return jwtProperties.accessTokenExpiration().toSeconds();
    }
}
