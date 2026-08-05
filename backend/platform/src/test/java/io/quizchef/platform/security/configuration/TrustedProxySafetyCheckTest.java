package io.quizchef.platform.security.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrustedProxySafetyCheckTest {

    /** What application-prod.yml ships. */
    private static final String PRIVATE_RANGES =
            "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|"
                    + "169\\.254\\.\\d{1,3}\\.\\d{1,3}|127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|"
                    + "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|"
                    + "172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}|0:0:0:0:0:0:0:1|::1";

    @ParameterizedTest(name = "refuses to start on ''{0}''")
    @ValueSource(strings = {
            ".*",
            ".+",
            "^.*$",
            "[\\s\\S]*",
            "(?s).*",
            // Not a literal catch-all, but trusts every IPv4 address, which
            // is the same thing said at greater length.
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
    })
    void refusesAPatternThatTrustsEveryone(String catchAll) {
        // Whoever is trusted as a proxy is believed about who the client is,
        // so a pattern matching anyone hands the bucket key back to the
        // caller — precisely the bypass this configuration closes.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new TrustedProxySafetyCheck(catchAll)
                        .verifyTheProxyBoundaryIsRealOrWarnLoudly())
                .withMessageContaining("arbitrary public addresses");
    }

    @Test
    void acceptsTheShippedPrivateRanges() {
        assertThatCode(() -> new TrustedProxySafetyCheck(PRIVATE_RANGES)
                .verifyTheProxyBoundaryIsRealOrWarnLoudly())
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsANarrowPublicProxyRange() {
        // A CDN or edge proxy has public addresses. Testing for "matches
        // anything" rather than "matches a public address" is what keeps
        // that legitimate, so this check does not force a wrong config.
        assertThatCode(() -> new TrustedProxySafetyCheck("104\\.16\\.\\d{1,3}\\.\\d{1,3}")
                .verifyTheProxyBoundaryIsRealOrWarnLoudly())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "starts with a warning when the pattern is ''{0}''")
    @ValueSource(strings = {"", "   "})
    void startsAndWarnsWhenNoProxyIsTrusted(String absent) {
        // Availability over strictness, because this direction fails closed:
        // no forwarded chain is trusted, everyone is keyed on the proxy, and
        // rate limiting degrades to one shared bucket without anyone gaining
        // a bypass.
        assertThatCode(() -> new TrustedProxySafetyCheck(absent)
                .verifyTheProxyBoundaryIsRealOrWarnLoudly())
                .doesNotThrowAnyException();
    }

    @Test
    void startsOnACidrLookingValueRatherThanRefusing() {
        // "10.0.0.0/8" is valid regex and matches nothing, so it fails
        // closed. Worth a warning, not a refusal — the operator can fix it
        // without the service being down while they do.
        assertThatCode(() -> new TrustedProxySafetyCheck("10.0.0.0/8")
                .verifyTheProxyBoundaryIsRealOrWarnLoudly())
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAPatternThatIsNotValidRegex() {
        // Tomcat takes a Java regex here, not CIDR — an operator reaching
        // for "10.0.0.0/8" should be told at startup, not by way of requests
        // quietly not being limited the way they expect.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new TrustedProxySafetyCheck("10.0.0.0/8[")
                        .verifyTheProxyBoundaryIsRealOrWarnLoudly())
                .withMessageContaining("not CIDR notation");
    }
}
