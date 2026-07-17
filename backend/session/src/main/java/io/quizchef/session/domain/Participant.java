package io.quizchef.session.domain;

import io.quizchef.common.persistence.AuditableEntity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.exception.InvalidParticipantTransitionException;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A durable player in a session (ADR-003). It survives disconnects,
 * refreshes, and device switches: a disconnect marks it disconnected, never
 * deletes it, and reconnection restores its score and answers.
 *
 * <p>Identified by exactly one mechanism — a registered {@link
 * IdentityReference} or a guest {@link GuestParticipantToken}, never a
 * display name or a connection. It owns its own state, answers, and cached
 * score; the session owns only the roster reference to it.
 *
 * <p>Connectivity is not a stored flag — it is derived from {@code state}
 * ({@link #isConnected()}), so it can never drift from the lifecycle.
 */
@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant extends AuditableEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "identityId",
                    column = @Column(name = "identity_id", updatable = false)),
            @AttributeOverride(name = "identityType",
                    column = @Column(name = "identity_type", updatable = false, length = 20))
    })
    private IdentityReference identityReference;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "guest_token", updatable = false))
    private GuestParticipantToken guestParticipantToken;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "preferred_language", nullable = false, length = 20))
    private LanguageCode preferredLanguage;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantState state;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "participant_answers", joinColumns = @JoinColumn(name = "participant_id"))
    private List<ParticipantAnswer> answers = new ArrayList<>();

    private Participant(UUID id, UUID sessionId, IdentityReference identityReference,
                        GuestParticipantToken guestParticipantToken, String displayName,
                        LanguageCode preferredLanguage) {
        super(id);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        boolean hasIdentity = identityReference != null;
        boolean hasGuest = guestParticipantToken != null;
        if (hasIdentity == hasGuest) {
            throw new IllegalArgumentException(
                    "a participant must have exactly one of an identity reference or a guest token");
        }
        this.identityReference = identityReference;
        this.guestParticipantToken = guestParticipantToken;
        this.displayName = requireDisplayName(displayName);
        this.preferredLanguage =
                Objects.requireNonNull(preferredLanguage, "preferredLanguage must not be null");
        this.totalScore = 0;
        this.state = ParticipantState.JOINED;
    }

    /**
     * A registered participant, backed by an identity.
     */
    public static Participant registered(UUID sessionId, IdentityReference identityReference,
                                         String displayName, LanguageCode preferredLanguage) {
        return new Participant(UUID.randomUUID(), sessionId, identityReference, null,
                displayName, preferredLanguage);
    }

    /**
     * A guest participant, backed by a reconnection token.
     */
    public static Participant guest(UUID sessionId, GuestParticipantToken guestParticipantToken,
                                    String displayName, LanguageCode preferredLanguage) {
        return new Participant(UUID.randomUUID(), sessionId, null, guestParticipantToken,
                displayName, preferredLanguage);
    }

    /**
     * The key by which the session recognizes this participant.
     */
    public ParticipantKey key() {
        return isGuest()
                ? ParticipantKey.forGuest(guestParticipantToken)
                : ParticipantKey.forIdentity(identityReference);
    }

    public boolean isGuest() {
        return guestParticipantToken != null;
    }

    /**
     * Connects or reconnects, refreshing {@code lastSeenAt}. Idempotent while
     * the participant is live: allowed from JOINED (first connect),
     * DISCONNECTED (reconnect), or an already CONNECTED state (a refresh or
     * second device the server never observed dropping). A reconnect keeps
     * score and answers. Only a FINISHED participant is rejected.
     */
    public void connect(Instant at) {
        if (state == ParticipantState.FINISHED) {
            throw new InvalidParticipantTransitionException(state, "connect");
        }
        this.state = ParticipantState.CONNECTED;
        this.lastSeenAt = Objects.requireNonNull(at, "at must not be null");
    }

    public void disconnect(Instant at) {
        if (state != ParticipantState.CONNECTED) {
            throw new InvalidParticipantTransitionException(state, "disconnect");
        }
        this.state = ParticipantState.DISCONNECTED;
        this.lastSeenAt = Objects.requireNonNull(at, "at must not be null");
    }

    public void finish() {
        if (state == ParticipantState.FINISHED) {
            throw new InvalidParticipantTransitionException(state, "finish");
        }
        this.state = ParticipantState.FINISHED;
    }

    /**
     * Records one answer and keeps {@code totalScore} as their cached sum
     * (ADR-003). Model only — it does not compute points (they arrive on the
     * answer) and there is no submission flow here.
     */
    public void recordAnswer(ParticipantAnswer answer) {
        Objects.requireNonNull(answer, "answer must not be null");
        if (answers.stream().anyMatch(existing -> existing.questionId().equals(answer.questionId()))) {
            throw new IllegalArgumentException(
                    "question %s is already answered".formatted(answer.questionId()));
        }
        answers.add(answer);
        this.totalScore += answer.pointsAwarded();
    }

    /**
     * Derived from the lifecycle — never a stored flag.
     */
    public boolean isConnected() {
        return state == ParticipantState.CONNECTED;
    }

    public List<ParticipantAnswer> answers() {
        return List.copyOf(answers);
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return displayName.strip();
    }
}
