package io.quizchef.session.application;

import io.quizchef.session.domain.FinalPlacementLabel;
import io.quizchef.session.domain.FinalPlacementVisibility;
import java.util.UUID;

/**
 * What one participant is told about how they finished — the only
 * participant-facing source of final ranking there is.
 *
 * <p>Two shapes, decided by the server, discriminated by
 * {@code visibility}:
 *
 * <ul>
 *   <li>{@code EXACT_RANK} — inside the reveal group: their position,
 *       score, and label. {@code rank} and {@code label} are set;
 *       the neighbour fields are not.</li>
 *   <li>{@code RELATIVE_ONLY} — outside it: their score and who they
 *       finished either side of, by <em>name only</em>. {@code rank} and
 *       {@code label} are null, and no neighbour rank, neighbour score,
 *       or score gap exists anywhere in the shape to leak one.</li>
 * </ul>
 *
 * <p>The absence is structural, not a rendering rule. A participant
 * outside the reveal group has no exact position on this projection at
 * all, so no client bug, cached response, or future screen can show them
 * one — the same reasoning that made the Top 5 transition withhold ranks
 * below fifth rather than send them and ask the client to be careful.
 */
public record ParticipantFinalPlacementView(
        UUID sessionId,
        UUID participantId,
        String displayName,
        FinalPlacementVisibility visibility,
        Integer rank,
        int score,
        FinalPlacementLabel label,
        int totalQuestions,
        int participantCount,
        Neighbour aheadOf,
        Neighbour behind,
        Neighbour tiedWith
) {

    /**
     * Someone this participant finished near. A name and nothing else —
     * carrying their rank or score would hand back, sideways, exactly the
     * position the reveal group exists to withhold.
     */
    public record Neighbour(String displayName) {
    }
}
