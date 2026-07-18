package io.quizchef.identity.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A durable actor within QuizChef.
 *
 * <p>An Identity is who someone is — registered or guest, active or
 * disabled — and, since Phase 3, what platform {@link Role}s they durably
 * hold. It deliberately carries no credentials, contact details, tokens,
 * or transport information; those belong to other aggregates.
 *
 * <p>Roles are the aggregate's to guard: every registered identity holds
 * {@link Role#USER} from birth, grants are idempotent and additive, and
 * guests can never hold a role (they exist only to play).
 */
@Entity
@Table(name = "identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Identity extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private IdentityType identityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "identity_roles", joinColumns = @JoinColumn(name = "identity_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = new HashSet<>();

    private Identity(UUID id, IdentityType identityType) {
        super(id);
        this.identityType = identityType;
        this.status = IdentityStatus.ACTIVE;
        if (identityType == IdentityType.REGISTERED) {
            this.roles.add(Role.USER);
        }
    }

    public static Identity registered() {
        return new Identity(UUID.randomUUID(), IdentityType.REGISTERED);
    }

    public static Identity guest() {
        return new Identity(UUID.randomUUID(), IdentityType.GUEST);
    }

    /**
     * Grants a role, additively and idempotently. Returns whether anything
     * changed, so callers can skip persistence and events on a no-op.
     * Guests hold no roles, ever (RFC-002).
     */
    public boolean grantRole(Role role) {
        if (isGuest()) {
            throw new IllegalStateException("A guest identity cannot hold roles");
        }
        return roles.add(role);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** The durable roles this identity holds. */
    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    public void disable() {
        this.status = IdentityStatus.DISABLED;
    }

    public void enable() {
        this.status = IdentityStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == IdentityStatus.ACTIVE;
    }

    public boolean isGuest() {
        return identityType == IdentityType.GUEST;
    }

    public IdentityReference reference() {
        return new IdentityReference(getId(), identityType);
    }
}
