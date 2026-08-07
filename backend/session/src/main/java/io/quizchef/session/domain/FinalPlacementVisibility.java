package io.quizchef.session.domain;

/**
 * How much of their finish a participant is shown.
 *
 * <p>{@code EXACT_RANK} for the reveal group — the podium and the top
 * half — and {@code RELATIVE_ONLY} for everyone else, who get their score
 * and who they finished near instead of a number. The server decides
 * which applies ({@link FinalPlacementPolicy}); a client renders whichever
 * it is handed and is never in a position to compute the other.
 */
public enum FinalPlacementVisibility {
    EXACT_RANK,
    RELATIVE_ONLY
}
