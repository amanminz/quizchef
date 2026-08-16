package io.quizchef.session.application;

import static org.mockito.Mockito.mock;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.application.GameplayQuestionContentQuery;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.session.infrastructure.persistence.SessionQuestionCorrectionRepository;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPin;
import io.quizchef.session.domain.SessionSettings;
import java.util.Set;
import java.util.UUID;

/**
 * Shared builders for session orchestration service tests.
 */
final class SessionOrchestrationTestFixtures {

    static final UUID QUIZ_VERSION = UUID.randomUUID();

    private SessionOrchestrationTestFixtures() {
    }

    static CurrentUser host() {
        return CurrentUser.authenticated(UUID.randomUUID(), IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER));
    }

    static CurrentUser anonymous() {
        return CurrentUser.anonymous();
    }

    /** A session in CREATED, hosted by the given caller, with default settings. */
    static Session sessionHostedBy(CurrentUser host, String pin) {
        return Session.create(SessionPin.of(pin), QUIZ_VERSION, host.reference(),
                SessionSettings.defaults());
    }

    static Session sessionHostedBy(CurrentUser host, String pin, SessionSettings settings) {
        return Session.create(SessionPin.of(pin), QUIZ_VERSION, host.reference(), settings);
    }

    /**
     * The effective-quiz boundary over a stubbed quiz module, with no
     * corrections stored.
     *
     * <p>A real {@link SessionQuizQuery} rather than a mock of it, because
     * what these tests exercise is the engine reading the quiz *through* it:
     * mocking it would let a service pass its test while reading a sequence
     * no session would ever play. With no corrections and no removals it
     * hands back the authored quiz untouched, so every test written before
     * this boundary existed still means what it meant.
     */
    static SessionQuizQuery sessionQuizQuery(GameplayQuizQuery quizQuery) {
        return sessionQuizQuery(quizQuery, mock(GameplayQuestionContentQuery.class));
    }

    static SessionQuizQuery sessionQuizQuery(GameplayQuizQuery quizQuery,
                                             GameplayQuestionContentQuery contentQuery) {
        return new SessionQuizQuery(quizQuery, contentQuery,
                mock(SessionQuestionCorrectionRepository.class));
    }
}
