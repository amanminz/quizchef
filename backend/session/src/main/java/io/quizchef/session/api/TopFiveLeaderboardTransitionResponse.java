package io.quizchef.session.api;

import io.quizchef.session.application.TopFiveLeaderboardTransitionView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The host's animated Top 5: the standings as they stood before the
 * question in play, and as they stand now it has been revealed. Five rows
 * at most per board (fewer in a small room) — ranks 6 onward never cross
 * the wire before the podium.
 *
 * <p>Both boards are the ranking service's own projections: render the
 * order and the rank numbers verbatim. Never re-sort them, and never infer
 * a tie from two equal scores — the ranking service is the sole authority
 * on order, ranks, and tie-breaks.
 */
public record TopFiveLeaderboardTransitionResponse(
        UUID sessionId,
        UUID questionId,
        @Schema(example = "3") int questionNumber,
        @Schema(example = "10") int totalQuestions,
        @Schema(description = "Always false in a successful read — the last question has no interim "
                + "leaderboard and this endpoint refuses for it", example = "false")
        boolean finalQuestion,
        List<Entry> previousTopFive,
        List<Entry> currentTopFive
) {

    /**
     * One participant's before-and-after. {@code previousRank} is absent
     * for a participant who was not in the previous Top 5 (show "New Top
     * 5", never a fabricated movement distance); {@code currentRank} is
     * absent for one who has dropped out of it (their new position below
     * fifth is deliberately not exposed). {@code previousScore +
     * pointsEarned == currentScore}, always — animate between the two
     * given values rather than computing either.
     */
    @Schema(name = "TopFiveLeaderboardEntry")
    public record Entry(
            UUID participantId,
            @Schema(example = "Amelia") String displayName,
            @Schema(description = "Null when they were not in the previous Top 5", example = "3")
            Integer previousRank,
            @Schema(description = "Null when they have dropped out of the Top 5", example = "1")
            Integer currentRank,
            @Schema(example = "4200") int previousScore,
            @Schema(example = "4850") int currentScore,
            @Schema(example = "650") int pointsEarned
    ) {
    }

    static TopFiveLeaderboardTransitionResponse from(TopFiveLeaderboardTransitionView view) {
        return new TopFiveLeaderboardTransitionResponse(
                view.sessionId(),
                view.questionId(),
                view.questionNumber(),
                view.totalQuestions(),
                view.finalQuestion(),
                view.previousTopFive().stream().map(TopFiveLeaderboardTransitionResponse::entry).toList(),
                view.currentTopFive().stream().map(TopFiveLeaderboardTransitionResponse::entry).toList());
    }

    private static Entry entry(TopFiveLeaderboardTransitionView.Entry entry) {
        return new Entry(
                entry.participantId(),
                entry.displayName(),
                entry.previousRank(),
                entry.currentRank(),
                entry.previousScore(),
                entry.currentScore(),
                entry.pointsEarned());
    }
}
