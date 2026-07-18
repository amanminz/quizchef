package io.quizchef.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.event.HostAccessGrantedEvent;
import io.quizchef.identity.domain.event.IdentityAuthenticatedEvent;
import io.quizchef.identity.domain.event.IdentityAuthorizationDeniedEvent;
import io.quizchef.identity.domain.event.IdentityRegisteredEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityEventLoggerTest {

    private final IdentityEventLogger logger = new IdentityEventLogger();
    private final IdentityReference identity =
            new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);

    @Test
    void logsRegistrationWithIdentityIdButNoPii() {
        try (LogCapture capture = new LogCapture(IdentityEventLogger.class)) {
            logger.on(new IdentityRegisteredEvent(identity, Instant.now()));

            assertThat(capture.messages()).anySatisfy(message -> {
                assertThat(message).contains("identity.registered");
                assertThat(message).contains(identity.identityId().toString());
            });
        }
    }

    @Test
    void logsLogin() {
        try (LogCapture capture = new LogCapture(IdentityEventLogger.class)) {
            logger.on(new IdentityAuthenticatedEvent(identity, Instant.now()));

            assertThat(capture.messages()).anyMatch(message -> message.contains("identity.login"));
        }
    }

    @Test
    void logsHostPromotion() {
        try (LogCapture capture = new LogCapture(IdentityEventLogger.class)) {
            logger.on(new HostAccessGrantedEvent(identity, Instant.now()));

            assertThat(capture.messages()).anyMatch(message -> message.contains("identity.host_promoted"));
        }
    }

    @Test
    void logsAuthorizationDenied() {
        try (LogCapture capture = new LogCapture(IdentityEventLogger.class)) {
            logger.on(new IdentityAuthorizationDeniedEvent(identity, Permission.QUIZ_CREATE, Instant.now()));

            assertThat(capture.messages()).anySatisfy(message -> {
                assertThat(message).contains("security.authorization_denied");
                assertThat(message).contains("QUIZ_CREATE");
            });
        }
    }
}
