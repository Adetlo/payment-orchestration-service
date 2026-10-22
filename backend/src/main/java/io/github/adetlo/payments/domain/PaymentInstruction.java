package io.github.adetlo.payments.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** What the client asked us to do: move {@code money} from payer to payee under a reference. */
public record PaymentInstruction(
        AccountReference payerAccount,
        AccountReference payeeAccount,
        Money money,
        String reference) {

    public static final String REFERENCE_PATTERN = "^[A-Za-z0-9 ./-]{1,35}$";
    private static final Pattern COMPILED = Pattern.compile(REFERENCE_PATTERN);

    public PaymentInstruction {
        Objects.requireNonNull(payerAccount, "payerAccount");
        Objects.requireNonNull(payeeAccount, "payeeAccount");
        Objects.requireNonNull(money, "money");
        if (reference == null || !COMPILED.matcher(reference).matches()) {
            throw new DomainValidationException(
                    "reference", "reference must be 1..35 characters of [A-Za-z0-9 ./-]");
        }
    }

    public boolean payerEqualsPayee() {
        return payerAccount.equals(payeeAccount);
    }
}
