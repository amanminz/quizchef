package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Whose address a rate-limit bucket is keyed on, when there is a proxy in
 * front.
 *
 * <p>Runs over a real HTTP connection rather than MockMvc, because the
 * behaviour under test belongs to Tomcat's {@code RemoteIpValve} — MockMvc
 * never starts a container, so it cannot see this either way.
 *
 * <p>The header a proxy hands the application looks like
 * {@code "<whatever the client sent>, <real client IP>"}: the proxy appends
 * the peer it actually saw, so only the leftmost part is attacker-controlled.
 * Spring's {@code framework} strategy took the leftmost entry, which made
 * every IP-keyed limit both evadable (edit your prefix, get a fresh bucket)
 * and weaponizable (send someone else's address, spend their budget). The
 * login bucket is used here because at five per minute it is small enough to
 * exhaust in a test, but the same key serves the participant limits.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "quizchef.security.rate-limit.enabled=true",
                // What application-prod.yml configures. The peer here is
                // loopback, which the default internal-proxies regex counts
                // as a proxy — the same position a platform's edge proxy
                // occupies in production.
                "server.forward-headers-strategy=native"
        })
@ActiveProfiles("test")
@Testcontainers
class TrustedClientIpIntegrationTest {

    private static final String REAL_CLIENT = "198.51.100.5";
    private static final String LOGIN_BODY = """
            {"email": "nobody@example.com", "password": "irrelevant"}
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void aCallerCannotMoveItsOwnBucketByEditingTheForwardedHeader() {
        // Spend the whole budget as one client behind a proxy, sending its
        // own junk in front of what the proxy appends.
        HttpStatus exhausted = spendUntilRefused("203.0.113.10, " + REAL_CLIENT);
        assertThat(exhausted).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Same client, same proxy, a different injected prefix. Under the
        // leftmost reading this was admitted; the trusted hop is the
        // rightmost entry, which the client cannot forge.
        assertThat(login("9.9.9.9, " + REAL_CLIENT).getStatusCode())
                .as("editing the client-supplied prefix must not issue a fresh bucket")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // And with no header at all — the client is still the same peer.
        assertThat(login(null).getStatusCode())
                .as("dropping the header must not issue a fresh bucket either")
                .isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void oneCallerCannotSpendAnotherCallersBudget() {
        // The other half of the leftmost bug: a caller could name someone
        // else's address and exhaust it for them. Spend one client's budget…
        assertThat(spendUntilRefused("203.0.113.10, " + REAL_CLIENT))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // …and a genuinely different client, arriving through the same proxy
        // with the same forged prefix, still has its own.
        assertThat(login("203.0.113.10, 203.0.113.99").getStatusCode())
                .as("a different real client must not inherit someone else's exhausted bucket")
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Sends logins until refused, or gives up. The bucket refills while the
     * loop runs, so this fires until it sees the refusal rather than a fixed
     * number of times.
     */
    private HttpStatus spendUntilRefused(String forwardedFor) {
        for (int attempt = 0; attempt < 30; attempt++) {
            ResponseEntity<String> response = login(forwardedFor);
            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return HttpStatus.TOO_MANY_REQUESTS;
            }
        }
        return HttpStatus.OK;
    }

    private ResponseEntity<String> login(String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (forwardedFor != null) {
            headers.add("X-Forwarded-For", forwardedFor);
        }
        return restTemplate.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(LOGIN_BODY, headers), String.class);
    }
}
