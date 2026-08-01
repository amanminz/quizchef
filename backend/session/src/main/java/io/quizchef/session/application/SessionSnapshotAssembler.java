package io.quizchef.session.application;

import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the reconnection snapshot from the live aggregates — the server's
 * authoritative view of where a returning participant left off. Time
 * remaining comes from the shared {@link Clock} (ADR-006).
 *
 * <p>{@code leaderboard} is always empty. It carried the full ranked roster
 * (every participant's name, score, and rank) unconditionally until this was
 * caught as a live-event privacy leak: a participant reconnecting at any
 * point in the game — including before the host's final-results release —
 * received standings no participant-facing read is supposed to expose (see
 * {@link SessionResultsQueryService#personalResult}). The frontend never
 * read the field, so nothing downstream needed it. The field itself stays
 * (a breaking API shape change is out of scope for this fix) but is never
 * populated — the same "notification, not a data source" pattern already
 * used for {@code LeaderboardUpdatedEvent}'s broadcast payload.
 */
@Component
public class SessionSnapshotAssembler {

    private final Clock clock;

    public SessionSnapshotAssembler(Clock clock) {
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SessionSnapshotView assemble(Session session, Participant participant) {
        return new SessionSnapshotView(
                session.getId(),
                participant.getId(),
                session.getState().name(),
                session.getCurrentPhase() == null ? null : session.getCurrentPhase().name(),
                session.getCurrentQuestionId(),
                remainingMillis(session),
                participant.getTotalScore(),
                submittedOptionIds(session, participant),
                List.of());
    }

    private long remainingMillis(Session session) {
        boolean countingDown = (session.getCurrentPhase() == SessionPhase.QUESTION_OPEN
                || session.getCurrentPhase() == SessionPhase.QUESTION_PREVIEW)
                && session.getCurrentQuestionTimer() != null;
        if (!countingDown) {
            return 0L;
        }
        long remaining = Duration.between(clock.instant(),
                session.getCurrentQuestionTimer().endsAt()).toMillis();
        return Math.max(0L, remaining);
    }

    private static Set<UUID> submittedOptionIds(Session session, Participant participant) {
        if (session.getCurrentQuestionId() == null) {
            return Set.of();
        }
        return participant.answers().stream()
                .filter(answer -> answer.questionId().equals(session.getCurrentQuestionId()))
                .findFirst()
                .map(ParticipantAnswer::selectedOptionIds)
                .orElse(Set.of());
    }
}
