package com.saryom.jobservice.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageTooFarTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void aPageOffsetPastIntegerMaxIsTheCallersMistake() {
        ProblemDetail problem = handler.handlePageTooFar(new InvalidDataAccessApiUsageException(
                "Page offset exceeds Integer.MAX_VALUE (2147483647)"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("page is too large");
    }

    @Test
    void anyOtherDataLayerMisuseIsStillOurs() {
        var other = new InvalidDataAccessApiUsageException("something else");

        assertThatThrownBy(() -> handler.handlePageTooFar(other)).isSameAs(other);
    }
}
