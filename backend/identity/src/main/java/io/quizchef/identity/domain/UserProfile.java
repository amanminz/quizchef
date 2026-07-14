package io.quizchef.identity.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The public and contact profile of a registered identity.
 *
 * <p>Email is the primary login identifier and is stored normalized
 * (trimmed, lower case) so uniqueness is case-insensitive. Phone number is
 * optional and reserved for future login methods.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends AuditableEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID identityId;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(length = 32)
    private String phoneNumber;

    @Column(length = 2048)
    private String avatarUrl;

    private UserProfile(UUID id, UUID identityId, String displayName, String email) {
        super(id);
        this.identityId = Objects.requireNonNull(identityId, "identityId must not be null");
        this.displayName = requireDisplayName(displayName);
        this.email = normalizeEmail(email);
    }

    public static UserProfile create(UUID identityId, String displayName, String email) {
        return new UserProfile(UUID.randomUUID(), identityId, displayName, email);
    }

    public void rename(String displayName) {
        this.displayName = requireDisplayName(displayName);
    }

    public void changeEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public void changePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void changeAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return displayName.strip();
    }

    /**
     * The single domain rule for email normalization: trimmed, lower case.
     * Uniqueness checks and persistence must always use this form.
     */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
