package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.domain.event.IdentityAuthorizedEvent;
import io.quizchef.identity.infrastructure.jwt.JwtTokenGenerator;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end authorization: the /users/me contract, permission derivation,
 * 401 vs 403 semantics, and event publication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AccessControlIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration(proxyBeanMethods = false)
    static class EventCapture {

        final List<IdentityAuthorizedEvent> authorized = new CopyOnWriteArrayList<>();

        @EventListener
        void on(IdentityAuthorizedEvent event) {
            authorized.add(event);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private IdentitySessionRepository identitySessionRepository;

    @Autowired
    private JwtTokenGenerator tokenGenerator;

    @Autowired
    private EventCapture eventCapture;

    @BeforeEach
    void resetCapturedEvents() {
        eventCapture.authorized.clear();
    }

    @Test
    void shouldReturnCurrentUserWithDerivedPermissions() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").isNotEmpty())
                .andExpect(jsonPath("$.identityType").value("REGISTERED"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.permissions").value(
                        org.hamcrest.Matchers.containsInAnyOrder(
                                "QUIZ_VIEW", "USER_PROFILE_READ", "USER_PROFILE_UPDATE")));

        assertThat(eventCapture.authorized)
                .singleElement()
                .satisfies(event ->
                        assertThat(event.permission()).isEqualTo(Permission.USER_PROFILE_READ));
    }

    @Test
    void shouldRejectMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthorized"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("identity.token.invalid"));

        assertThat(eventCapture.authorized).isEmpty();
    }

    @Test
    void shouldForbidAuthenticatedIdentityWithoutPermission() throws Exception {
        // Roles are durable now: a registered identity always holds USER, so
        // the genuinely role-less authenticated caller is a guest — guests
        // hold no roles, ever (RFC-002). A stripped-down *claim* would no
        // longer matter either way: authorization reads persisted roles.
        Identity guest = identityRepository.save(Identity.guest());
        IdentitySession session = identitySessionRepository.save(
                IdentitySession.start(guest.getId(), "JUnit", "127.0.0.1", null));
        String guestToken = tokenGenerator.generate(
                guest.getId(), session.getId(), IdentityType.GUEST, Set.of()).token();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.permission.denied"));

        assertThat(eventCapture.authorized)
                .as("denied authorization must not publish an event")
                .isEmpty();
    }

    @Test
    void shouldAuthorizeFromPersistedRolesNotTokenClaims() throws Exception {
        // A token minted with an inflated QUIZ_MASTER claim authorizes only
        // what the identity durably holds — the claim is not the authority.
        Identity identity = identityRepository.save(Identity.registered());
        IdentitySession session = identitySessionRepository.save(
                IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null));
        String inflatedToken = tokenGenerator.generate(
                identity.getId(), session.getId(), IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER)).token();

        mockMvc.perform(post("/api/v1/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + inflatedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "defaultLanguage": "en",
                                  "localization": {"languageCode": "en", "title": "Nope"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.permission.denied"));
    }

    private String registerAndLogin() throws Exception {
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

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPassword@123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
