package com.smartruble.authservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginMeRefreshLogout() throws Exception {
        JsonNode registered = postJson("/api/v1/auth/register",
                "{\"phoneNumber\":\"+79001112233\",\"email\":\"u1@example.com\",\"password\":\"password123\"}", 201);
        org.assertj.core.api.Assertions.assertThat(registered.get("user").get("roles").get(0).asText())
                .isEqualTo("ROLE_EMPLOYEE");
        org.assertj.core.api.Assertions.assertThat(registered.get("user").get("createdAt").isNull()).isFalse();

        // повторная регистрация -> 409
        postJson("/api/v1/auth/register",
                "{\"phoneNumber\":\"+79001112233\",\"password\":\"password123\"}", 409);

        // неверный пароль -> 401
        postJson("/api/v1/auth/login", "{\"phoneNumber\":\"+79001112233\",\"password\":\"wrong-pass\"}", 401);

        JsonNode login = postJson("/api/v1/auth/login",
                "{\"phoneNumber\":\"+79001112233\",\"password\":\"password123\"}", 200);
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+79001112233"));

        mockMvc.perform(get("/api/v1/auth/validate").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_EMPLOYEE")));

        // обычному пользователю админка недоступна
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());

        // rotation: новый refresh работает, старый — нет
        JsonNode refreshed = postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", 200);
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", 401);
        postJson("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + refreshed.get("refreshToken").asText() + "\"}", 200);

        JsonNode relogin = postJson("/api/v1/auth/login",
                "{\"phoneNumber\":\"+79001112233\",\"password\":\"password123\"}", 200);
        String newRefresh = relogin.get("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isNoContent());
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + newRefresh + "\"}", 401);
    }

    @Test
    void unauthenticatedAndInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/validate").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void adminCanManageRoles() throws Exception {
        JsonNode user = postJson("/api/v1/auth/register",
                "{\"phoneNumber\":\"+79005556677\",\"password\":\"password123\"}", 201);
        String userId = user.get("user").get("id").asText();

        String adminToken = postJson("/api/v1/auth/login",
                "{\"phoneNumber\":\"+70000000000\",\"password\":\"admin12345\"}", 200).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(put("/api/v1/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_MANAGER\",\"ROLE_FINANCE_DIRECTOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ROLE_FINANCE_DIRECTOR")));

        mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        postJson("/api/v1/auth/login", "{\"phoneNumber\":\"+79005556677\",\"password\":\"password123\"}", 403);
    }

    @Test
    void swaggerIsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private JsonNode postJson(String url, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : objectMapper.readTree(response);
    }
}
