package io.quizchef.session.application;

/**
 * Where a question stands in the session the host is running — what the
 * host's own view of the sequence needs to say about each row, and what
 * decides which recoveries are still open to them.
 */
public enum SessionQuestionStatus {

    /** Already asked and scored. Past the point where correcting it is honest. */
    PLAYED,

    /** The question in play. Correcting it replays it; removing it skips to the next. */
    CURRENT,

    /** Not yet reached. Correcting it is silent; removing it simply shortens the quiz. */
    UPCOMING,

    /** Pulled out by the host. Contributes to nothing, but stays visible to them. */
    REMOVED
}
