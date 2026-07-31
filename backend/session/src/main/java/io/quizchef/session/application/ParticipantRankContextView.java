package io.quizchef.session.application;

import java.util.UUID;

/**
 * A participant's own standing after a non-final question: their rank,
 * score, and points just earned, plus the immediate neighbours in the
 * ranking — never the full leaderboard (Live Event UX privacy). At most
 * one of {@code ahead}/{@code behind} collapses into {@code tiedWith} when
 * that neighbour's score is equal, since the leaderboard's own ranks are
 * fully tie-broken (submission time, then join order) and never share a
 * numeric rank even when scores tie.
 */
public record ParticipantRankContextView(
        UUID sessionId,
        UUID participantId,
        String displayName,
        int rank,
        int score,
        int pointsEarned,
        Neighbour ahead,
        Neighbour behind,
        TiedWith tiedWith
) {

    public record Neighbour(String displayName, int rank, int scoreDifference) {
    }

    public record TiedWith(String displayName, int rank) {
    }
}
