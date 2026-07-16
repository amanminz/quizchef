package io.quizchef.session.infrastructure;

import io.quizchef.session.application.SessionCodeGenerator;
import io.quizchef.session.domain.SessionPin;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Six random digits. Deliberately not derived from a timestamp or a
 * sequence — those are guessable and would let one host's PIN predict
 * another's. Uniqueness among active sessions is the orchestration's job,
 * not this generator's.
 */
@Component
public class RandomSessionCodeGenerator implements SessionCodeGenerator {

    private static final int BOUND = 1_000_000;
    private final SecureRandom random = new SecureRandom();

    @Override
    public SessionPin generate() {
        return SessionPin.of("%06d".formatted(random.nextInt(BOUND)));
    }
}
