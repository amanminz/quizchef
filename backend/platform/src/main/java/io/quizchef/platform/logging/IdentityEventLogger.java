package io.quizchef.platform.logging;

import io.quizchef.identity.domain.event.HostAccessGrantedEvent;
import io.quizchef.identity.domain.event.IdentityAuthenticatedEvent;
import io.quizchef.identity.domain.event.IdentityRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs identity lifecycle transitions worth operating on: registration,
 * login, host promotion. Every identity domain event already carries only
 * an {@link io.quizchef.identity.domain.IdentityReference} (id + type), so
 * there is no PII to accidentally log.
 *
 * <p>Deliberately does not listen to {@code IdentityAuthorizedEvent} — it
 * fires on every permission check, which is per-request noise, not an
 * operational transition (RFC-010).
 */
@Component
public class IdentityEventLogger {

    private static final Logger log = LoggerFactory.getLogger(IdentityEventLogger.class);

    @EventListener
    void on(IdentityRegisteredEvent event) {
        log.info("identity.registered identityId={} identityType={}",
                event.identity().identityId(), event.identity().identityType());
    }

    @EventListener
    void on(IdentityAuthenticatedEvent event) {
        log.info("identity.login identityId={} identityType={}",
                event.identity().identityId(), event.identity().identityType());
    }

    @EventListener
    void on(HostAccessGrantedEvent event) {
        log.info("identity.host_promoted identityId={} identityType={}",
                event.identity().identityId(), event.identity().identityType());
    }
}
