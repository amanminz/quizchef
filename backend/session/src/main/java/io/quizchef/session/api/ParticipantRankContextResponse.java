package io.quizchef.session.api;

import io.quizchef.session.application.ParticipantRankContextView;
import java.util.UUID;

/**
 * A participant's own standing after a non-final question: rank, score,
 * points just earned, and the immediate ranking neighbours — never the
 * full leaderboard.
 */
public record ParticipantRankContextResponse(
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

    static ParticipantRankContextResponse from(ParticipantRankContextView view) {
        return new ParticipantRankContextResponse(
                view.sessionId(),
                view.participantId(),
                view.displayName(),
                view.rank(),
                view.score(),
                view.pointsEarned(),
                view.ahead() == null ? null
                        : new Neighbour(view.ahead().displayName(), view.ahead().rank(), view.ahead().scoreDifference()),
                view.behind() == null ? null
                        : new Neighbour(view.behind().displayName(), view.behind().rank(), view.behind().scoreDifference()),
                view.tiedWith() == null ? null
                        : new TiedWith(view.tiedWith().displayName(), view.tiedWith().rank()));
    }
}
