package io.quizchef.websocket.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

class RealtimeConnectionMetricsTest {

    private static final Message<byte[]> STOMP_FRAME = MessageBuilder.withPayload(new byte[0]).build();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RealtimeConnectionMetrics metrics = new RealtimeConnectionMetrics(registry);

    @Test
    void tracksActiveConnectionsAcrossConnectAndDisconnect() {
        metrics.on(new SessionConnectedEvent(this, STOMP_FRAME));

        assertThat(registry.get("realtime.connections.active").gauge().value()).isEqualTo(1.0);
        assertThat(registry.counter("realtime.connections.opened").count()).isEqualTo(1.0);

        metrics.on(new SessionDisconnectEvent(this, STOMP_FRAME, "session-1", CloseStatus.NORMAL));

        assertThat(registry.get("realtime.connections.active").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void tracksActiveSubscriptionsAcrossSubscribeAndUnsubscribe() {
        metrics.on(new SessionSubscribeEvent(this, STOMP_FRAME));

        assertThat(registry.get("realtime.subscriptions.active").gauge().value()).isEqualTo(1.0);

        metrics.on(new SessionUnsubscribeEvent(this, STOMP_FRAME));

        assertThat(registry.get("realtime.subscriptions.active").gauge().value()).isEqualTo(0.0);
    }
}
