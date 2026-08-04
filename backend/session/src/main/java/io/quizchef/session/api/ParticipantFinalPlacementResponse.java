package io.quizchef.session.api;

import io.quizchef.session.application.ParticipantFinalPlacementView;
import io.quizchef.session.domain.FinalPlacementLabel;
import io.quizchef.session.domain.FinalPlacementVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * How one participant finished, in whichever of two shapes the server
 * decided for them — read {@code visibility} first.
 *
 * <p>{@code EXACT_RANK} (the podium and the top half): {@code rank} and
 * {@code label} are present, the neighbour fields are null.
 *
 * <p>{@code RELATIVE_ONLY} (everyone else): {@code rank} and {@code label}
 * are null, and {@code aheadOf}/{@code behind}/{@code tiedWith} carry
 * <em>display names only</em>. There is no neighbour rank, no neighbour
 * score, and no score gap anywhere in this shape — a participant outside
 * the reveal group has no exact position on the wire at all, so none can
 * be rendered, cached, or inferred.
 */
public record ParticipantFinalPlacementResponse(
        UUID sessionId,
        UUID participantId,
        String displayName,
        @Schema(description = "EXACT_RANK for the reveal group, RELATIVE_ONLY for everyone else")
        FinalPlacementVisibility visibility,
        @Schema(description = "Present only when visibility is EXACT_RANK", example = "7")
        Integer rank,
        @Schema(example = "8450") int score,
        @Schema(description = "Present only when visibility is EXACT_RANK")
        FinalPlacementLabel label,
        @Schema(example = "10") int totalQuestions,
        @Schema(example = "20") int participantCount,
        @Schema(description = "Whom they finished ahead of; RELATIVE_ONLY, name only")
        Neighbour aheadOf,
        @Schema(description = "Whom they finished behind; RELATIVE_ONLY, name only")
        Neighbour behind,
        @Schema(description = "Someone the ranking assigned an equal rank; RELATIVE_ONLY, name only")
        Neighbour tiedWith
) {

    /** A name, and deliberately nothing else. */
    @Schema(name = "FinalPlacementNeighbour")
    public record Neighbour(@Schema(example = "David") String displayName) {
    }

    static ParticipantFinalPlacementResponse from(ParticipantFinalPlacementView view) {
        return new ParticipantFinalPlacementResponse(
                view.sessionId(),
                view.participantId(),
                view.displayName(),
                view.visibility(),
                view.rank(),
                view.score(),
                view.label(),
                view.totalQuestions(),
                view.participantCount(),
                neighbour(view.aheadOf()),
                neighbour(view.behind()),
                neighbour(view.tiedWith()));
    }

    private static Neighbour neighbour(ParticipantFinalPlacementView.Neighbour neighbour) {
        return neighbour == null ? null : new Neighbour(neighbour.displayName());
    }
}
