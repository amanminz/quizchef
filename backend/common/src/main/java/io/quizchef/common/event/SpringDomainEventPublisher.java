package io.quizchef.common.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * In-process event dispatcher: delegates to Spring's application event
 * machinery behind the framework-free {@link DomainEventPublisher} port.
 *
 * <p>Subscribers register with {@code @EventListener} on the concrete event
 * type. No message broker is involved (ADR-005).
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    public SpringDomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(DomainEvent event) {
        delegate.publishEvent(event);
    }
}
