package io.quizchef.security.infrastructure;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Adapter from Spring Security's request context to the framework-independent
 * {@link CurrentUser} abstraction.
 *
 * <p>This is the only place where business-facing identity information is
 * read from SecurityContextHolder. Anything other than a verified
 * {@link IdentityPrincipal} — including Spring's anonymous authentication —
 * maps to {@link CurrentUser#anonymous()}.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return CurrentUser.anonymous();
        }
        return CurrentUser.authenticated(
                principal.identityId(),
                principal.identityType(),
                principal.roles());
    }
}
