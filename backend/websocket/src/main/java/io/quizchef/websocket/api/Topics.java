package io.quizchef.websocket.api;

import java.util.UUID;

/**
 * The one place STOMP destinations are built. Nothing else in the codebase
 * writes a topic string, so the topic hierarchy can change in exactly one
 * file.
 *
 * <p>Hierarchy (all under the {@code /topic} broker prefix):
 * <ul>
 *   <li>{@code /topic/session/{sessionId}} — broadcast to everyone in a
 *       session (lobby, roster, gameplay);</li>
 *   <li>{@code /topic/participant/{participantId}} — one participant only
 *       (their reconnection snapshot, personal feedback);</li>
 *   <li>{@code /topic/host/{sessionId}} — the host's control channel;</li>
 *   <li>{@code /topic/system} — server-wide notices.</li>
 * </ul>
 */
public final class Topics {

    /** Broker prefix; must match the simple broker configured for STOMP. */
    public static final String BROKER_PREFIX = "/topic";

    public static final String SYSTEM = BROKER_PREFIX + "/system";

    private Topics() {
    }

    public static String session(UUID sessionId) {
        return BROKER_PREFIX + "/session/" + sessionId;
    }

    public static String participant(UUID participantId) {
        return BROKER_PREFIX + "/participant/" + participantId;
    }

    public static String host(UUID sessionId) {
        return BROKER_PREFIX + "/host/" + sessionId;
    }
}
