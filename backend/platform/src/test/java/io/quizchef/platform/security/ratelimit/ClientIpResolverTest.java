package io.quizchef.platform.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void resolvesTheDirectRemoteAddressWhenNoForwardingIsInPlay() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void ignoresAClientSuppliedForwardedHeaderUnlessTheFrameworkFilterIsActive() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "198.51.100.1");

        // No ForwardedHeaderFilter in play here — a client-supplied header
        // must not change what the resolver sees, or IP-based rate limits
        // would be trivially spoofable.
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void honorsXForwardedForOnceSpringBootsForwardedHeaderFilterIsActive() throws Exception {
        // This is the exact mechanism enabled only in the prod profile
        // (server.forward-headers-strategy: framework) behind a trusted
        // reverse proxy (Railway) — proving it actually rewrites
        // getRemoteAddr() the way ClientIpResolver's design assumes.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] resolvedInsideTheFilter = new String[1];
        new ForwardedHeaderFilter().doFilter(request, response,
                (req, res) -> resolvedInsideTheFilter[0] = resolver.resolve((HttpServletRequest) req));

        assertThat(resolvedInsideTheFilter[0]).isEqualTo("198.51.100.1");
    }
}
