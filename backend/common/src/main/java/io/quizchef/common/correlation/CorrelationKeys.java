package io.quizchef.common.correlation;

/**
 * The one place request-correlation identifiers are named.
 *
 * <p>Every module that needs to read or write these already depends on
 * {@code common}, so no observability-specific dependency has to spread
 * beyond it. Values are SLF4J {@link org.slf4j.MDC} keys — correlation
 * propagates by riding the request thread, never by threading a parameter
 * through application or domain method signatures (Phase 3 PR #2,
 * RFC-010).
 */
public final class CorrelationKeys {

    /** Correlation id: reused across a client's retries, one request or many. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Request id: unique to exactly one HTTP request, even retries get a new one. */
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /** The authenticated identity making the request, when there is one. */
    public static final String IDENTITY_ID_MDC_KEY = "identityId";

    public static final String SESSION_ID_MDC_KEY = "sessionId";

    public static final String QUIZ_ID_MDC_KEY = "quizId";

    public static final String QUESTION_ID_MDC_KEY = "questionId";

    /** The HTTP header a caller may supply, and that the server always echoes back. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private CorrelationKeys() {
    }
}
