package io.quizchef.platform.security.configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Checks, in {@code prod} only, that the trusted-proxy pattern actually
 * draws a boundary — the same "secure by default" posture
 * {@link JwtSecretSafetyCheck} takes with the signing secret.
 *
 * <p>Rate limiting keys on the address a trusted hop observed rather than
 * one the caller supplied, and {@code server.tomcat.remoteip.internal-proxies}
 * is what decides which hops are trusted. Tomcat takes a <em>Java regular
 * expression</em>, not CIDR notation, so a plausible-looking value can be
 * far broader than intended — and a pattern that trusts everyone restores
 * exactly the bypass this configuration exists to close: whoever is trusted
 * as a proxy is believed about who the client is.
 *
 * <p>Two outcomes, deliberately different:
 *
 * <ul>
 *   <li><strong>Too broad — refuse to start.</strong> A pattern matching
 *       arbitrary unrelated public addresses is a catch-all whatever it
 *       looks like, and running with it is worse than not running.</li>
 *   <li><strong>Absent or narrow — start, and warn.</strong> If no peer
 *       matches, Tomcat ignores the forwarded chain entirely and groups
 *       traffic by the direct proxy address. That degrades rate limiting to
 *       one shared bucket, which is a real problem, but it fails closed —
 *       nobody gains a bypass — so availability wins and the operator gets
 *       a loud line to act on instead of an outage.</li>
 * </ul>
 */
@Component
@Profile("prod")
public class TrustedProxySafetyCheck {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxySafetyCheck.class);

    /**
     * Unrelated public addresses, from three different registries and none
     * of them adjacent. A pattern matching all of these is not describing a
     * proxy — it is describing "anyone". Testing behaviour rather than
     * string-matching `.*` catches the equivalents (`.+`, `^.*$`, `[\s\S]*`,
     * `(?s).*`) without trying to enumerate them, and still permits a
     * legitimately public proxy range, which matches its own addresses but
     * not these.
     */
    private static final List<String> UNRELATED_PUBLIC_ADDRESSES = List.of(
            "203.0.113.7", "8.8.8.8", "198.51.100.42", "1.1.1.1", "93.184.216.34");

    private final String internalProxies;

    public TrustedProxySafetyCheck(
            @Value("${server.tomcat.remoteip.internal-proxies:}") String internalProxies) {
        this.internalProxies = internalProxies;
    }

    @PostConstruct
    void verifyTheProxyBoundaryIsRealOrWarnLoudly() {
        if (internalProxies == null || internalProxies.isBlank()) {
            log.warn("security.trusted_proxy_absent: server.tomcat.remoteip.internal-proxies is not set, "
                    + "so no forwarded chain is trusted and every caller is rate-limited as the proxy "
                    + "itself — one shared bucket. Limits are not bypassable, but they are not "
                    + "per-client either. Set TRUSTED_PROXY_REGEX to the address the proxy reaches "
                    + "this service from.");
            return;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(internalProxies);
        } catch (PatternSyntaxException invalid) {
            // Tomcat would reject this later and less clearly; a request
            // never gets far enough for the operator to see why.
            throw new IllegalStateException(
                    "TRUSTED_PROXY_REGEX is not a valid Java regular expression — note that Tomcat "
                            + "takes a regex here, not CIDR notation", invalid);
        }

        boolean matchesAnything = UNRELATED_PUBLIC_ADDRESSES.stream()
                .allMatch(address -> pattern.matcher(address).matches());
        if (matchesAnything) {
            throw new IllegalStateException(
                    "TRUSTED_PROXY_REGEX matches arbitrary public addresses, so every caller would be "
                            + "trusted to declare its own client address — the header-spoofing bypass "
                            + "this setting exists to close. Refusing to start in prod. Set it to the "
                            + "addresses the proxy actually reaches this service from.");
        }

        if (internalProxies.contains("/")) {
            // "10.0.0.0/8" compiles happily and matches nothing, which fails
            // closed and therefore silently: no error, no bypass, just one
            // shared bucket. The slash is the tell.
            log.warn("security.trusted_proxy_suspicious: TRUSTED_PROXY_REGEX contains '/', which looks "
                    + "like CIDR notation. Tomcat takes a Java regular expression here — a CIDR string "
                    + "compiles but matches no address, leaving every caller keyed on the proxy.");
        }

        log.info("security.trusted_proxy_configured: forwarded chains are trusted only from peers "
                + "matching the configured pattern");
    }
}
