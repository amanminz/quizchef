package io.quizchef.common.event;

/**
 * Port through which application services publish domain events.
 *
 * <p>The dispatch mechanism behind this interface is an infrastructure
 * detail; business code never sees it.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
