package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FinalPlacementPolicyTest {

    @ParameterizedTest(name = "{0} participants → ranks 1–{1} revealed")
    @CsvSource({
            // Small rooms: everyone is inside the ceremony's own five places.
            "1, 1",
            "4, 4",
            // Once there are more than five, the five places lead until half
            // the room overtakes them.
            "6, 5",
            "10, 5",
            "11, 6",
            "20, 10"
    })
    void revealsTheCeremonialPlacesOrHalfTheRoom(int total, int expected) {
        assertThat(FinalPlacementPolicy.exactRankRevealCount(board(total))).isEqualTo(expected);
    }

    @Test
    void revealsNobodyInAnEmptyRoom() {
        assertThat(FinalPlacementPolicy.exactRankRevealCount(List.of())).isZero();
    }

    @Test
    void neverSplitsASharedRankAcrossTheCutoff() {
        // A hypothetical ranking that genuinely shares rank 5 across three
        // players. The cutoff would fall at 5 and cut through them; it has
        // to take all three, or one of the three learns their exact
        // position while the players the ranking calls their equals do not.
        List<LeaderboardEntry> ranked = new ArrayList<>();
        for (int rank = 1; rank <= 4; rank++) {
            ranked.add(entry("P" + rank, 1000 - rank, rank));
        }
        ranked.add(entry("Tied A", 500, 5));
        ranked.add(entry("Tied B", 500, 5));
        ranked.add(entry("Tied C", 500, 5));
        ranked.add(entry("Last", 100, 8));

        assertThat(FinalPlacementPolicy.exactRankRevealCount(ranked)).isEqualTo(7);
    }

    @Test
    void expandsOnRanksAndNeverOnEqualScores() {
        // Four players on identical scores, which LeaderboardService breaks
        // into distinct ranks (submission time, then join order). Equal
        // scores are NOT a tie: the cutoff must land on the ordinary 1–4
        // and not swallow the field because the numbers happen to match.
        List<LeaderboardEntry> ranked = List.of(
                entry("A", 900, 1), entry("B", 900, 2),
                entry("C", 900, 3), entry("D", 900, 4),
                entry("E", 900, 5), entry("F", 900, 6),
                entry("G", 900, 7), entry("H", 900, 8),
                entry("I", 900, 9), entry("J", 900, 10),
                entry("K", 900, 11));

        assertThat(FinalPlacementPolicy.exactRankRevealCount(ranked)).isEqualTo(6);
    }

    @Test
    void namesTheTopThreeWinnersAndTheNextTwoRunnersUp() {
        assertThat(FinalPlacementPolicy.labelFor(1)).isEqualTo(FinalPlacementLabel.WINNER);
        assertThat(FinalPlacementPolicy.labelFor(3)).isEqualTo(FinalPlacementLabel.WINNER);
        assertThat(FinalPlacementPolicy.labelFor(4)).isEqualTo(FinalPlacementLabel.RUNNER_UP);
        assertThat(FinalPlacementPolicy.labelFor(5)).isEqualTo(FinalPlacementLabel.RUNNER_UP);
        // Everyone else inside the reveal group finished the quiz — the word
        // says that, and nothing about how far back they came.
        assertThat(FinalPlacementPolicy.labelFor(6)).isEqualTo(FinalPlacementLabel.FINALIST);
        assertThat(FinalPlacementPolicy.labelFor(10)).isEqualTo(FinalPlacementLabel.FINALIST);
    }

    @Test
    void placesTheCutoffInclusively() {
        assertThat(FinalPlacementPolicy.revealsExactRank(10, 10)).isTrue();
        assertThat(FinalPlacementPolicy.revealsExactRank(11, 10)).isFalse();
    }

    private static List<LeaderboardEntry> board(int size) {
        List<LeaderboardEntry> ranked = new ArrayList<>();
        for (int rank = 1; rank <= size; rank++) {
            ranked.add(entry("P" + rank, 1000 - rank, rank));
        }
        return ranked;
    }

    private static LeaderboardEntry entry(String name, int score, int rank) {
        return new LeaderboardEntry(UUID.randomUUID(), name, score, rank);
    }
}
