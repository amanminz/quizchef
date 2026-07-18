package io.quizchef.platform.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller's IP for anonymous (unauthenticated) rate limiting.
 *
 * <p>Deliberately just {@code request.getRemoteAddr()} — no header parsing
 * here. Trusting a client-supplied {@code X-Forwarded-For} would let a
 * caller spoof its way around IP-based limits, so that translation is
 * handled once, upstream, by Spring Boot's own {@code ForwardedHeaderFilter}
 * ({@code server.forward-headers-strategy: framework}), enabled only in the
 * {@code prod} profile where a real reverse proxy (Railway) sits in front
 * of this service and is trusted to set that header correctly. Outside
 * {@code prod} there is no such proxy, so {@code getRemoteAddr()} is
 * already the real caller.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
