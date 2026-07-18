package io.quizchef.websocket.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Realtime transport metrics — the one module that knows STOMP exists
 * (ADR-004) is also the one that measures it, listening to Spring's own
 * STOMP session lifecycle events.
 *
 * <p>A true "reconnect" (a browser resuming after a drop) is not cheaply
 * distinguishable server-side from a first connect without client-supplied
 * state, so this reports total connection opens rather than fabricating a
 * reconnect-specific counter — an honest limitation, not a fake metric
 * (RFC-010).
 */
@Component
public class RealtimeConnectionMetrics {

    private final Counter connectionsOpened;
    private final AtomicInteger activeConnections;
    private final AtomicInteger activeSubscriptions;

    public RealtimeConnectionMetrics(MeterRegistry meterRegistry) {
        this.connectionsOpened = meterRegistry.counter("realtime.connections.opened");
        this.activeConnections = meterRegistry.gauge("realtime.connections.active", new AtomicInteger(0));
        this.activeSubscriptions = meterRegistry.gauge("realtime.subscriptions.active", new AtomicInteger(0));
    }

    @EventListener
    void on(SessionConnectedEvent event) {
        connectionsOpened.increment();
        activeConnections.incrementAndGet();
    }

    @EventListener
    void on(SessionDisconnectEvent event) {
        activeConnections.decrementAndGet();
    }

    @EventListener
    void on(SessionSubscribeEvent event) {
        activeSubscriptions.incrementAndGet();
    }

    @EventListener
    void on(SessionUnsubscribeEvent event) {
        activeSubscriptions.decrementAndGet();
    }
}
