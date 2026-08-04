package io.quizchef.session.domain;

/**
 * What a revealed finishing position is called: the top three are
 * Winners, fourth and fifth are Runners-up, and everyone else inside the
 * reveal group is a Finalist — a word that says "you finished the quiz",
 * not "you nearly won".
 *
 * <p>Only ever assigned to a position inside the reveal group
 * ({@link FinalPlacementPolicy}); the participants outside it are given
 * their score and their neighbours, not a label.
 */
public enum FinalPlacementLabel {
    WINNER,
    RUNNER_UP,
    FINALIST
}
