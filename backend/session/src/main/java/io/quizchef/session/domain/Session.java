package io.quizchef.session.domain;

import io.quizchef.common.persistence.AuditableEntity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.session.domain.exception.DuplicateParticipantException;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.ParticipantAlreadyJoinedException;
import io.quizchef.session.domain.exception.SessionFullException;
import io.quizchef.session.domain.exception.SessionNotStartableException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A live run of a published quiz: its PIN, host, lifecycle, execution
 * settings, and the ordered roster of participants.
 *
 * <p><strong>Owns</strong> the roster (ordering, membership, and the
 * uniqueness of each participant's identity/guest token within the session),
 * the lifecycle, and the current-question/phase/timer pointers.
 * <strong>Does not own</strong> quiz content (it references a published
 * quiz version by id — the exact immutable content it executes, never "the
 * latest quiz"), question ordering, or a Participant's mutable state — those
 * are the {@link Participant} aggregate. Transport is entirely absent
 * (ADR-004): a session never knows a connection exists.
 */
@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session extends AuditableEntity {

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "session_pin", nullable = false, length = 6))
    private SessionPin sessionPin;

    @Column(name = "published_quiz_version_id", nullable = false, updatable = false)
    private UUID publishedQuizVersionId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "identityId",
                    column = @Column(name = "host_identity_id", nullable = false, updatable = false)),
            @AttributeOverride(name = "identityType",
                    column = @Column(name = "host_identity_type", nullable = false, updatable = false, length = 20))
    })
    private IdentityReference hostIdentity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionState state;

    /**
     * The exact question currently in play — a content id, never a
     * positional index, so the session does not depend on quiz ordering.
     * Null until progression begins (a later PR).
     */
    @Column(name = "current_question_id")
    private UUID currentQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", length = 20)
    private SessionPhase currentPhase;

    @Embedded
    private SessionSettings sessionSettings;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "startedAt", column = @Column(name = "current_timer_started_at")),
            @AttributeOverride(name = "durationSeconds", column = @Column(name = "current_timer_duration_seconds")),
            @AttributeOverride(name = "endsAt", column = @Column(name = "current_timer_ends_at"))
    })
    private QuestionTimer currentQuestionTimer;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "session_participants", joinColumns = @JoinColumn(name = "session_id"))
    private List<SessionRosterEntry> roster = new ArrayList<>();

    private Session(UUID id, SessionPin sessionPin, UUID publishedQuizVersionId,
                    IdentityReference hostIdentity, SessionSettings sessionSettings) {
        super(id);
        this.sessionPin = Objects.requireNonNull(sessionPin, "sessionPin must not be null");
        this.publishedQuizVersionId =
                Objects.requireNonNull(publishedQuizVersionId, "publishedQuizVersionId must not be null");
        this.hostIdentity = Objects.requireNonNull(hostIdentity, "hostIdentity must not be null");
        this.sessionSettings = Objects.requireNonNull(sessionSettings, "sessionSettings must not be null");
        this.state = SessionState.CREATED;
    }

    /**
     * Creates a session in CREATED for the given published quiz version,
     * hosted by the given identity.
     */
    public static Session create(SessionPin sessionPin, UUID publishedQuizVersionId,
                                 IdentityReference hostIdentity, SessionSettings sessionSettings) {
        return new Session(UUID.randomUUID(), sessionPin, publishedQuizVersionId,
                hostIdentity, sessionSettings);
    }

    public void openLobby() {
        requireState(SessionState.CREATED, "open the lobby of");
        this.state = SessionState.LOBBY;
    }

    /**
     * Adds a participant to the roster. Allowed while the lobby is open, and
     * mid-session only when late join is enabled. Rejects a participant
     * already present, or an identity/guest token already in the session.
     */
    public void registerParticipant(UUID participantId, ParticipantKey key) {
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        requireJoinable();
        if (roster.size() >= sessionSettings.maxParticipants()) {
            throw new SessionFullException(sessionSettings.maxParticipants());
        }
        if (roster.stream().anyMatch(entry -> entry.participantId().equals(participantId))) {
            throw new DuplicateParticipantException(participantId);
        }
        if (roster.stream().anyMatch(entry -> entry.key().equals(key))) {
            throw new ParticipantAlreadyJoinedException();
        }
        roster.add(new SessionRosterEntry(participantId, key, nextJoinOrder()));
    }

    public void start() {
        requireState(SessionState.LOBBY, "start");
        if (roster.isEmpty()) {
            throw new SessionNotStartableException("A session needs at least one participant to start");
        }
        this.state = SessionState.IN_PROGRESS;
    }

    public void finish() {
        requireState(SessionState.IN_PROGRESS, "finish");
        this.state = SessionState.FINISHED;
    }

    public void archive() {
        requireState(SessionState.FINISHED, "archive");
        this.state = SessionState.ARCHIVED;
    }

    /**
     * The roster in join order.
     */
    public List<SessionRosterEntry> roster() {
        return roster.stream()
                .sorted(Comparator.comparingInt(SessionRosterEntry::joinOrder))
                .toList();
    }

    public int participantCount() {
        return roster.size();
    }

    public boolean hasParticipant(UUID participantId) {
        return roster.stream().anyMatch(entry -> entry.participantId().equals(participantId));
    }

    public boolean isInLobby() {
        return state == SessionState.LOBBY;
    }

    public boolean isInProgress() {
        return state == SessionState.IN_PROGRESS;
    }

    public boolean isFinished() {
        return state == SessionState.FINISHED;
    }

    public boolean isArchived() {
        return state == SessionState.ARCHIVED;
    }

    private int nextJoinOrder() {
        return roster.stream().mapToInt(SessionRosterEntry::joinOrder).max().orElse(0) + 1;
    }

    private void requireJoinable() {
        boolean joinable = state == SessionState.LOBBY
                || (state == SessionState.IN_PROGRESS && sessionSettings.allowLateJoin());
        if (!joinable) {
            throw new InvalidSessionTransitionException(state, "add a participant to");
        }
    }

    private void requireState(SessionState expected, String action) {
        if (state != expected) {
            throw new InvalidSessionTransitionException(state, action);
        }
    }
}
