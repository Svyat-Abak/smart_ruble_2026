package com.smartruble.apigateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поднимает шлюз целиком, а вместо auth-service — MockWebServer (поддельный HTTP-сервер).
 * Проверяем, что шлюз пропускает, блокирует и дополняет запросы правильно.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    static final MockWebServer authService = new MockWebServer();

    @LocalServerPort
    int port;

    WebTestClient client;

    /** Ходим в шлюз по настоящему HTTP, как это делал бы клиент. */
    @org.junit.jupiter.api.BeforeEach
    void createClient() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) throws IOException {
        authService.start();
        registry.add("AUTH_SERVICE_URL", () -> authService.url("/").toString());
        // на порту 1 никто не слушает — имитация упавшего сервиса
        registry.add("BILLING_SERVICE_URL", () -> "http://localhost:1");
        registry.add("app.rate-limit.enabled", () -> "false");
    }

    @AfterAll
    static void stop() throws IOException {
        authService.shutdown();
    }

    @BeforeEach
    void drainRecordedRequests() throws InterruptedException {
        while (authService.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // очищаем запросы, оставшиеся от предыдущего теста
        }
    }

    @Test
    void publicEndpointIsForwardedWithoutToken() throws Exception {
        authService.enqueue(json("{\"accessToken\":\"x\"}"));

        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"phoneNumber\":\"+79001234567\",\"password\":\"password123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody().jsonPath("$.accessToken").isEqualTo("x");

        RecordedRequest forwarded = authService.takeRequest(1, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/auth/login");
        assertThat(forwarded.getBody().readUtf8()).contains("+79001234567");
    }

    @Test
    void protectedEndpointWithoutTokenIs401AndNotForwarded() throws Exception {
        client.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.status").isEqualTo(401);

        assertThat(authService.takeRequest(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void invalidTokensAreRejected() {
        String wrongSecret = TestTokens.token(
                "b3RoZXItc2VjcmV0LW90aGVyLXNlY3JldC1vdGhlci1zZWNyZXQtb3RoZXI=", TestTokens.ISSUER,
                "u1", List.of("ROLE_EMPLOYEE"), Instant.now().plusSeconds(600));
        String expired = TestTokens.token(TestTokens.SECRET, TestTokens.ISSUER,
                "u1", List.of("ROLE_EMPLOYEE"), Instant.now().minusSeconds(600));
        String wrongIssuer = TestTokens.token(TestTokens.SECRET, "someone-else",
                "u1", List.of("ROLE_EMPLOYEE"), Instant.now().plusSeconds(600));

        for (String token : List.of(wrongSecret, expired, wrongIssuer, "garbage")) {
            client.get().uri("/api/v1/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().returnResult();
        }
    }

    @Test
    void validTokenIsForwardedWithUserHeadersAndSpoofedHeadersRemoved() throws Exception {
        authService.enqueue(json("{\"id\":\"user-42\"}"));
        String token = TestTokens.token("user-42", List.of("ROLE_EMPLOYEE", "ROLE_MANAGER"));

        client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Roles", "ROLE_ADMIN")        // попытка подделки
                .header("X-User-Id", "someone-else")
                .exchange()
                .expectStatus().isOk()
                    .expectBody().returnResult();

        RecordedRequest forwarded = authService.takeRequest(1, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("user-42");
        assertThat(forwarded.getHeader("X-User-Roles")).isEqualTo("ROLE_EMPLOYEE,ROLE_MANAGER");
        assertThat(forwarded.getHeader("X-User-Phone")).isEqualTo("+79001234567");
        assertThat(forwarded.getHeader("X-Request-Id")).isNotBlank();
        assertThat(forwarded.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    @Test
    void adminPathRequiresAdminRole() throws Exception {
        client.get().uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.token("u1", List.of("ROLE_MANAGER")))
                .exchange()
                .expectStatus().isForbidden()
                    .expectBody().returnResult();
        assertThat(authService.takeRequest(200, TimeUnit.MILLISECONDS)).isNull();

        authService.enqueue(json("{\"content\":[]}"));
        client.get().uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.token("admin", List.of("ROLE_ADMIN")))
                .exchange()
                .expectStatus().isOk()
                    .expectBody().returnResult();
    }

    @Test
    void downServiceReturns503FromFallback() {
        client.get().uri("/api/v1/billing/invoices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.token("u1", List.of("ROLE_EMPLOYEE")))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").value(m -> assertThat((String) m).contains("billing-service"))
                .jsonPath("$.path").isEqualTo("/api/v1/billing/invoices");
    }

    @Test
    void unknownPathIs404ForAuthenticatedUser() {
        client.get().uri("/api/v1/unknown")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.token("u1", List.of("ROLE_EMPLOYEE")))
                .exchange()
                .expectStatus().isNotFound()
                    .expectBody().returnResult();
    }

    @Test
    void corsPreflightIsAllowedForConfiguredOrigin() {
        client.options().uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectBody().returnResult();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
