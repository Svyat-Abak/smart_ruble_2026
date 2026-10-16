package com.smartruble.apigateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Выпускает токены в формате auth-service, чтобы тестировать шлюз без него. */
final class TestTokens {

    static final String SECRET = "c21hcnRydWJsZS1hdXRoLXNlcnZpY2UtZGV2LXNlY3JldC1rZXktY2hhbmdlLW1lLWluLXByb2Q=";
    static final String ISSUER = "smartruble-auth-service";

    private TestTokens() {
    }

    static String token(String userId, List<String> roles) {
        return token(SECRET, ISSUER, userId, roles, Instant.now().plusSeconds(600));
    }

    static String token(String secret, String issuer, String userId, List<String> roles, Instant expiresAt) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId)
                .claim("phone", "+79001234567")
                .claim("roles", roles)
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
