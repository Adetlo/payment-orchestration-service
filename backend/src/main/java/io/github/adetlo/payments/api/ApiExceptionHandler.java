package io.github.adetlo.payments.api;

import io.github.adetlo.payments.api.web.PayloadTooLargeException;
import io.github.adetlo.payments.api.web.RateLimitExceededException;
import io.github.adetlo.payments.application.PaymentNotFoundException;
import io.github.adetlo.payments.application.ReconciliationRunNotFoundException;
import io.github.adetlo.payments.application.idempotency.IdempotencyInProgressException;
import io.github.adetlo.payments.application.idempotency.IdempotencyKeyReuseException;
import io.github.adetlo.payments.domain.DomainValidationException;
import io.github.adetlo.payments.domain.InvalidStateTransitionException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every failure to an RFC 7807 problem. Internal details and stack traces never leave the
 * service: unexpected exceptions are logged with their trace and answered with a generic 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_PREFIX = "about:blank#";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, String>> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                e ->
                                        Map.of(
                                                "field",
                                                e.getField(),
                                                "message",
                                                String.valueOf(e.getDefaultMessage())))
                        .toList();
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "validation-error",
                        "Validation failed",
                        "Request validation failed");
        problem.setProperty("errors", errors);
        return respond(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "malformed-request",
                        "Malformed request",
                        "Request body could not be read as JSON");
        return respond(problem, headers);
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ProblemDetail> handleDomainValidation(DomainValidationException ex) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "validation-error",
                        "Validation failed",
                        "Request validation failed");
        problem.setProperty(
                "errors", List.of(Map.of("field", ex.field(), "message", ex.getMessage())));
        return respond(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex) {
        List<Map<String, String>> errors =
                ex.getConstraintViolations().stream()
                        .map(
                                v ->
                                        Map.of(
                                                "field",
                                                String.valueOf(v.getPropertyPath()),
                                                "message",
                                                v.getMessage()))
                        .toList();
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "validation-error",
                        "Validation failed",
                        "Request validation failed");
        problem.setProperty("errors", errors);
        return respond(problem);
    }

    @ExceptionHandler({PaymentNotFoundException.class, ReconciliationRunNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex) {
        return respond(problem(HttpStatus.NOT_FOUND, "not-found", "Not found", ex.getMessage()));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransition(
            InvalidStateTransitionException ex) {
        ProblemDetail problem =
                problem(
                        HttpStatus.CONFLICT,
                        "invalid-state-transition",
                        "Invalid state transition",
                        ex.getMessage());
        problem.setProperty("paymentId", ex.paymentId().toString());
        problem.setProperty("fromStatus", ex.from().name());
        problem.setProperty("toStatus", ex.to().name());
        return respond(problem);
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyReuse(IdempotencyKeyReuseException ex) {
        return respond(
                problem(
                        HttpStatus.CONFLICT,
                        "idempotency-key-reuse",
                        "Idempotency key reused",
                        ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyInProgress(
            IdempotencyInProgressException ex) {
        ProblemDetail problem =
                problem(
                        HttpStatus.CONFLICT,
                        "idempotency-request-in-progress",
                        "Idempotent request in progress",
                        ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockingFailureException ex) {
        return respond(
                problem(
                        HttpStatus.CONFLICT,
                        "concurrent-modification",
                        "Concurrent modification",
                        "The payment was modified concurrently; retry the request"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
        ProblemDetail problem =
                problem(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "rate-limit-exceeded",
                        "Too many requests",
                        ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(
                        HttpHeaders.RETRY_AFTER,
                        Long.toString(Math.max(1, ex.retryAfter().toSeconds())))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ProblemDetail> handlePayloadTooLarge(PayloadTooLargeException ex) {
        return respond(
                problem(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "payload-too-large",
                        "Payload too large",
                        ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(
                problem(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "internal-error",
                        "Internal error",
                        "An unexpected error occurred"));
    }

    private static ProblemDetail problem(
            HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static ResponseEntity<Object> respond(ProblemDetail problem, HttpHeaders headers) {
        return ResponseEntity.status(problem.getStatus())
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
