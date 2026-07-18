package io.quizchef.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The complete host onboarding journey, end to end over the public API —
 * the whole point of Phase 3 PR #1: a fresh user becomes a quiz host
 * through supported product flows, with no minted tokens and no repository
 * shortcuts anywhere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class HostOnboardingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aFreshUserBecomesAHostThroughTheProductFlow() throws Exception {
        String email = "onboarding-" + UUID.randomUUID() + "@example.com";

        // Register → log in: a plain USER, and the token's claim says so.
        register(email);
        String token = login(email);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(Matchers.containsInAnyOrder("USER")))
                .andExpect(jsonPath("$.displayName").value("Aspiring Host"))
                .andExpect(jsonPath("$.email").value(email));

        // A USER cannot author yet — the server, not the UI, says no.
        mockMvc.perform(post("/api/v1/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "defaultLanguage": "en",
                                  "localization": {"languageCode": "en", "title": "Too early"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.permission.denied"));

        // Request host access: granted, durably.
        mockMvc.perform(post("/api/v1/users/me/host-access")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"))
                .andExpect(jsonPath("$.roles").value(Matchers.containsInAnyOrder("USER", "QUIZ_MASTER")))
                .andExpect(jsonPath("$.permissions").value(Matchers.hasItem("QUIZ_HOST")));

        // The SAME token now authors — request-time authorization reads the
        // persisted roles, so no new login is needed.
        mockMvc.perform(post("/api/v1/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "defaultLanguage": "en",
                                  "localization": {"languageCode": "en", "title": "My First Quiz"}
                                }
                                """))
                .andExpect(status().isCreated());

        // /users/me reflects the promotion immediately, same token.
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(Matchers.containsInAnyOrder("USER", "QUIZ_MASTER")));

        // A repeat request is a harmless no-op.
        mockMvc.perform(post("/api/v1/users/me/host-access")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"));

        // Roles survive logout/login: a fresh login's token claim now
        // carries the durable QUIZ_MASTER.
        String freshLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "StrongPassword@123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities").value(
                        Matchers.containsInAnyOrder("USER", "QUIZ_MASTER")))
                .andReturn().getResponse().getContentAsString();
        String newToken = readJson(freshLogin).get("token").asText();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(Matchers.containsInAnyOrder("USER", "QUIZ_MASTER")));
    }

    @Test
    void anonymousCallersCannotRequestHostAccess() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/host-access"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentsTheOnboardingEndpoint() throws Exception {
        JsonNode paths = readJson(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("paths");

        org.assertj.core.api.Assertions.assertThat(
                paths.has("/api/v1/users/me/host-access")).isTrue();
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Aspiring Host",
                                  "email": "%s",
                                  "password": "StrongPassword@123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "StrongPassword@123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("token").asText();
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
