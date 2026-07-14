package io.quizchef.common.event;

import java.time.Instant;

/**
 * A fact that happened in the business domain (ADR-005).
 *
 * <p>Framework independent by design: domain events know nothing about
 * Spring, transports, or storage. Only application services publish them;
 * subscribers perform delivery and side effects, never business operations.
 */
public interface DomainEvent {

    Instant occurredAt();
}
