package io.quizchef.identity.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.common.exception.UnauthorizedException;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.RolePermissions;
import io.quizchef.identity.domain.event.IdentityAuthorizedEvent;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The single place authorization decisions are made.
 *
 * <p>Application services in every module ask this service; nobody checks
 * roles inline, and controllers never contain authorization logic. The
 * policy itself (role → permission) is the domain rule in
 * {@link RolePermissions}; this service evaluates it and publishes
 * {@link IdentityAuthorizedEvent} on success — denials publish nothing.
 *
 * <p>Framework-independent by design: no Spring Security, no web types —
 * only {@link CurrentUser} in, decision out.
 */
@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public AuthorizationService(DomainEventPublisher eventPublisher, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Grants or rejects: 401 semantics for anonymous callers, 403 for
     * authenticated callers lacking the permission.
     */
    public void authorize(CurrentUser currentUser, Permission permission) {
        if (!currentUser.authenticated()) {
            throw new UnauthorizedException();
        }
        if (!isGranted(currentUser, permission)) {
            log.info("Authorization denied: identity {} lacks {}", currentUser.identityId(), permission);
            throw new ForbiddenException(
                    "auth.permission.denied",
                    "Permission %s is not granted".formatted(permission));
        }
        eventPublisher.publish(new IdentityAuthorizedEvent(
                currentUser.reference(), permission, clock.instant()));
    }

    public boolean isGranted(CurrentUser currentUser, Permission permission) {
        return currentUser.authenticated() && currentUser.roles().stream()
                .anyMatch(role -> RolePermissions.permissionsOf(role).contains(permission));
    }

    /**
     * Union of the permissions of every role the user holds.
     */
    public Set<Permission> permissionsOf(CurrentUser currentUser) {
        if (!currentUser.authenticated()) {
            return Set.of();
        }
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        currentUser.roles().forEach(role -> permissions.addAll(RolePermissions.permissionsOf(role)));
        return Set.copyOf(permissions);
    }
}
