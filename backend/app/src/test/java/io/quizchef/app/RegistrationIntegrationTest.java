package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.identity.domain.Credentials;
import io.quizchef.identity.domain.UserProfile;
import io.quizchef.identity.domain.event.IdentityRegisteredEvent;
import io.quizchef.identity.infrastructure.persistence.CredentialsRepository;
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
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end registration flow against a real PostgreSQL: HTTP contract,
 * security whitelist, persistence, hashing, and event publication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration(proxyBeanMethods = false)
    static class EventCapture {

        final List<IdentityRegisteredEvent> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(IdentityRegisteredEvent event) {
            received.add(event);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @Autowired
    private IdentitySessionRepository identitySessionRepository;

    @Autowired
    private EventCapture eventCapture;

    @BeforeEach
    void resetCapturedEvents() {
        eventCapture.received.clear();
    }

    @Test
    void shouldRegisterIdentityAndReturnLocationHeader() throws Exception {
        String email = uniqueEmail();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Aman Minz",
                                  "email": "%s",
                                  "password": "StrongPassword@123",
                                  "phoneNumber": "+919999999999"
                                }
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identityId").isNotEmpty())
                .andExpect(jsonPath("$.displayName").value("Aman Minz"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID identityId = UUID.fromString(body.get("identityId").asText());
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/identities/" + identityId);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("StrongPassword@123");

        UserProfile profile = userProfileRepository.findByEmail(email).orElseThrow();
        assertThat(profile.getIdentityId()).isEqualTo(identityId);

        Credentials credentials = credentialsRepository.findByIdentityId(identityId).orElseThrow();
        assertThat(credentials.getPasswordHash()).startsWith("$argon2id$");
        assertThat(credentials.getPasswordHash()).doesNotContain("StrongPassword@123");

        assertThat(identitySessionRepository.findByIdentityIdAndRevokedFalse(identityId))
                .as("registration must not create a login session")
                .isEmpty();

        assertThat(eventCapture.received)
                .singleElement()
                .satisfies(event -> assertThat(event.identity().identityId()).isEqualTo(identityId));
    }

    @Test
    void shouldRejectDuplicateEmailCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("identity.email.duplicate"));
    }

    @Test
    void shouldRejectInvalidPayloadWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "A",
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'displayName')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(email)))
                .andExpect(status().isCreated());
    }

    private String registrationJson(String email) {
        return """
                {
                  "displayName": "Aman Minz",
                  "email": "%s",
                  "password": "StrongPassword@123"
                }
                """.formatted(email);
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
