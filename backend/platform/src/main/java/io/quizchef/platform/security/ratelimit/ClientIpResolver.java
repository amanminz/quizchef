package io.quizchef.platform.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller's IP for anonymous (unauthenticated) rate limiting.
 *
 * <p>Deliberately just {@code request.getRemoteAddr()} — no header parsing
 * here. Trusting a client-supplied {@code X-Forwarded-For} would let a
 * caller spoof its way around IP-based limits, so that translation is
 * handled once, upstream, by Tomcat's {@code RemoteIpValve}
 * ({@code server.forward-headers-strategy: native}), enabled only in the
 * {@code prod} profile where a real reverse proxy sits in front of this
 * service. Outside {@code prod} there is no such proxy, so
 * {@code getRemoteAddr()} is already the real caller.
 *
 * <p>The strategy matters more than it looks. {@code framework} (Spring's
 * {@code ForwardedHeaderFilter}) reads the <em>leftmost</em> entry, and a
 * proxy appends what it saw — so the leftmost value is whatever the client
 * chose to send. Measured against that configuration, a caller escaped its
 * own exhausted bucket by editing the prefix, and an unrelated client
 * sending the same prefix was refused on someone else's usage: every
 * IP-keyed limit was evadable and weaponizable at once.
 * {@code native} walks the chain from the right past the configured
 * proxies, so the address used is the one a trusted hop observed, which the
 * client cannot forge. See {@code TrustedClientIpIntegrationTest}.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
