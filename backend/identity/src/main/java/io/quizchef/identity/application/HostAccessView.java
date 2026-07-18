package io.quizchef.identity.application;

import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import java.util.Set;

/**
 * The outcome of a host-access request: the (always-GRANTED, today)
 * status, plus the identity's resulting roles and derived permissions so
 * a client can reflect its new capabilities without another round trip.
 *
 * <p>{@code status} is an enum with room for PENDING and DENIED: the
 * product rule today is automatic self-service promotion (a self-hosted,
 * church-scale platform trusts its own registered users to author), but
 * an approval gate can be inserted later without changing this shape.
 */
public record HostAccessView(
        HostAccessStatus status,
        Set<Role> roles,
        Set<Permission> permissions
) {

    public enum HostAccessStatus {
        GRANTED,
        PENDING,
        DENIED
    }

    public HostAccessView {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
