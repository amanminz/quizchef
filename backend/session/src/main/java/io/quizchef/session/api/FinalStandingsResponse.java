package io.quizchef.session.api;

import io.quizchef.session.domain.FinalStanding;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A finished session's standings, as captured when it ended.
 *
 * <p>Host-only administrative history: every participant, their finishing
 * rank, and their score. Rendered in the order given — these ranks are the
 * ones the game produced, read back rather than recomputed, so nothing here
 * should be re-sorted or re-ranked by a client.
 *
 * <p>{@code entries} is empty for a session that has not finished, and for
 * one that finished before this history was recorded. Neither is an error.
 */
public record FinalStandingsResponse(
        UUID sessionId,
        @Schema(description = "When the standings were captured; null when none exist")
        Instant capturedAt,
        List<Entry> entries
) {

    /** One participant's finish, exactly as it stood at completion. */
    @Schema(name = "FinalStandingEntry")
    public record Entry(
            UUID participantId,
            @Schema(description = "The name as it read at completion", example = "Amelia")
            String displayName,
            @Schema(example = "1") int rank,
            @Schema(example = "8450") int score
    ) {
    }

    static FinalStandingsResponse from(UUID sessionId, List<FinalStanding> standings) {
        return new FinalStandingsResponse(
                sessionId,
                standings.isEmpty() ? null : standings.getFirst().getCapturedAt(),
                standings.stream()
                        .map(standing -> new Entry(
                                standing.getParticipantId(),
                                standing.getDisplayNameAtCompletion(),
                                standing.getFinalRank(),
                                standing.getFinalScore()))
                        .toList());
    }
}
