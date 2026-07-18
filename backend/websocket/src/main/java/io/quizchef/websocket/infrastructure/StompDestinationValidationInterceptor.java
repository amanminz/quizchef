package io.quizchef.websocket.infrastructure;

import io.quizchef.websocket.api.Topics;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Rejects malformed STOMP frames before they reach the broker (Phase 3
 * PR #3 / RFC-011): a SUBSCRIBE destination must be one of {@link Topics}'
 * known patterns with a well-formed UUID suffix, and a SEND destination
 * must at least carry the configured application prefix.
 *
 * <p>This is destination <em>well-formedness</em>, not per-session
 * authorization — it cannot yet verify a subscriber actually belongs to
 * the session it's subscribing to, because that requires the inbound STOMP
 * command channel RFC-005 still defers (nothing resolves a Principal on
 * CONNECT today). That gap is documented, not silently ignored, in
 * RFC-011.
 */
@Component
public class StompDestinationValidationInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompDestinationValidationInterceptor.class);

    private static final String APPLICATION_PREFIX = "/app";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            requireValidDestination(accessor.getDestination(), this::isKnownTopic);
        } else if (accessor.getCommand() == StompCommand.SEND) {
            requireValidDestination(accessor.getDestination(),
                    destination -> destination.startsWith(APPLICATION_PREFIX));
        }
        return message;
    }

    private void requireValidDestination(String destination, Predicate<String> isValid) {
        if (destination == null || !isValid.test(destination)) {
            log.warn("security.invalid_stomp_destination destination={}", destination);
            throw new IllegalArgumentException("Invalid or unrecognized STOMP destination: " + destination);
        }
    }

    private boolean isKnownTopic(String destination) {
        return destination.equals(Topics.SYSTEM)
                || matchesIdSuffixed(destination, Topics.BROKER_PREFIX + "/session/")
                || matchesIdSuffixed(destination, Topics.BROKER_PREFIX + "/participant/")
                || matchesIdSuffixed(destination, Topics.BROKER_PREFIX + "/host/");
    }

    private boolean matchesIdSuffixed(String destination, String prefix) {
        if (!destination.startsWith(prefix)) {
            return false;
        }
        try {
            UUID.fromString(destination.substring(prefix.length()));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
