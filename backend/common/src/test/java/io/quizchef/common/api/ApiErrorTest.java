package io.quizchef.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.quizchef.common.correlation.CorrelationKeys;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ApiErrorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void ofCarriesTheCurrentCorrelationId() {
        MDC.put(CorrelationKeys.CORRELATION_ID_MDC_KEY, "corr-123");

        ApiError error = ApiError.of("some.code", "Something happened");

        assertThat(error.correlationId()).isEqualTo("corr-123");
    }

    @Test
    void validationCarriesTheCurrentCorrelationId() {
        MDC.put(CorrelationKeys.CORRELATION_ID_MDC_KEY, "corr-456");

        ApiError error = ApiError.validation("Request validation failed",
                List.of(new ApiFieldError("field", "must not be blank")));

        assertThat(error.correlationId()).isEqualTo("corr-456");
    }

    @Test
    void correlationIdIsNullOutsideARequestThread() {
        ApiError error = ApiError.of("some.code", "Something happened");

        assertThat(error.correlationId()).isNull();
    }
}
