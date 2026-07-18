package io.quizchef.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The real end-to-end 429 path (Phase 3 PR #3 / RFC-011), against the
 * {@code login} bucket's actual configured policy (5/minute/IP). Rate
 * limiting is off by default in the {@code test} profile (see
 * {@code application-test.yml}) because integration tests share one cached
 * Spring context — and this bean's in-memory buckets — across many test
 * methods and classes; the property override below deliberately gives this
 * one test class its own, isolated context so it can prove the feature
 * works without risking every other test's shared bucket state.
 */
@SpringBootTest(properties = "quizchef.security.rate-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RateLimitingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exceedingTheLoginBucketReturnsA429WithRetryAfter() throws Exception {
        String body = """
                {"email": "nobody@example.com", "password": "irrelevant"}
                """;

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(header().string("X-RateLimit-Limit", "5"));

        for (int attempt = 2; attempt <= 5; attempt++) {
            int currentAttempt = attempt;
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(result -> {
                        int actualStatus = result.getResponse().getStatus();
                        if (actualStatus == 429) {
                            throw new AssertionError("Got rate-limited before exhausting the bucket, attempt "
                                    + currentAttempt);
                        }
                    });
        }

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.code").value("rate-limit.exceeded"));
    }
}
