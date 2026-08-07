package io.quizchef.session.domain;

import java.util.List;

/**
 * Who learns their exact finishing position, and what it is called.
 *
 * <p>The product rule: a quiz should reward the front of the field
 * without telling everyone else exactly how far back they came. So the
 * standings split in two. The <em>reveal group</em> — the podium plus the
 * top half — gets exact ranks, on the projector and on their own phones.
 * Everyone else gets their score and who they finished near, and never a
 * number that says "last".
 *
 * <p>The cutoff is
 * {@code max(min(5, total), ceil(total / 2))}: at least the five
 * ceremonial places (or everyone, in a room smaller than five), and at
 * least half the room once half the room is more than five. Worked
 * through:
 *
 * <pre>
 * participants | exact ranks | relative-only
 *            1 |           1 |             0   they are simply first
 *            4 |           4 |             0   min(5,4)=4 beats ceil(4/2)=2
 *            6 |           5 |             1   min(5,6)=5 beats ceil(6/2)=3
 *           10 |           5 |             5   equal at 5, so 5
 *           11 |           6 |             5   ceil(11/2)=6 beats 5
 *           20 |          10 |            10   ceil(20/2)=10 beats 5
 * </pre>
 *
 * <p>Note the small rooms: at four participants everyone is inside the
 * group, so fourth place does learn they came fourth. That is intended —
 * concealing a position among four people offers no practical anonymity,
 * since everyone can see who is in the room.
 *
 * <p>This is domain policy, not presentation: the same number decides
 * what the projector renders and what each participant's own device is
 * told, so the two can never disagree about where the line falls.
 */
public final class FinalPlacementPolicy {

    /** The places the winner ceremony gives their own moment to. */
    private static final int CEREMONIAL_PLACES = 5;

    private FinalPlacementPolicy() {
    }

    /**
     * How many of the ranked standings may show their exact position.
     *
     * <p>{@code ranked} must be the ranking service's own output, in its
     * order. If the cutoff would fall in the middle of a shared rank, the
     * whole of that rank is included — nobody learns their exact position
     * while someone the ranking calls their equal does not.
     *
     * <p>That expansion is written against <em>ranks</em>, never scores.
     * Two equal scores are not a tie: {@link LeaderboardService} breaks
     * every tie (submission time, then join order) and currently emits
     * dense, unique ranks, so in practice this loop never expands
     * anything. It is here so the rule stays correct rather than
     * accidentally correct — if the ranking ever does share a rank, the
     * cutoff already handles it, and no caller has to remember to.
     */
    public static int exactRankRevealCount(List<LeaderboardEntry> ranked) {
        int total = ranked.size();
        if (total == 0) {
            return 0;
        }
        int count = Math.max(Math.min(CEREMONIAL_PLACES, total), (total + 1) / 2);
        while (count < total && ranked.get(count).rank() == ranked.get(count - 1).rank()) {
            count++;
        }
        return count;
    }

    /**
     * Whether this rank is inside the reveal group, given the cutoff.
     * Positions are 1-based; the cutoff is a count.
     */
    public static boolean revealsExactRank(int rank, int exactRankRevealCount) {
        return rank <= exactRankRevealCount;
    }

    /**
     * What to call a revealed position. Only ever asked about a rank
     * inside the reveal group — the point of the split is that nobody
     * outside it is labelled at all.
     */
    public static FinalPlacementLabel labelFor(int rank) {
        if (rank <= 3) {
            return FinalPlacementLabel.WINNER;
        }
        if (rank <= CEREMONIAL_PLACES) {
            return FinalPlacementLabel.RUNNER_UP;
        }
        return FinalPlacementLabel.FINALIST;
    }
}
