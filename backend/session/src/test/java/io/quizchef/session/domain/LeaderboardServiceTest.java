package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.quizchef.quiz.domain.LanguageCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    @Test
    void ranksTheBoardAsItStoodBeforeAGivenQuestion() {
        UUID earlier = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        Instant at = Instant.parse("2026-07-16T10:00:10Z");
        Participant leader = participant("Leader", Map.of(earlier, 900), at);
        Participant chaser = participant("Chaser", Map.of(earlier, 400, current, 1000), at);

        assertThat(leaderboardService.rank(List.of(leader, chaser), roster(leader, chaser)))
                .extracting(LeaderboardEntry::displayName, LeaderboardEntry::score)
                .containsExactly(tuple("Chaser", 1400), tuple("Leader", 900));

        // The same two players one question earlier: the current question's
        // answer simply does not count, so the board is the real earlier
        // one rather than today's with a number taken off it.
        assertThat(leaderboardService.rankBefore(List.of(leader, chaser), roster(leader, chaser), current))
                .extracting(LeaderboardEntry::displayName, LeaderboardEntry::score, LeaderboardEntry::rank)
                .containsExactly(tuple("Leader", 900, 1), tuple("Chaser", 400, 2));
    }

    @Test
    void beforeBoardBreaksTiesOnTheEarlierAnswerTimesToo() {
        UUID earlier = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        // Two players level on score, before and after. Only the *times*
        // separate them, and the current question moves them: 'Quick' was
        // ahead on the previous board (answered at :10 to 'Steady's :30)
        // but falls behind on this one (a late, pointless answer at :40).
        // The two boards therefore order these two differently — which is
        // exactly why a previous rank recovered by arithmetic on the
        // current board would be wrong here.
        Participant quick = participant("Quick", Map.of(earlier, 500),
                Instant.parse("2026-07-16T10:00:10Z"));
        quick.recordAnswer(new ParticipantAnswer(current, Set.of(UUID.randomUUID()), EN,
                Instant.parse("2026-07-16T10:00:40Z"), 1000, 0));
        Participant steady = participant("Steady", Map.of(earlier, 500),
                Instant.parse("2026-07-16T10:00:30Z"));

        assertThat(leaderboardService.rankBefore(List.of(quick, steady), roster(quick, steady), current))
                .extracting(LeaderboardEntry::displayName)
                .containsExactly("Quick", "Steady");
        assertThat(leaderboardService.rank(List.of(quick, steady), roster(quick, steady)))
                .extracting(LeaderboardEntry::displayName)
                .containsExactly("Steady", "Quick");
    }

    private static Participant participant(String name, Map<UUID, Integer> pointsByQuestion,
                                           Instant answeredAt) {
        Participant participant = Participant.guest(SESSION_ID, GuestParticipantToken.generate(), name, EN);
        pointsByQuestion.forEach((questionId, points) -> participant.recordAnswer(new ParticipantAnswer(
                questionId, Set.of(UUID.randomUUID()), EN, answeredAt, 1000, points)));
        return participant;
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
