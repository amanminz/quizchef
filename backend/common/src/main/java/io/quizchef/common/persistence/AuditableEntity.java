package io.quizchef.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for every QuizChef entity: UUID primary key plus creation and
 * modification timestamps, as mandated by the coding standards.
 *
 * <p>Identifiers are assigned by the domain (never by the database) so
 * aggregates are fully formed before they are persisted.
 */
@MappedSuperclass
public abstract class AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AuditableEntity() {
    }

    protected AuditableEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void refreshUpdatedAt() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuditableEntity entity)) {
            return false;
        }
        return getClass() == entity.getClass() && Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), id);
    }
}
