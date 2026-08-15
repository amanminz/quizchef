package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.CorrectedOptionText;
import io.quizchef.session.domain.CorrectedPrompt;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionQuestionCorrection;
import io.quizchef.session.domain.event.QuestionCorrectedEvent;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.domain.exception.NoCurrentQuestionException;
import io.quizchef.session.infrastructure.persistence.SessionQuestionCorrectionRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixes a question while the session is running. Host only.
 *
 * <p>The correction is session-scoped and the published question is never
 * touched — see {@link SessionQuestionCorrection} for why that is the only
 * safe place to put it. Everything downstream reads the corrected copy
 * through {@link SessionQuizQuery}, so the fixed wording and the fixed
 * answer key take effect everywhere at once: what participants see, what
 * scoring compares against, what the reveal shows.
 *
 * <p>Whether the correction also <em>replays</em> the question is the
 * server's decision, not a flag the host sends:
 *
 * <ul>
 *   <li>An upcoming question is simply corrected. It has not been shown, so
 *       there is nothing to undo — it arrives fixed when the game reaches
 *       it.</li>
 *   <li>The question in play is corrected <em>and</em> restarted. Its
 *       attempt is cancelled, every answer and point it produced is
 *       reversed, and the fixed question re-enters its reading period from
 *       the top. There is no third option worth offering: a question whose
 *       wording or answer key just changed cannot go on being scored
 *       against answers given to the old one.</li>
 * </ul>
 *
 * <p>Correcting an <em>already played</em> question is refused. Its answers
 * are scored and its standings shown; quietly rescoring a question the room
 * has moved past would change a leaderboard they already saw. Removing it
 * is the honest recovery there, and that is what
 * {@link RemoveQuestionApplicationService} is for.
 *
 * <p>All of it commits together, under the session's write lock: the
 * correction, the reversal, and the restart are one atomic change (section
 * 4), and an answer cannot slip in beside them.
 */
@Service
public class CorrectQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CorrectQuestionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final SessionQuestionCorrectionRepository correctionRepository;
    private final SessionQuizQuery sessionQuizQuery;
    private final AuthorizationService authorizationService;
    private final CancelQuestionAttempt cancelQuestionAttempt;
    private final QuestionOpener questionOpener;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public CorrectQuestionApplicationService(SessionRepository sessionRepository,
                                             SessionQuestionCorrectionRepository correctionRepository,
                                             SessionQuizQuery sessionQuizQuery,
                                             AuthorizationService authorizationService,
                                             CancelQuestionAttempt cancelQuestionAttempt,
                                             QuestionOpener questionOpener,
                                             DomainEventPublisher eventPublisher,
                                             Clock clock) {
        this.sessionRepository = sessionRepository;
        this.correctionRepository = correctionRepository;
        this.sessionQuizQuery = sessionQuizQuery;
        this.authorizationService = authorizationService;
        this.cancelQuestionAttempt = cancelQuestionAttempt;
        this.questionOpener = questionOpener;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSummaryView correct(CurrentUser currentUser, CorrectQuestionCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byIdForUpdate(sessionRepository, command.sessionId());
        SessionHostPolicy.requireHost(currentUser, session);

        UUID questionId = command.questionId();
        // A finished session is history. Correcting a question in it would
        // rewrite what an event that already happened actually asked, which
        // is the whole reason corrections are session-scoped in the first
        // place. Before the game starts is fine: nothing has been played.
        if (session.isFinished() || session.isArchived()) {
            throw new InvalidSessionTransitionException(session.getState(), "correct a question in");
        }
        if (session.isRemoved(questionId)) {
            throw new InvalidSessionTransitionException(session.getState(),
                    "correct a question already removed from");
        }
        PlayableQuizView quiz = sessionQuizQuery.effectiveQuiz(session);
        PlayableQuestion question = quiz.questions().stream()
                .filter(candidate -> candidate.questionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new NoCurrentQuestionException(session.getState()));

        boolean inPlay = questionId.equals(session.getCurrentQuestionId());
        if (!inPlay && hasBeenPlayed(quiz, session, questionId)) {
            throw new InvalidSessionTransitionException(session.getState(),
                    "correct a question already played in");
        }

        saveCorrection(session, command, question.allOptionIds());

        if (inPlay) {
            // Order matters: reverse first, then rewind the session, then
            // re-preview. Reversing while the question is still current
            // means nothing can be answered into the gap — the row lock
            // holds, and by the time the phase reopens the answers are gone.
            int cancelled = cancelQuestionAttempt.cancel(session.getId(), questionId);
            session.cancelCurrentQuestion();
            questionOpener.startPreview(session, question, quiz.questionTimeLimitSeconds());
            log.info("Corrected and replayed question {} in session {}, cancelling {} answers",
                    questionId, session.getId(), cancelled);
        } else {
            log.info("Corrected upcoming question {} in session {}", questionId, session.getId());
        }
        sessionRepository.saveAndFlush(session);

        eventPublisher.publish(
                new QuestionCorrectedEvent(session.getId(), questionId, inPlay, clock.instant()));
        return SessionSummaryView.of(session);
    }

    /**
     * Saved before anything is undone, so a rejected correction (a bad
     * option id, a blank prompt) fails before the room's answers are
     * touched rather than after.
     */
    private void saveCorrection(Session session, CorrectQuestionCommand command,
                                Set<UUID> authoredOptionIds) {
        List<CorrectedPrompt> prompts = new ArrayList<>();
        List<CorrectedOptionText> optionTexts = new ArrayList<>();
        for (CorrectQuestionCommand.CorrectedLocalization localization : command.localizations()) {
            LanguageCode language = LanguageCode.of(localization.languageCode());
            prompts.add(new CorrectedPrompt(language, localization.prompt()));
            for (CorrectQuestionCommand.CorrectedOption option : localization.options()) {
                optionTexts.add(new CorrectedOptionText(language, option.optionId(), option.text()));
            }
        }
        SessionQuestionCorrection correction = correctionRepository
                .findBySessionIdAndQuestionId(session.getId(), command.questionId())
                .orElse(null);
        if (correction == null) {
            correction = SessionQuestionCorrection.first(session.getId(), command.questionId(),
                    command.correctOptionIds(), prompts, optionTexts, authoredOptionIds);
        } else {
            correction.reviseTo(command.correctOptionIds(), prompts, optionTexts, authoredOptionIds);
        }
        correctionRepository.saveAndFlush(correction);
    }

    /**
     * A question sits before the one in play, so the room has already
     * answered it and seen where it left them.
     *
     * <p>Read positionally rather than from a "played" flag: the session's
     * effective sequence is the one authority on order, and asking it where
     * two questions sit relative to each other cannot drift from what the
     * host is looking at.
     */
    private static boolean hasBeenPlayed(PlayableQuizView quiz, Session session, UUID questionId) {
        UUID currentQuestionId = session.getCurrentQuestionId();
        if (currentQuestionId == null) {
            return false;
        }
        int position = QuestionProgression.numberOf(quiz, session, questionId);
        int current = QuestionProgression.numberOf(quiz, session, currentQuestionId);
        return position > 0 && current > 0 && position < current;
    }
}
