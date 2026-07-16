package io.quizchef.websocket.api;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The stable, language-agnostic vocabulary of the realtime protocol.
 *
 * <p>Each constant serializes to a dotted wire name (for example {@code
 * participant.reconnected}) via {@link #wireName()} — deliberately
 * <em>decoupled</em> from the internal domain event class names. A client
 * subscribes to {@code participant.reconnected}; it never sees
 * {@code ParticipantReconnectedEvent}, so renaming or repackaging a domain
 * class can never break the wire contract.
 *
 * <p>The gameplay types ({@code question.*}, {@code answer.revealed},
 * {@code leaderboard.updated}) are reserved here as the settled vocabulary;
 * their payloads and producers arrive with the gameplay PRs (RFC-006).
 */
public enum ProtocolMessageType {

    // Session lifecycle — projected from session domain events today.
    LOBBY_OPENED("lobby.opened"),
    SESSION_STARTED("session.started"),
    SESSION_FINISHED("session.finished"),

    // Roster — projected from participant domain events today.
    PARTICIPANT_JOINED("participant.joined"),
    PARTICIPANT_DISCONNECTED("participant.disconnected"),
    PARTICIPANT_RECONNECTED("participant.reconnected"),

    // Gameplay — reserved vocabulary; producers/payloads arrive with RFC-006.
    QUESTION_STARTED("question.started"),
    QUESTION_CLOSED("question.closed"),
    ANSWER_REVEALED("answer.revealed"),
    LEADERBOARD_UPDATED("leaderboard.updated"),

    // Reconnection — the replay/sync snapshot delivered to one participant.
    SESSION_SNAPSHOT("session.snapshot");

    private final String wireName;

    ProtocolMessageType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }
}
