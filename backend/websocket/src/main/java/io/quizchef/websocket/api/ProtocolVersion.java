package io.quizchef.websocket.api;

/**
 * The version of the QuizChef realtime <em>wire</em> protocol — not the
 * application version.
 *
 * <p>Every {@link ProtocolMessage} carries it, so the protocol can evolve
 * (new fields, new message types, a new replay format) while older web,
 * mobile, or third-party clients keep working: a client reads the version
 * and adapts, and the server can serve more than one at once. The cost is
 * one integer per message; the payoff is not breaking every deployed client
 * the first time the protocol changes.
 *
 * <p>Bumped only on a breaking change to the wire format.
 */
public final class ProtocolVersion {

    /** The version this server speaks. */
    public static final int CURRENT = 1;

    private ProtocolVersion() {
    }
}
