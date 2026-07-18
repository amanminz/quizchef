package io.quizchef.websocket.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;

@ExtendWith(MockitoExtension.class)
class RealtimeHealthIndicatorTest {

    @Mock
    private AbstractBrokerMessageHandler brokerMessageHandler;

    @Test
    void upWhenBrokerIsAvailable() {
        when(brokerMessageHandler.isBrokerAvailable()).thenReturn(true);

        Health health = new RealtimeHealthIndicator(brokerMessageHandler).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenBrokerIsUnavailable() {
        when(brokerMessageHandler.isBrokerAvailable()).thenReturn(false);

        Health health = new RealtimeHealthIndicator(brokerMessageHandler).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
