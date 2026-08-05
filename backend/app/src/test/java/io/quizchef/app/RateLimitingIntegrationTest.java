package io.quizchef.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.platform.security.ratelimit.RateLimitProperties;
import io.quizchef.platform.security.ratelimit.RateLimitRule;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

    @Autowired
    private RateLimitProperties rateLimitProperties;

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

    /**
     * The participant-facing limits are per client IP, and at a real venue
     * every phone shares one NAT address — so these are per-venue budgets,
     * and a room that cannot all join is a room that cannot play. Measured
     * before this was sized properly: a 40-device room got 10 joins, 10
     * reconnects, and 7 answers through.
     *
     * <p>Asserted against the configured policy rather than by firing 150
     * requests: the number in production is the thing that matters, and a
     * test that hammered it would be slow and would prove only that the
     * bucket arithmetic works — which the login test above already does.
     */
    @Test
    void participantRoutesAreSizedForAWholeVenueSharingOneAddress() {
        int smallestSupportedVenue = 120;

        RateLimitRule join = ruleFor("POST", "/api/v1/sessions/{pin}/join");
        assertThat(join.capacity()).isGreaterThanOrEqualTo(smallestSupportedVenue);
        assertThat(join.window()).isLessThanOrEqualTo(Duration.ofMinutes(1));

        // Every device reconnects on join, on refresh, and after any dropped
        // websocket — a wifi blip reconnects the whole room at once, so this
        // needs headroom above the room size rather than parity with it.
        RateLimitRule reconnect = ruleFor("POST", "/api/v1/sessions/reconnect");
        assertThat(reconnect.capacity()).isGreaterThanOrEqualTo(join.capacity() * 2);

        // A whole room answers within seconds of a question opening.
        RateLimitRule answers = ruleFor("POST", "/api/v1/sessions/{id}/answers");
        assertThat(answers.capacity()).isGreaterThanOrEqualTo(smallestSupportedVenue);
        assertThat(answers.window()).isLessThanOrEqualTo(Duration.ofSeconds(10));
    }

    /**
     * Raising the venue limits must not have loosened the ones that guard
     * accounts. These stay small on purpose: they are per person, not per
     * room, and nobody legitimately logs in ten times a minute.
     */
    @Test
    void authenticationAndRegistrationStayTight() {
        assertThat(ruleFor("POST", "/api/v1/auth/login").capacity()).isLessThanOrEqualTo(5);
        assertThat(ruleFor("POST", "/api/v1/auth/register").capacity()).isLessThanOrEqualTo(3);
        assertThat(ruleFor("POST", "/api/v1/users/me/host-access").capacity()).isLessThanOrEqualTo(3);
        assertThat(ruleFor("POST", "/api/v1/sessions").capacity()).isLessThanOrEqualTo(10);

        // And they are genuinely their own buckets, not the venue one.
        assertThat(ruleFor("POST", "/api/v1/auth/login").capacity())
                .isLessThan(ruleFor("POST", "/api/v1/sessions/{pin}/join").capacity());
    }

    /**
     * The venue ceiling is still a ceiling: past it, a caller is refused
     * with a code and a Retry-After it can act on rather than a bare 429.
     */
    @Test
    void exceedingTheJoinBucketIsStillRefusedWithAnActionableRetryAfter() throws Exception {
        String body = """
                {"displayName": "Flood", "preferredLanguage": "en"}
                """;
        int capacity = ruleFor("POST", "/api/v1/sessions/{pin}/join").capacity();

        // Fire until refused rather than exactly `capacity` times: the bucket
        // refills continuously (2.5 tokens a second at this size), so a loop
        // slower than the refill would never exhaust it. The ceiling is
        // proven by the refusal arriving, and by it not arriving early.
        //
        // No such session, so every attempt 404s — the bucket is consumed by
        // the attempt, not by the outcome, which is what makes this the guard
        // against PIN guessing.
        int attempts = 0;
        MvcResult refused = null;
        while (attempts < capacity * 3 && refused == null) {
            attempts++;
            MvcResult result = mockMvc.perform(post("/api/v1/sessions/000001/join")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
            if (result.getResponse().getStatus() == 429) {
                refused = result;
            }
        }

        assertThat(refused)
                .as("the venue budget is still a ceiling, not an open door")
                .isNotNull();
        assertThat(attempts)
                .as("a legitimate venue-sized burst must get through before anyone is refused")
                .isGreaterThanOrEqualTo(capacity);

        MockHttpServletResponse response = refused.getResponse();
        assertThat(response.getContentAsString()).contains("rate-limit.exceeded");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo(String.valueOf(capacity));
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(Integer.parseInt(response.getHeader("Retry-After")))
                .as("Retry-After should tell the caller when to come back, in seconds")
                .isPositive()
                .isLessThanOrEqualTo(60);
    }

    private RateLimitRule ruleFor(String method, String route) {
        return rateLimitProperties.rules().getOrDefault(method + " " + route,
                rateLimitProperties.defaultRule());
    }
}
