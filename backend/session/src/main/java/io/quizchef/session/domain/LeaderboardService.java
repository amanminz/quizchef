package io.quizchef.session.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        Map<UUID, Integer> joinOrder = roster.stream()
                .collect(Collectors.toMap(SessionRosterEntry::participantId, SessionRosterEntry::joinOrder));

        List<Participant> ordered = participants.stream()
                .sorted(Comparator
                        .comparingInt(Participant::getTotalScore).reversed()
                        .thenComparing(LeaderboardService::lastSubmissionOrMax)
                        .thenComparingInt(participant ->
                                joinOrder.getOrDefault(participant.getId(), Integer.MAX_VALUE)))
                .toList();

        return IntStream.range(0, ordered.size())
                .mapToObj(index -> {
                    Participant participant = ordered.get(index);
                    return new LeaderboardEntry(participant.getId(), participant.getDisplayName(),
                            participant.getTotalScore(), index + 1);
                })
                .toList();
    }

    /**
     * The time a participant locked in their most recent answer; the far
     * future when they have not answered, so they rank after those who have.
     */
    private static Instant lastSubmissionOrMax(Participant participant) {
        return participant.answers().stream()
                .map(ParticipantAnswer::submittedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.MAX);
    }
}
