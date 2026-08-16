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
 * {@code leaderboard.updated}, and the private {@code
 * participant.answer.accepted}) are projected from the gameplay domain events
 * (RFC-004); scoring specifics live in RFC-006.
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

    // Gameplay.
    QUESTION_PREVIEW_STARTED("question.preview.started"),
    QUESTION_STARTED("question.started"),
    QUESTION_CLOSED("question.closed"),

    /**
     * The host fixed a question mid-session — its wording, its answer key,
     * or both. Purely "what you are looking at is out of date"; the
     * correction itself never rides the wire, because it includes the
     * answer key and this reaches every device in the room. When the
     * corrected question was the one in play, a {@code
     * question.preview.started} follows: its attempt was cancelled and it
     * restarts from the reading period.
     */
    QUESTION_CORRECTED("question.corrected"),

    /**
     * The host pulled a question out of the session. Its answers and points
     * are already reversed and the numbering already closed the gap.
     * Carries no correct answer and no standings — the room never finished
     * the question, and revealing its key would tell them what they were
     * about to be asked. Followed by a {@code question.preview.started} for
     * whatever comes next, or {@code session.finished} when nothing does.
     */
    QUESTION_REMOVED("question.removed"),
    ANSWER_REVEALED("answer.revealed"),
    LEADERBOARD_UPDATED("leaderboard.updated"),
    ANSWER_ACCEPTED("participant.answer.accepted"),

    /**
     * A pure notification that the current question's answer counts moved
     * — no counts and no participant in the payload (privacy: who answered
     * is host material, read through the host-only progress endpoint).
     */
    ANSWER_PROGRESS("answer.progress"),

    /**
     * The host released final standings — participants may now read their
     * own final rank through the personal-result endpoint. No rank rides
     * this notification; it is purely "go re-read your result now".
     */
    FINAL_RESULTS_REVEALED("final.results.revealed"),

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
