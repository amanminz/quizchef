package io.quizchef.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.websocket.api.Topics;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopicsTest {

    @Test
    void resolvesEachDestinationUnderTheBrokerPrefix() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID participantId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(Topics.session(sessionId)).isEqualTo("/topic/session/" + sessionId);
        assertThat(Topics.participant(participantId)).isEqualTo("/topic/participant/" + participantId);
        assertThat(Topics.host(sessionId)).isEqualTo("/topic/host/" + sessionId);
        assertThat(Topics.SYSTEM).isEqualTo("/topic/system");
    }

    @Test
    void everyDestinationStartsWithTheBrokerPrefix() {
        UUID id = UUID.randomUUID();

        assertThat(Topics.session(id)).startsWith(Topics.BROKER_PREFIX);
        assertThat(Topics.participant(id)).startsWith(Topics.BROKER_PREFIX);
        assertThat(Topics.host(id)).startsWith(Topics.BROKER_PREFIX);
        assertThat(Topics.SYSTEM).startsWith(Topics.BROKER_PREFIX);
    }
}
