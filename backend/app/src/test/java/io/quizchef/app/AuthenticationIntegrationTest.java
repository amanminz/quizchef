package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.event.IdentityAuthenticatedEvent;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
import io.quizchef.identity.infrastructure.persistence.UserProfileRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end authentication: login contract, session-bound JWTs, single
 * active session, CurrentUser population, and indistinguishable failures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthenticationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Test-only probe: the first protected endpoint. Lets the suite prove
     * that the filter populates CurrentUser and that protection applies.
     */
    @RestController
    @RequestMapping("/api/v1/test")
    static class CurrentUserProbeController {

        private final CurrentUserProvider currentUserProvider;

        CurrentUserProbeController(CurrentUserProvider currentUserProvider) {
            this.currentUserProvider = currentUserProvider;
        }

        @GetMapping("/me")
        CurrentUser me() {
            return currentUserProvider.currentUser();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeAndEventCapture {

        final List<IdentityAuthenticatedEvent> authenticated = new CopyOnWriteArrayList<>();

        @EventListener
        void on(IdentityAuthenticatedEvent event) {
            authenticated.add(event);
        }

        @Bean
        CurrentUserProbeController currentUserProbeController(CurrentUserProvider currentUserProvider) {
            return new CurrentUserProbeController(currentUserProvider);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private IdentitySessionRepository identitySessionRepository;

    @Autowired
    private ProbeAndEventCapture capture;

    @BeforeEach
    void resetCapturedEvents() {
        capture.authenticated.clear();
    }

    @Test
    void shouldLoginAndUseSessionBoundTokenOnProtectedEndpoint() throws Exception {
        String email = registerUser();

        JsonNode login = parse(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Aman Minz"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.authorities[0]").value("USER"))
                .andReturn().getResponse().getContentAsString());

        UUID identityId = UUID.fromString(login.get("identityId").asText());
        String token = login.get("token").asText();

        assertThat(identitySessionRepository.findByIdentityIdAndRevokedFalse(identityId))
                .hasSize(1)
                .allSatisfy(session -> {
                    assertThat(session.getLastAuthenticatedAt()).isNotNull();
                    assertThat(session.getLastSeenAt()).isNotNull();
                });

        assertThat(capture.authenticated)
                .singleElement()
                .satisfies(event -> assertThat(event.identity().identityId()).isEqualTo(identityId));

        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value(identityId.toString()))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.identityType").value("REGISTERED"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void shouldRevokePreviousSessionWhenLoggingInAgain() throws Exception {
        String email = registerUser();
        String firstToken = login(email);
        String secondToken = login(email);

        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("identity.session.revoked"));

        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isOk());

        UUID identityId = userProfileRepository.findByEmail(email).orElseThrow().getIdentityId();
        assertThat(identitySessionRepository.findByIdentityIdAndRevokedFalse(identityId)).hasSize(1);
    }

    @Test
    void shouldRejectAllAuthenticationFailuresIdentically() throws Exception {
        String email = registerUser();

        JsonNode wrongPassword = parse(failLogin(loginJson(email, "WrongPassword@123")));
        JsonNode unknownEmail = parse(failLogin(loginJson("nobody-" + email, "StrongPassword@123")));

        Identity identity = identityRepository
                .findById(userProfileRepository.findByEmail(email).orElseThrow().getIdentityId())
                .orElseThrow();
        identity.disable();
        identityRepository.save(identity);
        JsonNode disabledIdentity = parse(failLogin(loginJson(email)));

        for (JsonNode failure : List.of(wrongPassword, unknownEmail, disabledIdentity)) {
            assertThat(failure.get("code").asText()).isEqualTo("identity.credentials.invalid");
            assertThat(failure.get("message").asText()).isEqualTo("Invalid email or password");
        }
    }

    @Test
    void shouldRejectProtectedEndpointWithoutOrWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthorized"));

        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("identity.token.invalid"));
    }

    private String registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Aman Minz",
                                  "email": "%s",
                                  "password": "StrongPassword@123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return parse(body).get("token").asText();
    }

    private String failLogin(String requestBody) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    private String loginJson(String email) {
        return loginJson(email, "StrongPassword@123");
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private JsonNode parse(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
