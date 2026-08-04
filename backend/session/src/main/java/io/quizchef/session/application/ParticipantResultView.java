package io.quizchef.session.application;

import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import java.util.UUID;

/**
 * One participant's own progress during the quiz: what the question in
 * play just awarded them, what they have altogether, and the counts that
 * frame it.
 *
 * <p><strong>No rank.</strong> Not a nulled field — there is no position
 * on this projection at all. A participant is never told where they stand
 * mid-quiz: it spoils the leaderboard moment the host is running for the
 * room, and a player shown a low number after every question stops
 * enjoying a quiz they could still finish well. Their own score is their
 * own business and stays; where it places them is the ceremony's to
 * reveal, through {@link ParticipantFinalPlacementView} and only once the
 * host has released results.
 *
 * <p>{@code pointsEarned} is what the current question awarded, read from
 * the stored answer — so a refresh mid-reveal shows the same number,
 * rather than losing it the way a diff of two client-side snapshots does.
 */
public record ParticipantResultView(
        UUID sessionId,
        SessionState state,
        SessionPhase currentPhase,
        int totalQuestions,
        int participantCount,
        UUID participantId,
        String displayName,
        int score,
        int pointsEarned
) {
}
