package io.quizchef.security.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.common.api.ApiError;
import io.quizchef.common.correlation.CorrelationKeys;
import io.quizchef.common.exception.UnauthorizedException;
import io.quizchef.identity.application.IdentitySessionQueryService;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.infrastructure.jwt.IdentityToken;
import io.quizchef.identity.infrastructure.jwt.InvalidTokenException;
import io.quizchef.identity.infrastructure.jwt.JwtTokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying a bearer token.
 *
 * <p>Two checks, by design (RFC-002): the JWT is validated cryptographically
 * (stateless), then its {@code sessionId} claim is checked against the durable
 * IdentitySession. A revoked session invalidates every token issued for it —
 * no blacklist, no token store. Requests without a bearer token pass through
 * anonymously; authorization decides what anonymous may reach.
 *
 * <p>The principal's roles come from that same session check — the
 * identity's <em>persisted</em> roles, not the token's issuance-time claim
 * — so a role granted mid-session (host onboarding) authorizes the very
 * next request without a new login. The claim remains accurate at issuance
 * and useful diagnostically; it is simply not the authority.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenValidator tokenValidator;
    private final IdentitySessionQueryService sessionQueryService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenValidator tokenValidator,
                                   IdentitySessionQueryService sessionQueryService,
                                   ObjectMapper objectMapper) {
        this.tokenValidator = tokenValidator;
        this.sessionQueryService = sessionQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            IdentityToken token = tokenValidator.validate(header.substring(BEARER_PREFIX.length()));
            Set<Role> roles = sessionQueryService
                    .activeSessionRoles(token.sessionId(), token.identityId())
                    .orElseThrow(InvalidTokenException::sessionRevoked);
            authenticate(token, roles);
        } catch (UnauthorizedException exception) {
            SecurityContextHolder.clearContext();
            reject(response, exception);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(IdentityToken token, Set<Role> roles) {
        IdentityPrincipal principal =
                new IdentityPrincipal(token.identityId(), token.identityType(), roles);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        // Every log line for the rest of this request can now be attributed
        // to an identity, without threading it through any method signature
        // (RFC-010). CorrelationIdFilter clears MDC at the end of the
        // request, so nothing here needs to remove it.
        MDC.put(CorrelationKeys.IDENTITY_ID_MDC_KEY, token.identityId().toString());
    }

    private void reject(HttpServletResponse response, UnauthorizedException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiError.of(exception.errorCode(), exception.getMessage()));
    }
}
