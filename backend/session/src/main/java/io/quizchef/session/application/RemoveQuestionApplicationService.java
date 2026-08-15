package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.event.QuestionRemovedEvent;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.NoCurrentQuestionException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pulls a question out of a running session. Host only.
 *
 * <p>The recovery of last resort for a question that cannot be fixed: it
 * leaves the session's sequence, its answers are cancelled, its points are
 * reversed, and the game continues from the next question. The published
 * quiz keeps it — another session will still ask it.
 *
 * <p>Removing the question <em>in play</em> also ends its attempt. Whether
 * anyone had answered makes no difference to what happens here (it only
 * changes what the host is warned about before clicking): the answers go,
 * the timer goes with them, and the next question enters its reading
 * period. If there is no next question, the removal is what ends the quiz —
 * the standings are captured and the session finishes into the winner
 * ceremony, with no leaderboard for a question nobody completed.
 *
 * <p>Two things this deliberately does not do. It never reveals the removed
 * question's answer — the room never finished it, and showing the key would
 * tell them what they were about to be asked. And it never empties the
 * session: the aggregate refuses to remove the last question standing,
 * because a quiz with nothing left to play cannot produce a result worth
 * showing.
 */
@Service
public class RemoveQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RemoveQuestionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final SessionQuizQuery sessionQuizQuery;
    private final AuthorizationService authorizationService;
    private final CancelQuestionAttempt cancelQuestionAttempt;
    private final QuestionOpener questionOpener;
    private final SessionFinisher sessionFinisher;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public RemoveQuestionApplicationService(SessionRepository sessionRepository,
                                            SessionQuizQuery sessionQuizQuery,
                                            AuthorizationService authorizationService,
                                            CancelQuestionAttempt cancelQuestionAttempt,
                                            QuestionOpener questionOpener,
                                            SessionFinisher sessionFinisher,
                                            DomainEventPublisher eventPublisher,
                                            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.sessionQuizQuery = sessionQuizQuery;
        this.authorizationService = authorizationService;
        this.cancelQuestionAttempt = cancelQuestionAttempt;
        this.questionOpener = questionOpener;
        this.sessionFinisher = sessionFinisher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSummaryView remove(CurrentUser currentUser, UUID sessionId, UUID questionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byIdForUpdate(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        // A second click, or a retry of one that already won. Converge:
        // report the session as it now stands rather than throwing, and
        // never undo what the first call did.
        if (session.isRemoved(questionId)) {
            log.debug("Question {} was already removed from session {}", questionId, sessionId);
            return SessionSummaryView.of(session);
        }
        if (session.isFinished() || session.isArchived()) {
            throw new InvalidSessionTransitionException(session.getState(),
                    "remove a question from");
        }

        PlayableQuizView quiz = sessionQuizQuery.effectiveQuiz(session);
        Set<UUID> effectiveQuestionIds = quiz.questions().stream()
                .map(PlayableQuestion::questionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!effectiveQuestionIds.contains(questionId)) {
            throw new NoCurrentQuestionException(session.getState());
        }

        boolean inPlay = questionId.equals(session.getCurrentQuestionId());
        // Resolved *before* the removal, while the current question is still
        // findable in the sequence. Once it is gone, "what comes after it"
        // has no answer at all — and the wrong answer would be "nothing",
        // which would finish a session that has questions left.
        Optional<PlayableQuestion> next = inPlay
                ? QuestionProgression.nextAfter(quiz, session)
                : Optional.empty();
        SessionPhase removedFrom = inPlay ? session.getCurrentPhase() : null;

        int cancelled = inPlay ? cancelQuestionAttempt.cancel(sessionId, questionId) : 0;
        session.removeQuestion(questionId, effectiveQuestionIds, removedFrom, cancelled,
                clock.instant());

        if (inPlay) {
            session.cancelCurrentQuestion();
            if (next.isPresent()) {
                questionOpener.startPreview(session, next.get(), quiz.questionTimeLimitSeconds());
            } else {
                sessionFinisher.finish(session, "when its last remaining question was removed");
            }
        }
        sessionRepository.saveAndFlush(session);

        eventPublisher.publish(
                new QuestionRemovedEvent(sessionId, questionId, inPlay, clock.instant()));
        log.info("Removed question {} from session {} ({} answers cancelled, in play: {})",
                questionId, sessionId, cancelled, inPlay);
        return SessionSummaryView.of(session);
    }
}
