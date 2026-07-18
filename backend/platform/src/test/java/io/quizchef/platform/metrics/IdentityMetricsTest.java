package io.quizchef.platform.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.event.HostAccessGrantedEvent;
import io.quizchef.identity.domain.event.IdentityAuthenticatedEvent;
import io.quizchef.identity.domain.event.IdentityRegisteredEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IdentityMetrics metrics = new IdentityMetrics(registry);
    private final IdentityReference identity =
            new IdentityReference(UUID.randomUUID(), IdentityType.REGISTERED);

    @Test
    void countsRegistrations() {
        metrics.on(new IdentityRegisteredEvent(identity, Instant.now()));

        assertThat(registry.counter("identity.registrations").count()).isEqualTo(1.0);
    }

    @Test
    void countsLogins() {
        metrics.on(new IdentityAuthenticatedEvent(identity, Instant.now()));

        assertThat(registry.counter("identity.logins").count()).isEqualTo(1.0);
    }

    @Test
    void countsHostPromotions() {
        metrics.on(new HostAccessGrantedEvent(identity, Instant.now()));

        assertThat(registry.counter("identity.host_promotions").count()).isEqualTo(1.0);
    }
}
