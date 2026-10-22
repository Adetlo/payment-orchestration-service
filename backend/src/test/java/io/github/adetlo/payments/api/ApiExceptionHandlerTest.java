package io.github.adetlo.payments.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.api.web.RateLimitExceededException;
import io.github.adetlo.payments.application.idempotency.IdempotencyInProgressException;
import io.github.adetlo.payments.domain.InvalidStateTransitionException;
import io.github.adetlo.payments.domain.PaymentStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unexpectedExceptionsNeverLeakInternalDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new IllegalStateException("secret database password"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problem.getType()).hasToString("about:blank#internal-error");
        assertThat(problem.getProperties()).isNullOrEmpty();
        assertThat(String.valueOf(problem.getDetail()) + problem.getTitle())
                .doesNotContain("secret");
    }

    @Test
    void invalidTransitionIsA409WithTypedProblem() {
        UUID id = UUID.randomUUID();
        ResponseEntity<ProblemDetail> response =
                handler.handleInvalidTransition(
                        new InvalidStateTransitionException(
                                id, PaymentStatus.COMPLETED, PaymentStatus.CANCELLED));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType()).hasToString("about:blank#invalid-state-transition");
        assertThat(problem.getProperties())
                .containsEntry("fromStatus", "COMPLETED")
                .containsEntry("toStatus", "CANCELLED")
                .containsEntry("paymentId", id.toString());
    }

    @Test
    void inProgressAndRateLimitCarryRetryAfter() {
        ResponseEntity<ProblemDetail> inProgress =
                handler.handleIdempotencyInProgress(new IdempotencyInProgressException("k"));
        ResponseEntity<ProblemDetail> limited =
                handler.handleRateLimit(new RateLimitExceededException(Duration.ofMillis(100)));

        assertThat(inProgress.getStatusCode().value()).isEqualTo(409);
        assertThat(inProgress.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(inProgress.getBody().getType())
                .hasToString("about:blank#idempotency-request-in-progress");
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }
}
