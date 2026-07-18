package io.quizchef.platform.correlation;

import io.quizchef.common.correlation.CorrelationKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Adds the resource ids a request is about — session, quiz, question — to
 * MDC, purely from the path variables Spring MVC already resolved. This is
 * an MVC-layer concern only: no application service or domain method is
 * touched to make a log line searchable by "which session" (RFC-010).
 */
public class RequestContextMdcInterceptor implements HandlerInterceptor {

    /**
     * Every session-scoped endpoint in this codebase names its path
     * variable {@code id} (e.g. {@code /sessions/{id}/answers}); quiz and
     * question endpoints name theirs explicitly. If a future controller
     * introduces another {@code id}-named resource, this mapping needs a
     * second look rather than silently mislabeling it.
     */
    private static final Map<String, String> PATH_VARIABLE_TO_MDC_KEY = Map.of(
            "id", CorrelationKeys.SESSION_ID_MDC_KEY,
            "sessionId", CorrelationKeys.SESSION_ID_MDC_KEY,
            "quizId", CorrelationKeys.QUIZ_ID_MDC_KEY,
            "questionId", CorrelationKeys.QUESTION_ID_MDC_KEY
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        pathVariables(request).forEach((name, value) -> {
            String mdcKey = PATH_VARIABLE_TO_MDC_KEY.get(name);
            if (mdcKey != null) {
                MDC.put(mdcKey, value);
            }
        });
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        PATH_VARIABLE_TO_MDC_KEY.values().forEach(MDC::remove);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> pathVariables(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return attribute instanceof Map<?, ?> variables ? (Map<String, String>) variables : Map.of();
    }
}
