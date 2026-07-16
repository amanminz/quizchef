package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.ConflictException;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.QuizPublicationQuery;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPin;
import io.quizchef.session.domain.SessionSettings;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.event.SessionCreatedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a live session for a published quiz version, hosted by the
 * authenticated caller.
 *
 * <p>The host is always {@code CurrentUser.reference()} — never client-
 * supplied. The PIN comes from the {@link SessionCodeGenerator} port; this
 * service owns uniqueness: it asks for a candidate and retries until one is
 * free among active sessions, with the partial unique index as the final
 * authority for the rare same-instant race.
 */
@Service
public class CreateSessionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CreateSessionApplicationService.class);
    private static final int MAX_PIN_ATTEMPTS = 10;

    private final SessionRepository sessionRepository;
    private final QuizPublicationQuery quizPublicationQuery;
    private final SessionCodeGenerator sessionCodeGenerator;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public CreateSessionApplicationService(SessionRepository sessionRepository,
                                           QuizPublicationQuery quizPublicationQuery,
                                           SessionCodeGenerator sessionCodeGenerator,
                                           AuthorizationService authorizationService,
                                           DomainEventPublisher eventPublisher,
                                           Clock clock) {
        this.sessionRepository = sessionRepository;
        this.quizPublicationQuery = quizPublicationQuery;
        this.sessionCodeGenerator = sessionCodeGenerator;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSummaryView create(CurrentUser currentUser, CreateSessionCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        quizPublicationQuery.requirePublished(command.publishedQuizVersionId());

        Session session = Session.create(allocateUniquePin(), command.publishedQuizVersionId(),
                currentUser.reference(), SessionSettings.defaults());
        try {
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException raced) {
            throw new ConflictException("session.pin-unavailable",
                    "Could not allocate a session PIN, please retry");
        }

        eventPublisher.publish(new SessionCreatedEvent(session.getId(), session.getHostIdentity(),
                session.getPublishedQuizVersionId(), clock.instant()));
        log.info("Session {} created by identity {} for quiz version {}",
                session.getId(), currentUser.identityId(), command.publishedQuizVersionId());
        return SessionSummaryView.of(session);
    }

    private SessionPin allocateUniquePin() {
        for (int attempt = 0; attempt < MAX_PIN_ATTEMPTS; attempt++) {
            SessionPin candidate = sessionCodeGenerator.generate();
            if (!sessionRepository.existsBySessionPinValueAndStateNot(
                    candidate.value(), SessionState.ARCHIVED)) {
                return candidate;
            }
        }
        throw new ConflictException("session.pin-unavailable",
                "Could not allocate a unique session PIN, please retry");
    }
}
