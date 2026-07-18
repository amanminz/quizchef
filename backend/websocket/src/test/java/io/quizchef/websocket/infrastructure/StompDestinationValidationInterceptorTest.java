package io.quizchef.websocket.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class StompDestinationValidationInterceptorTest {

    private final StompDestinationValidationInterceptor interceptor = new StompDestinationValidationInterceptor();

    @Test
    void allowsSubscribingToAKnownSessionTopic() {
        Message<?> message = subscribeTo("/topic/session/" + UUID.randomUUID());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void allowsSubscribingToTheParticipantTopic() {
        Message<?> message = subscribeTo("/topic/participant/" + UUID.randomUUID());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void allowsSubscribingToTheHostTopic() {
        Message<?> message = subscribeTo("/topic/host/" + UUID.randomUUID());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void allowsSubscribingToTheSystemTopic() {
        Message<?> message = subscribeTo("/topic/system");

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void rejectsASubscribeToAnUnknownTopic() {
        Message<?> message = subscribeTo("/topic/session/not-a-uuid");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsASubscribeToAWhollyUnrelatedDestination() {
        Message<?> message = subscribeTo("/topic/../../etc/passwd");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsSendingToTheApplicationPrefix() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/sessions/whatever");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void rejectsSendingOutsideTheApplicationPrefix() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/session/" + UUID.randomUUID());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passesThroughNonStompMessagesUntouched() {
        Message<?> message = MessageBuilder.withPayload(new byte[0]).build();

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void passesThroughConnectFramesWithNoDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    private Message<?> subscribeTo(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
