package com.smartruble.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartruble.billing.counterparties.CounterpartyDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class CounterpartyFlowTest {
    private static final String ROOT = "/api/v1/billing/counterparties";
    private static final String MANAGER = "ROLE_MANAGER";
    private static final String DIRECTOR = "ROLE_FINANCE_DIRECTOR";
    private static final String CONTRACTOR = "ROLE_CONTRACTOR";
    private final UUID actor = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CounterpartyDirectory directory;

    private JsonNode call(MockHttpServletRequestBuilder request, String role, Object body, int expected)
            throws Exception {
        if (role != null) request.header("X-User-Id", actor).header("X-User-Roles", role);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        byte[] bytes = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray();
        return bytes.length == 0 ? json.nullNode() : json.readTree(bytes);
    }

    private Map<String,String> company(String inn) {
        return Map.of("name","Тест " + inn, "inn",inn, "type","LEGAL_ENTITY", "kpp","7701AB001",
                "email","test@example.org");
    }

    @Test
    void creationStatusHistoryAndContractorVisibility() throws Exception {
        var request = company("7707083893");
        call(get(ROOT), null, null, 401);
        JsonNode created = call(post(ROOT), MANAGER, request, 201);
        assertThat(created.path("verificationStatus").asText()).isEqualTo("NEW");
        assertThat(created.path("contactEmail").asText()).isEqualTo("test@example.org");
        call(post(ROOT), MANAGER, request, 409);
        String card = ROOT + "/" + created.path("id").asText();
        var block = Map.of("status","BLOCKED","reason","Решение директора");
        call(post(card + "/status"), MANAGER, block, 403);
        call(post(card + "/status"), DIRECTOR, Map.of("status","BLOCKED"), 400);
        assertThat(call(get(card + "/status-history"), DIRECTOR, null, 200).size()).isOne();
        call(post(card + "/status"), DIRECTOR, block, 200);
        JsonNode history = call(get(card + "/status-history"), DIRECTOR, null, 200);
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).path("oldStatus").asText()).isEqualTo("NEW");
        assertThat(history.get(0).path("newStatus").asText()).isEqualTo("BLOCKED");
        call(get(card), CONTRACTOR, null, 404);
        assertThat(call(get(ROOT).param("search",request.get("inn")), CONTRACTOR, null, 200)
                .path("content").size()).isZero();
    }

    @Test
    void invalidRequisitesIdentifyField() throws Exception {
        var invalid = Map.of("name","ИП Иванов","type","INDIVIDUAL_ENTREPRENEUR",
                "inn","500100732259","kpp","770701001");
        assertThat(call(post(ROOT), MANAGER, invalid, 400).path("fieldErrors").has("kpp")).isTrue();
        assertThat(call(post(ROOT), MANAGER, company("123456789012"), 400).path("fieldErrors").has("inn")).isTrue();
    }

    @Test
    void updatesBankAccountsAndMemberLink() throws Exception {
        UUID id = UUID.fromString(call(post(ROOT), MANAGER, company("7736050003"), 201).path("id").asText());
        String card = ROOT + "/" + id;
        call(put(card), MANAGER, Map.of("name","Поставщик", "shortName","ПС"), 200);
        assertThat(directory.find(id).orElseThrow().name()).isEqualTo("Поставщик");
        String accounts = card + "/bank-accounts";
        var first = call(post(accounts), MANAGER, bank("40702810900000000001"), 201);
        var second = call(post(accounts), MANAGER, bank("40702810900000000002"), 201);
        assertThat(first.path("primary").asBoolean()).isTrue();
        assertThat(second.path("primary").asBoolean()).isFalse();
        var deactivate = Map.of("action","DEACTIVATE");
        String oldAccount = accounts + "/" + first.path("id").asText();
        call(patch(oldAccount), MANAGER, deactivate, 409);
        call(patch(accounts + "/" + second.path("id").asText()), MANAGER, Map.of("action","MAKE_PRIMARY"), 200);
        call(patch(oldAccount), MANAGER, deactivate, 200);
        JsonNode stored = call(get(card), MANAGER, null, 200).path("bankAccounts");
        assertThat(stored.size()).isEqualTo(2);
        stored.forEach(account -> {
            boolean current = account.get("id").equals(second.get("id"));
            assertThat(account.get("primary").asBoolean()).isEqualTo(current);
            assertThat(account.get("active").asBoolean()).isEqualTo(current);
        });
        UUID member = UUID.randomUUID();
        var link = Map.of("userId", member);
        call(post(card + "/members"), MANAGER, link, 201);
        assertThat(directory.findCompanyOf(member)).contains(id);
        // Retry the same association without creating a second membership.
        call(post(card + "/members"), MANAGER, link, 201);
        assertThat(directory.findCompanyOf(member)).contains(id);
        call(delete(card + "/members/" + member), MANAGER, null, 204);
        assertThat(directory.findCompanyOf(member)).isEmpty();
    }

    private Map<String,String> bank(String number) {
        return Map.of("accountNumber",number,"bik","044525225","bankName","Тестовый банк");
    }
}
