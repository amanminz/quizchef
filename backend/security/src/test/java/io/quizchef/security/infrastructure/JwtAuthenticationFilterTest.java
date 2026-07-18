package io.quizchef.security.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quizchef.common.correlation.CorrelationKeys;
import io.quizchef.identity.application.IdentitySessionQueryService;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.infrastructure.jwt.IdentityToken;
import io.quizchef.identity.infrastructure.jwt.InvalidTokenException;
import io.quizchef.identity.infrastructure.jwt.JwtTokenValidator;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final ObjectMapper OBJECT_MAPPER =
            JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Mock
    private JwtTokenValidator tokenValidator;

    @Mock
    private IdentitySessionQueryService sessionQueryService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void createFilter() {
        filter = new JwtAuthenticationFilter(tokenValidator, sessionQueryService, OBJECT_MAPPER);
        request = new MockHttpServletRequest("GET", "/api/v1/anything");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void shouldPassThroughAnonymouslyWithoutBearerToken() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).as("chain must continue").isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldAuthenticateValidTokenWithActiveSession() throws Exception {
        UUID identityId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer valid-token");
        when(tokenValidator.validate("valid-token")).thenReturn(new IdentityToken(
                identityId, sessionId, IdentityType.REGISTERED, Set.of(Role.USER),
                Instant.parse("2026-07-14T11:00:00Z")));
        when(sessionQueryService.activeSessionRoles(sessionId, identityId))
                .thenReturn(Optional.of(Set.of(Role.USER)));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal())
                .isEqualTo(new IdentityPrincipal(identityId, IdentityType.REGISTERED, Set.of(Role.USER)));
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(MDC.get(CorrelationKeys.IDENTITY_ID_MDC_KEY)).isEqualTo(identityId.toString());
    }

    @Test
    void shouldAuthorizeFromPersistedRolesNotTheTokenClaim() throws Exception {
        // The token was issued before a promotion: its claim says USER, the
        // durable roles now include QUIZ_MASTER — persisted roles win.
        UUID identityId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer pre-promotion-token");
        when(tokenValidator.validate("pre-promotion-token")).thenReturn(new IdentityToken(
                identityId, sessionId, IdentityType.REGISTERED, Set.of(Role.USER),
                Instant.parse("2026-07-14T11:00:00Z")));
        when(sessionQueryService.activeSessionRoles(sessionId, identityId))
                .thenReturn(Optional.of(Set.of(Role.USER, Role.QUIZ_MASTER)));

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(new IdentityPrincipal(
                identityId, IdentityType.REGISTERED, Set.of(Role.USER, Role.QUIZ_MASTER)));
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_QUIZ_MASTER");
    }

    @Test
    void shouldRejectInvalidTokenWith401() throws Exception {
        request.addHeader("Authorization", "Bearer broken-token");
        when(tokenValidator.validate("broken-token")).thenThrow(InvalidTokenException.malformed());

        try (LogCapture capture = new LogCapture(JwtAuthenticationFilter.class)) {
            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).as("chain must stop").isNull();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("identity.token.invalid");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(capture.messages()).anySatisfy(message -> {
                assertThat(message).contains("security.invalid_jwt");
                assertThat(message).contains("identity.token.invalid");
            });
        }
    }

    @Test
    void shouldRejectTokenOfRevokedSessionWith401() throws Exception {
        UUID identityId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer stale-token");
        when(tokenValidator.validate("stale-token")).thenReturn(new IdentityToken(
                identityId, sessionId, IdentityType.REGISTERED, Set.of(Role.USER),
                Instant.parse("2026-07-14T11:00:00Z")));
        when(sessionQueryService.activeSessionRoles(sessionId, identityId))
                .thenReturn(Optional.empty());

        try (LogCapture capture = new LogCapture(JwtAuthenticationFilter.class)) {
            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).isNull();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("identity.session.revoked");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(capture.messages()).anyMatch(message -> message.contains("security.invalid_jwt"));
        }
    }
}
