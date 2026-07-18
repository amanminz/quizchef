package io.quizchef.platform.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.common.correlation.CorrelationKeys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesACorrelationIdWhenNoneSupplied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sessions/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, Mockito.mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationKeys.CORRELATION_ID_HEADER)).isNotBlank();
    }

    @Test
    void reusesASuppliedCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sessions/123");
        request.addHeader(CorrelationKeys.CORRELATION_ID_HEADER, "caller-supplied-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, Mockito.mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationKeys.CORRELATION_ID_HEADER)).isEqualTo("caller-supplied-id");
    }

    @Test
    void mdcIsPopulatedDuringTheChainAndClearedAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sessions/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] capturedCorrelationId = new String[1];
        String[] capturedRequestId = new String[1];
        FilterChain chain = (req, res) -> {
            capturedCorrelationId[0] = MDC.get(CorrelationKeys.CORRELATION_ID_MDC_KEY);
            capturedRequestId[0] = MDC.get(CorrelationKeys.REQUEST_ID_MDC_KEY);
        };

        filter.doFilter(request, response, chain);

        assertThat(capturedCorrelationId[0]).isNotBlank();
        assertThat(capturedRequestId[0]).isNotBlank();
        assertThat(MDC.get(CorrelationKeys.CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(CorrelationKeys.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void everyRequestGetsItsOwnRequestIdEvenWithTheSameCorrelationId() throws Exception {
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/v1/sessions/123");
        first.addHeader(CorrelationKeys.CORRELATION_ID_HEADER, "shared-correlation-id");
        String[] firstRequestId = new String[1];
        filter.doFilter(first, new MockHttpServletResponse(),
                (req, res) -> firstRequestId[0] = MDC.get(CorrelationKeys.REQUEST_ID_MDC_KEY));

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/v1/sessions/123");
        second.addHeader(CorrelationKeys.CORRELATION_ID_HEADER, "shared-correlation-id");
        String[] secondRequestId = new String[1];
        filter.doFilter(second, new MockHttpServletResponse(),
                (req, res) -> secondRequestId[0] = MDC.get(CorrelationKeys.REQUEST_ID_MDC_KEY));

        assertThat(firstRequestId[0]).isNotEqualTo(secondRequestId[0]);
    }
}
