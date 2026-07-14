package io.quizchef.identity.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The password credential of a registered identity.
 *
 * <p>Only ever stores a hash — hashing itself happens behind the
 * {@link PasswordHasher} port. Kept apart from {@link Identity} so that the
 * actor model never carries secrets.
 */
@Entity
@Table(name = "credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credentials extends AuditableEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID identityId;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    private Credentials(UUID id, UUID identityId, String passwordHash) {
        super(id);
        this.identityId = Objects.requireNonNull(identityId, "identityId must not be null");
        this.passwordHash = requireHash(passwordHash);
    }

    public static Credentials create(UUID identityId, String passwordHash) {
        return new Credentials(UUID.randomUUID(), identityId, passwordHash);
    }

    public void rotate(String newPasswordHash) {
        this.passwordHash = requireHash(newPasswordHash);
    }

    private static String requireHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        return passwordHash;
    }
}
