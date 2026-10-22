package io.github.adetlo.payments.support;

import io.github.adetlo.payments.application.CreatePaymentCommand;
import io.github.adetlo.payments.domain.AccountReference;
import io.github.adetlo.payments.domain.DemoFailureMode;
import io.github.adetlo.payments.domain.Money;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentInstruction;
import io.github.adetlo.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reusable request bodies, commands and domain objects for tests. */
public final class PaymentFixtures {

    public static final String PAYER = "GB12PAYLAB00000001";
    public static final String PAYEE = "GB12PAYLAB00000002";
    public static final String CORRELATION_ID = "corr-test";
    public static final int MAX_RETRIES = 3;

    private PaymentFixtures() {}

    /** A valid create request body. */
    public static Map<String, Object> createRequest() {
        return createRequest("125.50", "GBP");
    }

    public static Map<String, Object> createRequest(String amount, String currency) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payerAccount", PAYER);
        body.put("payeeAccount", PAYEE);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("reference", "INV-" + uniqueSuffix());
        return body;
    }

    public static Map<String, Object> createRequestWith(String field, Object value) {
        Map<String, Object> body = createRequest();
        body.put(field, value);
        return body;
    }

    public static CreatePaymentCommand command() {
        return command(null, null);
    }

    public static CreatePaymentCommand command(String idempotencyKey, String demoFailure) {
        return new CreatePaymentCommand(
                PAYER,
                PAYEE,
                "125.50",
                "GBP",
                "INV-" + uniqueSuffix(),
                idempotencyKey,
                demoFailure,
                CORRELATION_ID);
    }

    public static PaymentInstruction instruction() {
        return instruction("125.50", "GBP");
    }

    public static PaymentInstruction instruction(String amount, String currency) {
        return new PaymentInstruction(
                new AccountReference(PAYER),
                new AccountReference(PAYEE),
                Money.of(amount, currency),
                "INV-1001");
    }

    /** A fresh RECEIVED payment. */
    public static Payment payment() {
        return payment("125.50", "GBP", null);
    }

    public static Payment payment(String amount, String currency, DemoFailureMode mode) {
        return Payment.create(
                instruction(amount, currency),
                null,
                CORRELATION_ID,
                mode,
                MAX_RETRIES,
                Instant.now());
    }

    /** A payment walked through the valid path to {@code status} using domain transitions only. */
    public static Payment paymentIn(PaymentStatus status) {
        return paymentIn(status, "125.50", "GBP");
    }

    public static Payment paymentIn(PaymentStatus status, String amount, String currency) {
        Payment payment = payment(amount, currency, null);
        Instant now = Instant.now();
        for (PaymentStatus step : pathTo(status)) {
            payment.transitionTo(
                    step, now, step == PaymentStatus.FAILED ? "fixture failure" : null);
        }
        return payment;
    }

    /** The valid transition path from RECEIVED to the given status. */
    public static List<PaymentStatus> pathTo(PaymentStatus status) {
        return switch (status) {
            case RECEIVED -> List.of();
            case VALIDATED -> List.of(PaymentStatus.VALIDATED);
            case PROCESSING -> List.of(PaymentStatus.VALIDATED, PaymentStatus.PROCESSING);
            case COMPLETED ->
                    List.of(
                            PaymentStatus.VALIDATED,
                            PaymentStatus.PROCESSING,
                            PaymentStatus.COMPLETED);
            case FAILED ->
                    List.of(
                            PaymentStatus.VALIDATED,
                            PaymentStatus.PROCESSING,
                            PaymentStatus.FAILED);
            case RETRY_PENDING ->
                    List.of(
                            PaymentStatus.VALIDATED,
                            PaymentStatus.PROCESSING,
                            PaymentStatus.RETRY_PENDING);
            case REJECTED -> List.of(PaymentStatus.REJECTED);
            case CANCELLED -> List.of(PaymentStatus.CANCELLED);
        };
    }

    public static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
