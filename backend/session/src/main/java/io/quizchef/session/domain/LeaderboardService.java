package io.quizchef.session.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Projects the current standings — always computed, never stored (ADR-006).
 *
 * <p>Ordering: highest score first; ties broken by who reached that score
 * soonest (their earliest-latest answer time), then by join order, so the
 * ranking is total and deterministic. A participant with no answers sorts
 * after those who have answered at the same score.
 */
public class LeaderboardService {

    public List<LeaderboardEntry> rank(List<Participant> participants, List<SessionRosterEntry> roster) {
        return rank(participants, roster, answer -> true);
    }

    /**
     * The standings as they stood <em>before</em> the given question was
     * played: the identical ranking rule applied to each participant's
     * answers with that question's answer left out — score and tie-break
     * time alike. This is a real ranking of a real earlier state, not the
     * current board with numbers subtracted from it: the tie-break
     * (who reached the score first) genuinely differs between the two
     * boards, so a "previous rank" recovered by arithmetic on the current
     * one would be wrong exactly when it matters most — at a tie.
     *
     * <p>Sound because a {@link Participant}'s cached score is by
     * construction the sum of its answers' {@code pointsAwarded}
     * ({@link Participant#recordAnswer}) and at most one answer exists per
     * question, so dropping one answer yields precisely the score that
     * participant held before that question.
     */
    public List<LeaderboardEntry> rankBefore(List<Participant> participants,
                                             List<SessionRosterEntry> roster,
                                             UUID questionId) {
        return rank(participants, roster, answer -> !answer.questionId().equals(questionId));
    }

    private List<LeaderboardEntry> rank(List<Participant> participants,
                                        List<SessionRosterEntry> roster,
                                        Predicate<ParticipantAnswer> counted) {
        Map<UUID, Integer> joinOrder = roster.stream()
                .collect(Collectors.toMap(SessionRosterEntry::participantId, SessionRosterEntry::joinOrder));

        List<Standing> ordered = participants.stream()
                .map(participant -> Standing.of(participant, counted))
                .sorted(Comparator
                        .comparingInt(Standing::score).reversed()
                        .thenComparing(Standing::lastSubmission)
                        .thenComparingInt(standing ->
                                joinOrder.getOrDefault(standing.participantId(), Integer.MAX_VALUE)))
                .toList();

        return IntStream.range(0, ordered.size())
                .mapToObj(index -> {
                    Standing standing = ordered.get(index);
                    return new LeaderboardEntry(standing.participantId(), standing.displayName(),
                            standing.score(), index + 1);
                })
                .toList();
    }

    /**
     * One participant's score and tie-break time over the counted subset of
     * their answers. {@code lastSubmission} is the time they locked in their
     * most recent counted answer, or the far future when they have none — so
     * they rank after those who have answered.
     */
    private record Standing(UUID participantId, String displayName, int score, Instant lastSubmission) {

        static Standing of(Participant participant, Predicate<ParticipantAnswer> counted) {
            List<ParticipantAnswer> answers = participant.answers().stream().filter(counted).toList();
            int score = answers.stream().mapToInt(ParticipantAnswer::pointsAwarded).sum();
            Instant lastSubmission = answers.stream()
                    .map(ParticipantAnswer::submittedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(Instant.MAX);
            return new Standing(participant.getId(), participant.getDisplayName(), score, lastSubmission);
        }
    }
}
