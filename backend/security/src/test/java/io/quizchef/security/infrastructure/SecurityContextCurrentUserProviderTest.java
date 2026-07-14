package io.quizchef.security.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextCurrentUserProviderTest {

    private final SecurityContextCurrentUserProvider provider = new SecurityContextCurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnAnonymousWhenNoAuthenticationExists() {
        CurrentUser user = provider.currentUser();

        assertThat(user.authenticated()).isFalse();
        assertThat(user.identityId()).isNull();
    }

    @Test
    void shouldMapIdentityPrincipalToCurrentUser() {
        UUID identityId = UUID.randomUUID();
        IdentityPrincipal principal = new IdentityPrincipal(
                identityId, IdentityType.REGISTERED, Set.of(Role.QUIZ_MASTER));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        CurrentUser user = provider.currentUser();

        assertThat(user.authenticated()).isTrue();
        assertThat(user.identityId()).isEqualTo(identityId);
        assertThat(user.identityType()).isEqualTo(IdentityType.REGISTERED);
        assertThat(user.hasRole(Role.QUIZ_MASTER)).isTrue();
    }

    @Test
    void shouldReturnAnonymousForForeignPrincipalTypes() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("some-user", null, List.of()));

        CurrentUser user = provider.currentUser();

        assertThat(user.authenticated()).isFalse();
    }
}
