package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.quiz.domain.LanguageCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaderboardServiceTest {

    private static final LanguageCode EN = LanguageCode.of("en");
    private static final UUID SESSION_ID = UUID.randomUUID();

    private final LeaderboardService leaderboardService = new LeaderboardService();

    @Test
    void ordersByScoreDescending() {
        Participant low = participant("Low", 100, Instant.parse("2026-07-16T10:00:10Z"));
        Participant high = participant("High", 900, Instant.parse("2026-07-16T10:00:11Z"));
        Participant mid = participant("Mid", 500, Instant.parse("2026-07-16T10:00:12Z"));

        List<LeaderboardEntry> board = leaderboardService.rank(
                List.of(low, high, mid), roster(low, high, mid));

        assertThat(board).extracting(LeaderboardEntry::displayName).containsExactly("High", "Mid", "Low");
        assertThat(board).extracting(LeaderboardEntry::rank).containsExactly(1, 2, 3);
    }

    @Test
    void breaksScoreTiesByEarliestSubmission() {
        Participant slower = participant("Slower", 500, Instant.parse("2026-07-16T10:00:20Z"));
        Participant faster = participant("Faster", 500, Instant.parse("2026-07-16T10:00:10Z"));

        List<LeaderboardEntry> board = leaderboardService.rank(
                List.of(slower, faster), roster(slower, faster));

        assertThat(board).extracting(LeaderboardEntry::displayName).containsExactly("Faster", "Slower");
    }

    @Test
    void breaksRemainingTiesByJoinOrder() {
        Participant second = participant("Second", 0, null);
        Participant first = participant("First", 0, null);

        // roster gives 'first' join order 1, 'second' join order 2
        List<LeaderboardEntry> board = leaderboardService.rank(
                List.of(second, first), roster(first, second));

        assertThat(board).extracting(LeaderboardEntry::displayName).containsExactly("First", "Second");
    }

    private static Participant participant(String name, int score, Instant answeredAt) {
        Participant participant = Participant.guest(SESSION_ID, GuestParticipantToken.generate(), name, EN);
        if (answeredAt != null) {
            participant.recordAnswer(new ParticipantAnswer(
                    UUID.randomUUID(), Set.of(UUID.randomUUID()), EN, answeredAt, 1000, score));
        }
        return participant;
    }

    private static List<SessionRosterEntry> roster(Participant... participantsInJoinOrder) {
        SessionRosterEntry[] entries = new SessionRosterEntry[participantsInJoinOrder.length];
        for (int index = 0; index < participantsInJoinOrder.length; index++) {
            entries[index] = new SessionRosterEntry(participantsInJoinOrder[index].getId(),
                    participantsInJoinOrder[index].key(), index + 1);
        }
        return List.of(entries);
    }
}
