package io.quizchef.websocket.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.stereotype.Component;

/**
 * Reports whether the STOMP message broker can actually deliver messages —
 * {@code isBrokerAvailable()} is a real signal from the broker's own
 * lifecycle, not a synthetic connection. Contributes to
 * {@code /actuator/health} as {@code realtime}.
 *
 * <p>{@code @EnableWebSocketMessageBroker} always registers both a
 * {@code simpleBrokerMessageHandler} and a {@code stompBrokerRelayMessageHandler}
 * bean regardless of which is active, so autowiring by type alone is
 * ambiguous. {@link WebSocketStompConfiguration} enables only the simple
 * broker (church-scale, no external broker, ADR-005) — the qualifier must
 * move with that decision if a future deployment switches to a relay
 * (RFC-008).
 */
@Component
public class RealtimeHealthIndicator implements HealthIndicator {

    private final AbstractBrokerMessageHandler brokerMessageHandler;

    public RealtimeHealthIndicator(
            @Qualifier("simpleBrokerMessageHandler") AbstractBrokerMessageHandler brokerMessageHandler) {
        this.brokerMessageHandler = brokerMessageHandler;
    }

    @Override
    public Health health() {
        return brokerMessageHandler.isBrokerAvailable()
                ? Health.up().withDetail("broker", "available").build()
                : Health.down().withDetail("broker", "unavailable").build();
    }
}
