package io.quizchef.session.application;

import java.util.List;
import java.util.UUID;

/**
 * The two authoritative standings a host's animated Top 5 moves between:
 * the board as it stood before the question in play, and the board as it
 * stands now that the question has been revealed. Host-only, exactly like
 * {@link SessionResultsView} and {@link AnswerDistributionView} — a
 * participant device never sees another player's name, score, or rank.
 *
 * <p>Both boards are the ranking service's own projections (ADR-006): the
 * client renders the order and the rank numbers verbatim and never
 * computes, sorts, or infers a tie from equal scores.
 *
 * <p>Only ever built for a <em>non-final</em> question — the quiz's last
 * question has no interim leaderboard at all (the standings are held for
 * the host's winner ceremony), so the query service refuses outright
 * rather than returning an empty transition. {@code finalQuestion} is
 * therefore always {@code false} in a successful read; it is on the
 * contract so a client can assert the rule rather than assume it.
 */
public record TopFiveLeaderboardTransitionView(
        UUID sessionId,
        UUID questionId,
        int questionNumber,
        int totalQuestions,
        boolean finalQuestion,
        List<Entry> previousTopFive,
        List<Entry> currentTopFive
) {

    /**
     * One participant's before-and-after, with both ranks deliberately
     * nullable and each one absent exactly when revealing it would say
     * something the host's screen has no business showing:
     *
     * <ul>
     *   <li>In {@code currentTopFive}, {@code previousRank} is null for a
     *       participant who was <em>not</em> in the previous Top 5 — the
     *       client shows "New Top 5" rather than inventing a movement
     *       distance out of a rank the board never displayed.</li>
     *   <li>In {@code previousTopFive}, {@code currentRank} is null for a
     *       participant who has <em>dropped out</em> of the Top 5 — their
     *       new position below fifth is not exposed; the row simply
     *       leaves.</li>
     * </ul>
     *
     * <p>{@code pointsEarned} is what this question awarded them (0 when
     * they did not answer), and {@code previousScore + pointsEarned ==
     * currentScore} always holds — both scores come from the ranking
     * service's two projections, never from client-side arithmetic.
     */
    public record Entry(
            UUID participantId,
            String displayName,
            Integer previousRank,
            Integer currentRank,
            int previousScore,
            int currentScore,
            int pointsEarned
    ) {
    }
}
