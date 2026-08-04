package io.quizchef.session.application;

import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.session.domain.FinalPlacementPolicy;
import io.quizchef.session.domain.FinalPlacementVisibility;
import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.domain.exception.ResultsNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of a participant's own finish — the single participant-facing
 * source of final ranking, and the only place a participant device can
 * learn anything about where they came.
 *
 * <p>Open like {@link SessionResultsQueryService#personalResult}: the
 * unguessable session and participant ids gate it, the same trust answer
 * submission already places in the participant id. Held until the host
 * releases results, exactly like the personal result — the winner
 * ceremony runs first, always.
 *
 * <p>The reveal group ({@link FinalPlacementPolicy}) gets their exact
 * position and a label. Everyone else gets their score and the names
 * either side of them, and no exact rank exists on their response to be
 * rendered by accident. That split is decided here, over the ranking
 * service's own output, so the projector and the phones cannot disagree
 * about where the line falls.
 */
@Service
public class ParticipantFinalPlacementQueryService {

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final LeaderboardService leaderboardService;
    private final GameplayQuizQuery gameplayQuizQuery;

    public ParticipantFinalPlacementQueryService(SessionRepository sessionRepository,
                                                 ParticipantRepository participantRepository,
                                                 LeaderboardService leaderboardService,
                                                 GameplayQuizQuery gameplayQuizQuery) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.leaderboardService = leaderboardService;
        this.gameplayQuizQuery = gameplayQuizQuery;
    }

    @Transactional(readOnly = true)
    public ParticipantFinalPlacementView placement(UUID sessionId, UUID participantId) {
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        boolean finished = session.getState() == SessionState.FINISHED
                || session.getState() == SessionState.ARCHIVED;
        if (!finished || !session.isFinalResultsReleased()) {
            throw new ResultsNotAvailableException();
        }

        List<LeaderboardEntry> ranked = leaderboardService.rank(
                participantRepository.findBySessionId(sessionId), session.roster());
        int ownIndex = indexOf(ranked, participantId);
        LeaderboardEntry own = ranked.get(ownIndex);
        int revealCount = FinalPlacementPolicy.exactRankRevealCount(ranked);
        int totalQuestions = gameplayQuizQuery.load(session.getPublishedQuizVersionId())
                .questions().size();

        if (FinalPlacementPolicy.revealsExactRank(own.rank(), revealCount)) {
            return new ParticipantFinalPlacementView(
                    sessionId,
                    participantId,
                    own.displayName(),
                    FinalPlacementVisibility.EXACT_RANK,
                    own.rank(),
                    own.score(),
                    FinalPlacementPolicy.labelFor(own.rank()),
                    totalQuestions,
                    session.participantCount(),
                    null, null, null);
        }

        // Outside the reveal group. The neighbours are whoever the ranking
        // put immediately either side — by name only, and the participant's
        // own rank is simply not on this response.
        LeaderboardEntry justAbove = ownIndex > 0 ? ranked.get(ownIndex - 1) : null;
        LeaderboardEntry justBelow = ownIndex < ranked.size() - 1 ? ranked.get(ownIndex + 1) : null;

        // "Tied" means the ranking service itself assigned an equal rank —
        // never merely an equal score, which it breaks by submission time
        // and join order. Saying "you finished ahead of X" when the game's
        // own ranking calls them equal would be a small lie in the one
        // direction this feature exists to avoid.
        ParticipantFinalPlacementView.Neighbour tiedWith = null;
        ParticipantFinalPlacementView.Neighbour aheadOf = null;
        ParticipantFinalPlacementView.Neighbour behind = null;

        if (justAbove != null) {
            if (justAbove.rank() == own.rank()) {
                tiedWith = neighbour(justAbove);
            } else {
                behind = neighbour(justAbove);
            }
        }
        if (justBelow != null) {
            if (justBelow.rank() == own.rank()) {
                if (tiedWith == null) {
                    tiedWith = neighbour(justBelow);
                }
            } else {
                aheadOf = neighbour(justBelow);
            }
        }

        return new ParticipantFinalPlacementView(
                sessionId,
                participantId,
                own.displayName(),
                FinalPlacementVisibility.RELATIVE_ONLY,
                null,
                own.score(),
                null,
                totalQuestions,
                session.participantCount(),
                aheadOf,
                behind,
                tiedWith);
    }

    private static ParticipantFinalPlacementView.Neighbour neighbour(LeaderboardEntry entry) {
        return new ParticipantFinalPlacementView.Neighbour(entry.displayName());
    }

    private static int indexOf(List<LeaderboardEntry> ranked, UUID participantId) {
        for (int index = 0; index < ranked.size(); index++) {
            if (ranked.get(index).participantId().equals(participantId)) {
                return index;
            }
        }
        throw new ParticipantNotFoundException();
    }
}
