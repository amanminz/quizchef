package io.quizchef.session.application;

import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the reconnection snapshot from the live aggregates — the server's
 * authoritative view of where a returning participant left off. Time
 * remaining comes from the shared {@link Clock} (ADR-006), and the
 * leaderboard is projected fresh, never read from storage.
 */
@Component
public class SessionSnapshotAssembler {

    private final ParticipantRepository participantRepository;
    private final LeaderboardService leaderboardService;
    private final Clock clock;

    public SessionSnapshotAssembler(ParticipantRepository participantRepository,
                                    LeaderboardService leaderboardService,
                                    Clock clock) {
        this.participantRepository = participantRepository;
        this.leaderboardService = leaderboardService;
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
                leaderboardService.rank(
                        participantRepository.findBySessionId(session.getId()), session.roster()));
    }

    private long remainingMillis(Session session) {
        if (session.getCurrentPhase() != SessionPhase.QUESTION_OPEN
                || session.getCurrentQuestionTimer() == null) {
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
