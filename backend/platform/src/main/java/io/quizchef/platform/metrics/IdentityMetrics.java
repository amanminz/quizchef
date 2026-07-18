package io.quizchef.platform.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.quizchef.identity.domain.event.HostAccessGrantedEvent;
import io.quizchef.identity.domain.event.IdentityAuthenticatedEvent;
import io.quizchef.identity.domain.event.IdentityRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Identity operational metrics: registrations, logins, host promotions.
 */
@Component
public class IdentityMetrics {

    private final MeterRegistry meterRegistry;

    public IdentityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    void on(IdentityRegisteredEvent event) {
        meterRegistry.counter("identity.registrations").increment();
    }

    @EventListener
    void on(IdentityAuthenticatedEvent event) {
        meterRegistry.counter("identity.logins").increment();
    }

    @EventListener
    void on(HostAccessGrantedEvent event) {
        meterRegistry.counter("identity.host_promotions").increment();
    }
}
