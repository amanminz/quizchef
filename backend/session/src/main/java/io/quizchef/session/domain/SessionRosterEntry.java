package io.quizchef.session.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * One participant's place in a session's roster: which participant, by what
 * key, in what join order. Lives inside the {@link Session} aggregate (the
 * session id is the collection key), because roster invariants — no
 * duplicate participant, one identity/token per session, unique join order,
 * at least one to start — can only be enforced by the session root.
 *
 * <p>It holds a participant <em>id</em> and an immutable {@link
 * ParticipantKey}, never the Participant aggregate: the two are separate
 * consistency boundaries (ADR-003). The Participant owns its own state,
 * answers, and score.
 */
@Embeddable
public record SessionRosterEntry(
        UUID participantId,
        @Embedded ParticipantKey key,
        int joinOrder
) {

    public SessionRosterEntry {
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (joinOrder < 1) {
            throw new IllegalArgumentException("joinOrder must be positive");
        }
    }
}
