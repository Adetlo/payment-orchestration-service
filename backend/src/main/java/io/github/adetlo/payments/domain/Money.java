package io.github.adetlo.payments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * An exact monetary amount in a supported currency. The amount is always carried at the scale of
 * the currency (2 for GBP/EUR/USD/SGD, 0 for JPY/IDR); amounts that would lose precision when
 * brought to that scale are rejected ({@link RoundingMode#UNNECESSARY}).
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    /** Maximum amount accepted by the demo business rules, in major units. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    private static final Map<String, Integer> SCALES =
            Map.of("GBP", 2, "EUR", 2, "USD", 2, "SGD", 2, "JPY", 0, "IDR", 0);

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int scale = scaleFor(currency);
        try {
            amount = amount.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new DomainValidationException(
                    "amount",
                    "amount "
                            + amount.toPlainString()
                            + " must have at most "
                            + scale
                            + " decimal places for "
                            + currency.getCurrencyCode());
        }
    }

    /** Parses the API representation (decimal string + ISO-4217 code). */
    public static Money of(String amount, String currencyCode) {
        BigDecimal value;
        try {
            value = new BigDecimal(Objects.requireNonNull(amount, "amount").trim());
        } catch (NumberFormatException e) {
            throw new DomainValidationException("amount", "amount must be a decimal number");
        }
        return new Money(value, currencyOf(currencyCode));
    }

    /** Resolves a supported currency or fails with a validation error. */
    public static Currency currencyOf(String currencyCode) {
        String code = Objects.requireNonNull(currencyCode, "currencyCode").trim().toUpperCase();
        if (!SCALES.containsKey(code)) {
            throw new DomainValidationException(
                    "currency",
                    "currency "
                            + currencyCode
                            + " is not supported ("
                            + supportedCurrencies()
                            + ")");
        }
        return Currency.getInstance(code);
    }

    /** Scale (number of minor-unit digits) used for the given currency. */
    public static int scaleFor(Currency currency) {
        Integer scale = SCALES.get(currency.getCurrencyCode());
        if (scale == null) {
            throw new DomainValidationException(
                    "currency", "currency " + currency.getCurrencyCode() + " is not supported");
        }
        return scale;
    }

    /** The ISO codes this service accepts, sorted. */
    public static Set<String> supportedCurrencies() {
        return new TreeSet<>(SCALES.keySet());
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean exceeds(BigDecimal limit) {
        return amount.compareTo(limit) > 0;
    }

    /** The smallest representable step for this currency (e.g. 0.01 for GBP, 1 for JPY). */
    public Money smallestUnit() {
        return new Money(BigDecimal.ONE.movePointLeft(amount.scale()), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currencyCode() + " vs " + other.currencyCode());
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
