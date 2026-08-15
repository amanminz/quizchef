package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.AnswerDistributionNotAvailableException;
import io.quizchef.session.domain.exception.NoCurrentQuestionException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the post-reveal answer distribution: how many participants
 * picked each option on the current question, plus how many gave no
 * answer at all. Host only, exactly like {@link AnswerProgressQueryService}
 * — participants see only their own submission, never the group's.
 *
 * <p>Revealed only from the moment the answer is revealed (ADR-006, the
 * same reveal-time gate {@link SessionResultsQueryService} enforces):
 * before that, per-option counts would leak who is right before the
 * reveal broadcast does.
 */
@Service
public class AnswerDistributionQueryService {

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final SessionQuizQuery sessionQuizQuery;
    private final AuthorizationService authorizationService;

    public AnswerDistributionQueryService(SessionRepository sessionRepository,
                                          ParticipantRepository participantRepository,
                                          SessionQuizQuery sessionQuizQuery,
                                          AuthorizationService authorizationService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.sessionQuizQuery = sessionQuizQuery;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public AnswerDistributionView distribution(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        UUID questionId = session.getCurrentQuestionId();
        if (session.getState() != SessionState.IN_PROGRESS || questionId == null) {
            throw new NoCurrentQuestionException(session.getState());
        }
        if (session.getCurrentPhase() != SessionPhase.ANSWER_REVEALED
                && session.getCurrentPhase() != SessionPhase.LEADERBOARD) {
            throw new AnswerDistributionNotAvailableException();
        }

        Set<UUID> optionIds = sessionQuizQuery.effectiveQuiz(session)
                .questions().stream()
                .filter(question -> question.questionId().equals(questionId))
                .findFirst()
                .map(PlayableQuestion::allOptionIds)
                .orElse(Set.of());

        Map<UUID, Integer> counts = new LinkedHashMap<>();
        optionIds.forEach(optionId -> counts.put(optionId, 0));

        List<Participant> participants = participantRepository.findBySessionId(sessionId);
        int answered = 0;
        int eligible = 0;
        for (Participant participant : participants) {
            ParticipantAnswer answer = participant.answers().stream()
                    .filter(a -> a.questionId().equals(questionId))
                    .findFirst()
                    .orElse(null);
            if (answer != null) {
                answered++;
                eligible++;
                for (UUID selected : answer.selectedOptionIds()) {
                    counts.merge(selected, 1, Integer::sum);
                }
            } else if (participant.isConnected()) {
                eligible++;
            }
        }

        int finalAnswered = answered;
        List<AnswerDistributionView.OptionCount> options = counts.entrySet().stream()
                .map(entry -> new AnswerDistributionView.OptionCount(
                        entry.getKey(), entry.getValue(), percentage(entry.getValue(), finalAnswered)))
                .toList();

        return new AnswerDistributionView(sessionId, questionId, answered, eligible, eligible - answered, options);
    }

    /**
     * Percentage of <em>accepted answers</em>, not eligible participants —
     * "60%" means 60% of those who actually answered picked this option,
     * the same framing every live-quiz product uses. For a single/true-false
     * question this makes {@code sum(options[].percentage) ≈ 100}; for a
     * multiple-answer question the sum may legitimately exceed 100, since
     * one participant can contribute to several options (see
     * {@link AnswerDistributionView}). Never divides by eligible or
     * no-answer counts — those would understate every option's share for
     * no product reason.
     */
    private static int percentage(int count, int answered) {
        return answered == 0 ? 0 : (int) Math.round(count * 100.0 / answered);
    }
}
