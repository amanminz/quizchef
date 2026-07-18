package io.quizchef.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The realtime endpoint's cross-origin handshake. Spring's WebSocket and
 * SockJS handshakes enforce same-origin by default — which silently broke
 * the split-domain production topology (RFC-008: frontend and backend on
 * separate domains) while local same-origin dev kept working. The endpoint
 * now honors the same allowlist as REST CORS ({@code CorsProperties}); the
 * SockJS info probe exercises the real origin check without needing a
 * WebSocket upgrade inside MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RealtimeHandshakeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void handshakeFromAnAllowedCrossOriginFrontendIsAccepted() throws Exception {
        mockMvc.perform(get("/ws/info")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk());
    }

    @Test
    void handshakeFromAnUnlistedOriginIsRefused() throws Exception {
        mockMvc.perform(get("/ws/info")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handshakeWithoutAnOriginHeaderStillWorks() throws Exception {
        // Non-browser clients and same-origin requests send no Origin.
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk());
    }
}
