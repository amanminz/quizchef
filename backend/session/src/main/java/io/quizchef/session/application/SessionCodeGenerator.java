package io.quizchef.session.application;

import io.quizchef.session.domain.SessionPin;

/**
 * Produces a <em>candidate</em> join code. The orchestration asks for a
 * candidate and checks its uniqueness against active sessions itself, so the
 * generator stays ignorant of persistence and can evolve independently —
 * numeric PINs today, alphanumeric room codes, organization prefixes, or
 * invitation codes later — without touching the create flow.
 */
public interface SessionCodeGenerator {

    SessionPin generate();
}
