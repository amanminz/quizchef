package io.quizchef.session.application;

import io.quizchef.identity.domain.CurrentUser;
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
}
