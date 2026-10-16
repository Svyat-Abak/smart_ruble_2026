package com.smartruble.apigateway;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitTest {

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
    static void props(DynamicPropertyRegistry registry) throws IOException {
        authService.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setBody("{}").setHeader("Content-Type", "application/json");
            }
        });
        authService.start();
        registry.add("AUTH_SERVICE_URL", () -> authService.url("/").toString());
        registry.add("app.rate-limit.capacity", () -> "3");
        registry.add("app.rate-limit.refill-per-second", () -> "1");
    }

    @AfterAll
    static void stop() throws IOException {
        authService.shutdown();
    }

    @Test
    void fourthRequestInBurstIsRejectedPerUser() {
        String alice = "Bearer " + TestTokens.token("alice", List.of("ROLE_EMPLOYEE"));
        String bob = "Bearer " + TestTokens.token("bob", List.of("ROLE_EMPLOYEE"));

        for (int i = 0; i < 3; i++) {
            client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, alice)
                    .exchange().expectStatus().isOk()
                    .expectBody().returnResult();
        }
        client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, alice)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "1")
                .expectBody().returnResult();

        // у другого пользователя своё ведро
        client.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bob)
                .exchange().expectStatus().isOk()
                    .expectBody().returnResult();
    }
}
