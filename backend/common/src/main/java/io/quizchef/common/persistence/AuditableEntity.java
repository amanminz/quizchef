package io.quizchef.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Base class for every QuizChef entity: UUID primary key plus creation and
 * modification timestamps, as mandated by the coding standards.
 *
 * <p>Identifiers are assigned by the domain (never by the database) so
 * aggregates are fully formed before they are persisted. Because the id is
 * pre-assigned, {@link Persistable#isNew()} tells Spring Data to persist
 * (not merge) unsaved aggregates: without it every insert would run through
 * merge, firing lifecycle callbacks on an internal copy and costing an
 * extra SELECT.
 */
@MappedSuperclass
public abstract class AuditableEntity implements Persistable<UUID> {

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

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return createdAt == null;
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
