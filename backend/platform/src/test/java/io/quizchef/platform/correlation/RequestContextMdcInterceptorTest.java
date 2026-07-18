package io.quizchef.platform.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.common.correlation.CorrelationKeys;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestContextMdcInterceptorTest {

    private final RequestContextMdcInterceptor interceptor = new RequestContextMdcInterceptor();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsKnownPathVariablesIntoMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("id", "session-1", "quizId", "quiz-1", "questionId", "question-1"));

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(MDC.get(CorrelationKeys.SESSION_ID_MDC_KEY)).isEqualTo("session-1");
        assertThat(MDC.get(CorrelationKeys.QUIZ_ID_MDC_KEY)).isEqualTo("quiz-1");
        assertThat(MDC.get(CorrelationKeys.QUESTION_ID_MDC_KEY)).isEqualTo("question-1");
    }

    @Test
    void ignoresUnknownPathVariables() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("pin", "123456"));

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(MDC.get(CorrelationKeys.SESSION_ID_MDC_KEY)).isNull();
    }

    @Test
    void afterCompletionRemovesEveryKnownKey() {
        MDC.put(CorrelationKeys.SESSION_ID_MDC_KEY, "leftover-session");
        MDC.put(CorrelationKeys.QUIZ_ID_MDC_KEY, "leftover-quiz");

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(MDC.get(CorrelationKeys.SESSION_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(CorrelationKeys.QUIZ_ID_MDC_KEY)).isNull();
    }
}
