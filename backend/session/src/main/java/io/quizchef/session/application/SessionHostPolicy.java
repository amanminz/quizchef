package io.quizchef.session.application;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.NotSessionHostException;

/**
 * Hosting a session is exclusive to the identity that created it. Having the
 * {@code QUIZ_HOST} permission lets you host <em>your</em> sessions, not
 * anyone else's — the permission and the ownership are separate checks, as
 * with quiz authoring.
 */
final class SessionHostPolicy {

    private SessionHostPolicy() {
    }

    static void requireHost(CurrentUser currentUser, Session session) {
        if (!session.getHostIdentity().identityId().equals(currentUser.identityId())) {
            throw new NotSessionHostException();
        }
    }
}
