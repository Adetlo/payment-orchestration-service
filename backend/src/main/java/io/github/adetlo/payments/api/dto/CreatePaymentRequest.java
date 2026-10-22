package io.github.adetlo.payments.api.dto;

import io.github.adetlo.payments.domain.AccountReference;
import io.github.adetlo.payments.domain.PaymentInstruction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/payments}. */
public record CreatePaymentRequest(
        @NotBlank
                @Pattern(
                        regexp = AccountReference.PATTERN,
                        message = "must be a synthetic IBAN-like reference")
                String payerAccount,
        @NotBlank
                @Pattern(
                        regexp = AccountReference.PATTERN,
                        message = "must be a synthetic IBAN-like reference")
                String payeeAccount,
        @NotBlank
                @Pattern(
                        regexp = "^[0-9]{1,13}(\\.[0-9]{1,4})?$",
                        message = "must be a positive decimal string such as 125.50")
                String amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank
                @Pattern(
                        regexp = PaymentInstruction.REFERENCE_PATTERN,
                        message = "must be 1..35 characters of [A-Za-z0-9 ./-]")
                String reference) {}
