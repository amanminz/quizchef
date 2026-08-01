package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.event.QuestionStartedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends a question's reading period and opens it for answers — the
 * server-timed, fully automatic counterpart to {@link CloseQuestionApplicationService}.
 * There is no host command for this transition (the spec is explicit: no
 * browser "open options" button); the scheduled preview-end callback is the
 * only trigger, so unlike close-vs-host-close there is no competing path to
 * race against — the guard below exists only to make a duplicate or
 * stale-after-the-fact firing a harmless no-op.
 *
 * <p>Deliberately does not itself arm the answer-close timer: doing so would
 * need a dependency on {@link QuestionTimerScheduler}, whose sole
 * implementation depends on this service to open on preview expiry — a
 * circular bean graph. Returning the answer window instead lets the
 * scheduler (which already owns both operations) chain them itself.
 */
@Service
public class OpenQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OpenQuestionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public OpenQuestionApplicationService(SessionRepository sessionRepository,
                                          DomainEventPublisher eventPublisher,
                                          Clock clock) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Opens the question for answers if — and only if — it is still in its
     * reading period on this exact question. A no-op otherwise (empty
     * result): the host cannot end the preview any other way, so
     * "otherwise" only means play has already moved on by the time this
     * fired, never a real race to settle (contrast {@link
     * CloseQuestionApplicationService#closeIfExpired}). Invoked by the
     * scheduler; no caller, so no authorization here — a system trigger,
     * not a user command.
     *
     * @return the answer window's close time, so the caller can arm the
     *         next timer — empty if this firing was a no-op
     */
    @Transactional
    public Optional<Instant> openIfPreviewExpired(UUID sessionId, UUID questionId, int answerDurationSeconds) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null
                || session.getCurrentPhase() != SessionPhase.QUESTION_PREVIEW
                || !questionId.equals(session.getCurrentQuestionId())) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        QuestionTimer answerTimer = QuestionTimer.startingAt(now, Duration.ofSeconds(answerDurationSeconds));
        session.openQuestion(answerTimer);
        sessionRepository.saveAndFlush(session);

        eventPublisher.publish(new QuestionStartedEvent(
                sessionId, questionId, answerTimer.endsAt(), answerTimer.durationSeconds(), now));
        log.info("Question {} in session {} opened for answers after its reading period", questionId, sessionId);
        return Optional.of(answerTimer.endsAt());
    }
}
