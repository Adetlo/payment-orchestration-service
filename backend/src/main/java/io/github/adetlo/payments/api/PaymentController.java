package io.github.adetlo.payments.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adetlo.payments.api.dto.CancelPaymentRequest;
import io.github.adetlo.payments.api.dto.CreatePaymentRequest;
import io.github.adetlo.payments.api.dto.PageResponse;
import io.github.adetlo.payments.api.dto.PaymentHistoryResponse;
import io.github.adetlo.payments.api.dto.PaymentResponse;
import io.github.adetlo.payments.api.web.CorrelationIdFilter;
import io.github.adetlo.payments.application.CreatePaymentCommand;
import io.github.adetlo.payments.application.PaymentQuery;
import io.github.adetlo.payments.application.PaymentService;
import io.github.adetlo.payments.application.idempotency.IdempotencyService;
import io.github.adetlo.payments.application.idempotency.IdempotentResult;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Payment endpoints. */
@RestController
@RequestMapping(path = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String DEMO_FAILURE_HEADER = "X-Demo-Failure";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final HistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    public PaymentController(
            PaymentService paymentService,
            IdempotencyService idempotencyService,
            HistoryMapper historyMapper,
            ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.historyMapper = historyMapper;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create a payment (idempotent with Idempotency-Key)")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
                    @Size(min = 1, max = 128)
                    String idempotencyKey,
            @RequestHeader(name = DEMO_FAILURE_HEADER, required = false) String demoFailure,
            @RequestAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE) String correlationId) {
        CreatePaymentCommand command =
                new CreatePaymentCommand(
                        request.payerAccount(),
                        request.payeeAccount(),
                        request.amount(),
                        request.currency(),
                        request.reference(),
                        blankToNull(idempotencyKey),
                        blankToNull(demoFailure),
                        correlationId);
        IdempotentResult result =
                idempotencyService.execute(command, paymentService::create, this::render);
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(result.status())
                        .location(URI.create("/api/v1/payments/" + result.paymentId()))
                        .contentType(MediaType.APPLICATION_JSON);
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(result.body());
    }

    @Operation(summary = "List payments (newest first)")
    @GetMapping
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @Size(min = 3, max = 3) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return PageResponse.of(
                paymentService.list(new PaymentQuery(status, currency, from, to), page, size),
                PaymentResponse::from);
    }

    @Operation(summary = "Get a payment")
    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.get(paymentId));
    }

    @Operation(summary = "Cancel a payment (RECEIVED, VALIDATED or RETRY_PENDING only)")
    @PostMapping("/{paymentId}/cancel")
    public PaymentResponse cancel(
            @PathVariable UUID paymentId,
            @Valid @RequestBody(required = false) CancelPaymentRequest request) {
        return PaymentResponse.from(
                paymentService.cancel(paymentId, request == null ? null : request.reason()));
    }

    @Operation(summary = "Events and audit trail of a payment, oldest first")
    @GetMapping("/{paymentId}/events")
    public PaymentHistoryResponse events(@PathVariable UUID paymentId) {
        return historyMapper.toResponse(paymentService.history(paymentId));
    }

    private String render(Payment payment) {
        try {
            return objectMapper.writeValueAsString(PaymentResponse.from(payment));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise payment response", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
