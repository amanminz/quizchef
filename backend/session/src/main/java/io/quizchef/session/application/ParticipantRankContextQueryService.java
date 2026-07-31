package io.quizchef.session.application;

import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.domain.exception.RankContextNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of per-question ranking neighbours: a participant's own rank
 * plus whoever is immediately ahead and behind — never the full
 * leaderboard (Live Event UX privacy split). Public like
 * {@link SessionResultsQueryService#personalResult}, gated by the same
 * unguessable session/participant ids.
 *
 * <p>Deliberately narrower than the results read: available only while a
 * non-final question's answer is showing ({@code ANSWER_REVEALED} or
 * {@code LEADERBOARD}), and never for the quiz's last question — that
 * question's standings are held for the host's winner ceremony
 * ({@link SessionResultsQueryService#personalResult}'s release gate), so
 * this endpoint refuses outright rather than leaking a preview of the
 * final order.
 */
@Service
public class ParticipantRankContextQueryService {

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final LeaderboardService leaderboardService;
    private final GameplayQuizQuery gameplayQuizQuery;

    public ParticipantRankContextQueryService(SessionRepository sessionRepository,
                                              ParticipantRepository participantRepository,
                                              LeaderboardService leaderboardService,
                                              GameplayQuizQuery gameplayQuizQuery) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.leaderboardService = leaderboardService;
        this.gameplayQuizQuery = gameplayQuizQuery;
    }

    @Transactional(readOnly = true)
    public ParticipantRankContextView rankContext(UUID sessionId, UUID participantId) {
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        if (!rankContextReadable(session)) {
            throw new RankContextNotAvailableException();
        }

        PlayableQuizView quiz = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        boolean isLastQuestion = QuestionProgression
                .nextAfter(quiz, session.getCurrentQuestionId())
                .isEmpty();
        if (isLastQuestion) {
            throw new RankContextNotAvailableException();
        }

        List<Participant> participants = participantRepository.findBySessionId(sessionId);
        List<LeaderboardEntry> entries = leaderboardService.rank(participants, session.roster());

        int ownIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).participantId().equals(participantId)) {
                ownIndex = i;
                break;
            }
        }
        if (ownIndex < 0) {
            throw new ParticipantNotFoundException();
        }
        LeaderboardEntry own = entries.get(ownIndex);

        int pointsEarned = participants.stream()
                .filter(participant -> participant.getId().equals(participantId))
                .findFirst()
                .flatMap(participant -> participant.answers().stream()
                        .filter(answer -> answer.questionId().equals(session.getCurrentQuestionId()))
                        .findFirst())
                .map(ParticipantAnswer::pointsAwarded)
                .orElse(0);

        LeaderboardEntry aheadCandidate = ownIndex > 0 ? entries.get(ownIndex - 1) : null;
        LeaderboardEntry behindCandidate = ownIndex < entries.size() - 1 ? entries.get(ownIndex + 1) : null;

        ParticipantRankContextView.TiedWith tiedWith = null;
        ParticipantRankContextView.Neighbour ahead = null;
        ParticipantRankContextView.Neighbour behind = null;

        if (aheadCandidate != null) {
            if (aheadCandidate.score() == own.score()) {
                tiedWith = new ParticipantRankContextView.TiedWith(aheadCandidate.displayName(), aheadCandidate.rank());
            } else {
                ahead = new ParticipantRankContextView.Neighbour(
                        aheadCandidate.displayName(), aheadCandidate.rank(), aheadCandidate.score() - own.score());
            }
        }
        if (behindCandidate != null) {
            if (behindCandidate.score() == own.score()) {
                if (tiedWith == null) {
                    tiedWith = new ParticipantRankContextView.TiedWith(
                            behindCandidate.displayName(), behindCandidate.rank());
                }
            } else {
                behind = new ParticipantRankContextView.Neighbour(
                        behindCandidate.displayName(), behindCandidate.rank(), own.score() - behindCandidate.score());
            }
        }

        return new ParticipantRankContextView(
                sessionId, participantId, own.displayName(), own.rank(), own.score(),
                pointsEarned, ahead, behind, tiedWith);
    }

    private static boolean rankContextReadable(Session session) {
        return session.getState() == SessionState.IN_PROGRESS
                && (session.getCurrentPhase() == SessionPhase.ANSWER_REVEALED
                        || session.getCurrentPhase() == SessionPhase.LEADERBOARD);
    }
}
