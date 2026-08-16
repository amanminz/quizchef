package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.ScoringPolicy;
import io.quizchef.session.domain.ScoringService;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.AnswerSubmittedEvent;
import io.quizchef.session.domain.exception.AnswerNotAcceptedException;
import io.quizchef.session.domain.exception.ParticipantNotConnectedException;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts a participant's answer — the one place the server takes input and
 * turns it into score, entirely on its own authority (ADR-006). It stamps the
 * response time from the server clock, decides correctness by comparing to
 * the question's correct set, computes the points, and records the answer
 * (updating the cached score, ADR-003). The acknowledgement carries no score.
 *
 * <p>Every guard the spec requires lives here or in the aggregate: the
 * participant must exist and be connected, the question must be open and the
 * one in play, it must not already be answered, and the options must be
 * valid. A host cannot answer — a host has no participant.
 */
@Service
public class SubmitAnswerApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SubmitAnswerApplicationService.class);

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final SessionQuizQuery sessionQuizQuery;
    private final ScoringService scoringService;
    private final ScoringPolicy scoringPolicy;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public SubmitAnswerApplicationService(SessionRepository sessionRepository,
                                          ParticipantRepository participantRepository,
                                          SessionQuizQuery sessionQuizQuery,
                                          ScoringService scoringService,
                                          ScoringPolicy scoringPolicy,
                                          DomainEventPublisher eventPublisher,
                                          Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.sessionQuizQuery = sessionQuizQuery;
        this.scoringService = scoringService;
        this.scoringPolicy = scoringPolicy;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public AnswerAcceptedView submit(SubmitAnswerCommand command) {
        // The session's write lock comes first, and the participant is read
        // only once it is held. A host correcting or removing this very
        // question reverses points across every participant under the same
        // lock, so the two cannot interleave: either this answer is taken
        // and then cancelled with the rest, or it arrives after the question
        // is already gone and is refused below. A participant loaded before
        // the lock would be a snapshot from before that decision.
        UUID sessionId = participantRepository.findSessionIdById(command.participantId())
                .orElseThrow(ParticipantNotFoundException::new);
        Session session = SessionLookup.byIdForUpdate(sessionRepository, sessionId);
        Participant participant = participantRepository.findById(command.participantId())
                .orElseThrow(ParticipantNotFoundException::new);

        if (!session.acceptsAnswersFor(command.questionId())) {
            throw new AnswerNotAcceptedException("The question is not open for answers");
        }
        if (!participant.isConnected()) {
            throw new ParticipantNotConnectedException();
        }
        if (alreadyAnswered(participant, command)) {
            throw new AnswerNotAcceptedException("This question is already answered");
        }
        if (command.selectedOptionIds().isEmpty()) {
            throw new IllegalArgumentException("an answer must select at least one option");
        }

        PlayableQuestion question = sessionQuizQuery.effectiveQuiz(session)
                .questions().stream()
                .filter(playable -> playable.questionId().equals(command.questionId()))
                .findFirst()
                .orElseThrow(() -> new AnswerNotAcceptedException("Unknown question"));
        if (!question.allOptionIds().containsAll(command.selectedOptionIds())) {
            throw new IllegalArgumentException("selected options are not valid for this question");
        }

        QuestionTimer timer = session.getCurrentQuestionTimer();
        Duration responseTime = elapsedSince(timer);
        boolean correct = command.selectedOptionIds().equals(question.correctOptionIds());
        int points = scoringService.award(correct, responseTime, timer.duration(),
                question.difficulty(), scoringPolicy);

        participant.recordAnswer(new ParticipantAnswer(command.questionId(), command.selectedOptionIds(),
                participant.getPreferredLanguage(), clock.instant(), responseTime.toMillis(), points));
        participantRepository.save(participant);

        eventPublisher.publish(new AnswerSubmittedEvent(
                session.getId(), participant.getId(), command.questionId(), clock.instant()));
        log.info("Answer accepted from participant {} for question {} in session {}",
                participant.getId(), command.questionId(), session.getId());
        return new AnswerAcceptedView(participant.getId(), command.questionId());
    }

    private static boolean alreadyAnswered(Participant participant, SubmitAnswerCommand command) {
        return participant.answers().stream()
                .anyMatch(answer -> answer.questionId().equals(command.questionId()));
    }

    private Duration elapsedSince(QuestionTimer timer) {
        Duration elapsed = Duration.between(timer.startedAt(), clock.instant());
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }
}
