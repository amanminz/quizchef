package io.quizchef.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
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
 *
 * <p>Every entity is optimistically locked: concurrent modifications lose
 * against the {@code version} column instead of silently overwriting each
 * other. Clients read the version, send it back with updates, and receive
 * 409 when someone else saved in between.
 */
@MappedSuperclass
public abstract class AuditableEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

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

    public long getVersion() {
        return version;
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
