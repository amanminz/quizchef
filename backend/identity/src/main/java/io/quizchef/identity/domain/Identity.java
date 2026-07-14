package io.quizchef.identity.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A durable actor within QuizChef.
 *
 * <p>An Identity is only who someone is — registered or guest, active or
 * disabled. It deliberately carries no credentials, contact details, tokens,
 * or transport information; those belong to other aggregates.
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

    private Identity(UUID id, IdentityType identityType) {
        super(id);
        this.identityType = identityType;
        this.status = IdentityStatus.ACTIVE;
    }

    public static Identity registered() {
        return new Identity(UUID.randomUUID(), IdentityType.REGISTERED);
    }

    public static Identity guest() {
        return new Identity(UUID.randomUUID(), IdentityType.GUEST);
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
