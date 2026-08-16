package io.quizchef.session.domain;

import io.quizchef.common.persistence.AuditableEntity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.session.domain.exception.DuplicateParticipantException;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.ParticipantAlreadyJoinedException;
import io.quizchef.session.domain.exception.SessionFullException;
import io.quizchef.session.domain.exception.QuestionRemovalNotAllowedException;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
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
     * Whether the host has released final standings to participants. Set
     * false whenever the session finishes and flipped true only by
     * {@link #releaseFinalResults()} — the one gate between "quiz complete"
     * and a participant learning their final rank, so the host's winner
     * ceremony always runs before anyone sees where they placed.
     */
    @Column(name = "final_results_released", nullable = false)
    private boolean finalResultsReleased;

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

    /**
     * The order this session plays its questions in, when it differs from
     * the quiz's own.
     *
     * <p>Empty means "play the authored order", which is what every session
     * does unless the host shuffles it. Published quizzes are immutable and
     * sessions pin the version they execute, so the same quiz replayed runs
     * the same sequence — correct for the content, wrong for the event. A
     * group playing a quiz a second time should not get the questions in
     * the order they already remember, and the way to give them a different
     * one without editing published content is for the order to belong to
     * the session.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "session_question_order", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "position")
    @Column(name = "question_id", nullable = false)
    private List<UUID> questionOrder = new ArrayList<>();

    /**
     * The questions the host pulled out of this session, and the record of
     * pulling them.
     *
     * <p>Empty for every session that runs cleanly. A removed question stays
     * listed rather than being erased: everything that reads the session
     * skips it (see {@link #isRemoved}), but the audit entry outlives the
     * game.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "session_removed_questions", joinColumns = @JoinColumn(name = "session_id"))
    private List<RemovedQuestion> removedQuestions = new ArrayList<>();

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

    /**
     * Points a roster entry at a new key, keeping the participant's place.
     *
     * <p>Only ever used to rotate a guest's resume token during host-assisted
     * recovery. The roster mirrors the token's digest to enforce "one guest
     * token per session", so a rotation that changed only the Participant
     * would leave the session holding a key that no longer identifies
     * anyone — the participant would still be listed and still be
     * unreachable.
     *
     * <p>Join order is preserved deliberately: recovering a player must not
     * quietly move them to the back of the room.
     */
    public void rekeyParticipant(UUID participantId, ParticipantKey newKey) {
        Objects.requireNonNull(participantId, "participantId must not be null");
        Objects.requireNonNull(newKey, "newKey must not be null");
        SessionRosterEntry existing = roster.stream()
                .filter(entry -> entry.participantId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "participant %s is not in this session".formatted(participantId)));
        boolean takenBySomeoneElse = roster.stream()
                .anyMatch(entry -> !entry.participantId().equals(participantId)
                        && entry.key().equals(newKey));
        if (takenBySomeoneElse) {
            throw new ParticipantAlreadyJoinedException();
        }
        roster.remove(existing);
        roster.add(new SessionRosterEntry(participantId, newKey, existing.joinOrder()));
    }

    public void start() {
        requireState(SessionState.LOBBY, "start");
        if (roster.isEmpty()) {
            throw new SessionNotStartableException("A session needs at least one participant to start");
        }
        this.state = SessionState.IN_PROGRESS;
    }

    /**
     * Makes a question current and starts its reading period. Allowed at the
     * start of play (no phase yet) and between questions (from LEADERBOARD).
     * The question is visible but not yet answerable — {@link
     * #acceptsAnswersFor} stays false until {@link #openQuestion(QuestionTimer)}
     * follows. The server owns the timer (ADR-006) — the caller supplies one
     * built from the server clock.
     */
    public void previewQuestion(UUID questionId, QuestionTimer previewTimer) {
        requireState(SessionState.IN_PROGRESS, "preview a question in");
        if (currentPhase != null && currentPhase != SessionPhase.LEADERBOARD) {
            throw new InvalidSessionTransitionException(state, "preview a question during " + currentPhase);
        }
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(previewTimer, "previewTimer must not be null");
        this.currentQuestionId = questionId;
        this.currentQuestionTimer = previewTimer;
        this.currentPhase = SessionPhase.QUESTION_PREVIEW;
    }

    /**
     * Ends the reading period and opens the current question for answering.
     * The question stays the one {@link #previewQuestion} set — this only
     * replaces the timer (the preview clock is done; the answer clock
     * starts now) and flips the phase. The server owns the timer (ADR-006).
     */
    public void openQuestion(QuestionTimer answerTimer) {
        requirePhase(SessionPhase.QUESTION_PREVIEW, "open the question");
        Objects.requireNonNull(answerTimer, "answerTimer must not be null");
        this.currentQuestionTimer = answerTimer;
        this.currentPhase = SessionPhase.QUESTION_OPEN;
    }

    /**
     * Closes the current question to further answers.
     */
    public void closeQuestion() {
        requirePhase(SessionPhase.QUESTION_OPEN, "close the question");
        this.currentPhase = SessionPhase.QUESTION_CLOSED;
    }

    public void revealAnswer() {
        requirePhase(SessionPhase.QUESTION_CLOSED, "reveal the answer");
        this.currentPhase = SessionPhase.ANSWER_REVEALED;
    }

    public void showLeaderboard() {
        requirePhase(SessionPhase.ANSWER_REVEALED, "show the leaderboard");
        this.currentPhase = SessionPhase.LEADERBOARD;
    }

    /**
     * True while the given question is open for answers — the one gate answer
     * acceptance passes through (ADR-006).
     */
    public boolean acceptsAnswersFor(UUID questionId) {
        return state == SessionState.IN_PROGRESS
                && currentPhase == SessionPhase.QUESTION_OPEN
                && questionId.equals(currentQuestionId);
    }

    public void finish() {
        requireState(SessionState.IN_PROGRESS, "finish");
        this.state = SessionState.FINISHED;
        this.currentPhase = null;
        this.currentQuestionId = null;
        this.currentQuestionTimer = null;
        this.finalResultsReleased = false;
    }

    /**
     * Fixes the order this session will play its questions in.
     *
     * <p>Only before the first question opens: the sequence a game is part
     * way through is not something to renumber underneath the people
     * playing it, and a participant who has answered question two would
     * find question two arriving again. {@code CREATED} and {@code LOBBY}
     * are both fine — a host may shuffle while the room fills — as is a
     * started session that has not opened anything yet.
     *
     * <p>The caller supplies the order; the aggregate only checks that it
     * is exactly the quiz's questions, once each. Which order is a decision
     * for the application service, not the model, but "this is a permutation
     * of the right set" is an invariant and belongs here.
     */
    public void useQuestionOrder(List<UUID> orderedQuestionIds, Set<UUID> quizQuestionIds) {
        if (currentQuestionId != null) {
            throw new InvalidSessionTransitionException(state,
                    "change the question order once a question has been played");
        }
        if (state != SessionState.CREATED && state != SessionState.LOBBY
                && state != SessionState.IN_PROGRESS) {
            throw new InvalidSessionTransitionException(state, "change the question order in");
        }
        Objects.requireNonNull(orderedQuestionIds, "orderedQuestionIds must not be null");
        Set<UUID> provided = new HashSet<>(orderedQuestionIds);
        if (provided.size() != orderedQuestionIds.size() || !provided.equals(quizQuestionIds)) {
            throw new IllegalArgumentException(
                    "orderedQuestionIds must contain exactly the quiz's questions, each once");
        }
        this.questionOrder = new ArrayList<>(orderedQuestionIds);
    }

    /**
     * This session's own question order, or empty when it plays the quiz's.
     */
    public List<UUID> questionOrder() {
        return List.copyOf(questionOrder);
    }

    /**
     * Pulls a question out of this session for good.
     *
     * <p>Session-scoped by construction: the published quiz keeps the
     * question, another session of the same quiz still asks it, and only
     * this session's effective sequence loses it. Everything downstream —
     * numbering, progression, scoring, history — reads through {@link
     * #isRemoved} and so never sees it again, which is what keeps
     * "Question 3 of 10 / Question 5 of 10" from ever reaching a player.
     *
     * <p>Refuses the last one. A session with no questions left cannot
     * produce standings for a game nobody played, and the safe outcome for
     * a host who has already removed everything else is to be told no
     * rather than handed an empty result. Idempotent for the caller's
     * benefit: removing an already-removed question is a no-op, so a
     * double-click converges instead of throwing.
     *
     * <p>The caller supplies the questions currently in play, exactly as
     * {@link #useQuestionOrder} does — which questions a quiz has is not
     * something the session knows, but "this one is real and it is not the
     * last" is an invariant and belongs here.
     *
     * @return true if this call performed the removal, false if it was
     *         already removed
     */
    public boolean removeQuestion(UUID questionId, Set<UUID> effectiveQuestionIds,
                                  SessionPhase removedFromPhase, int cancelledAnswerCount,
                                  Instant at) {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(effectiveQuestionIds, "effectiveQuestionIds must not be null");
        if (isRemoved(questionId)) {
            return false;
        }
        if (!effectiveQuestionIds.contains(questionId)) {
            throw new IllegalArgumentException(
                    "question %s is not part of this session's sequence".formatted(questionId));
        }
        if (effectiveQuestionIds.size() <= 1) {
            throw new QuestionRemovalNotAllowedException(
                    "This is the session's last remaining question — removing it would leave "
                            + "nothing to play");
        }
        removedQuestions.add(
                new RemovedQuestion(questionId, at, removedFromPhase, cancelledAnswerCount));
        return true;
    }

    /**
     * Abandons the attempt at the current question, leaving the session
     * between questions.
     *
     * <p>Clears the phase and the running clock but keeps {@code
     * currentQuestionId}, which is what makes both recoveries work: the
     * engine still knows where in the sequence it stands (so it can find
     * what comes next), and a null phase is one {@link #previewQuestion}
     * already accepts — so a corrected question can re-enter its reading
     * period without a special case, and a removed one can be stepped past.
     *
     * <p>Cancelling the attempt is only half the reversal; the answers and
     * points it produced belong to the participants and are reversed there.
     */
    public void cancelCurrentQuestion() {
        requireState(SessionState.IN_PROGRESS, "cancel the current question of");
        if (currentPhase == null) {
            return;
        }
        this.currentPhase = null;
        this.currentQuestionTimer = null;
    }

    /** The questions pulled out of this session, in the order they were pulled. */
    public List<RemovedQuestion> removedQuestions() {
        return List.copyOf(removedQuestions);
    }

    public Set<UUID> removedQuestionIds() {
        return removedQuestions.stream()
                .map(RemovedQuestion::questionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isRemoved(UUID questionId) {
        return removedQuestions.stream()
                .anyMatch(removed -> removed.questionId().equals(questionId));
    }

    public void archive() {
        requireState(SessionState.FINISHED, "archive");
        this.state = SessionState.ARCHIVED;
    }

    /**
     * Releases final standings to participants — the host's explicit "reveal
     * results" command, issued once the winner ceremony has run. Requires
     * the session to have finished; idempotent once released, so a
     * duplicate host click or a concurrent retry is harmless rather than a
     * conflict.
     */
    public void releaseFinalResults() {
        if (finalResultsReleased) {
            return;
        }
        requireState(SessionState.FINISHED, "release final results for");
        this.finalResultsReleased = true;
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

    private void requirePhase(SessionPhase expected, String action) {
        if (state != SessionState.IN_PROGRESS || currentPhase != expected) {
            throw new InvalidSessionTransitionException(state,
                    action + " (phase " + currentPhase + ")");
        }
    }
}
