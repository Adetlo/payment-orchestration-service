package io.github.adetlo.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0} {1} is carried at scale {2}")
    @CsvSource({
        "GBP, 125.5, 2",
        "EUR, 10, 2",
        "USD, 0.01, 2",
        "SGD, 1000.00, 2",
        "JPY, 1000, 0",
        "IDR, 150000, 0"
    })
    void normalisesToCurrencyScale(String currency, String amount, int scale) {
        Money money = Money.of(amount, currency);

        assertThat(money.amount().scale()).isEqualTo(scale);
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal(amount));
    }

    @ParameterizedTest(name = "{1} {0} is rejected")
    @CsvSource({"GBP, 125.505", "JPY, 100.5", "IDR, 10.01", "EUR, 0.001"})
    void rejectsAmountsThatWouldNeedRounding(String currency, String amount) {
        assertThatThrownBy(() -> Money.of(amount, currency))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("decimal places");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> Money.of("1.00", "XXX"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("not supported");
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, Currency.getInstance("CHF")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsNonNumericAmount() {
        assertThatThrownBy(() -> Money.of("12,50", "GBP"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void trailingZerosBeyondScaleAreAcceptedWithoutLoss() {
        assertThat(Money.of("100.000", "GBP").amount()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void equalityUsesNormalisedScale() {
        assertThat(Money.of("125.5", "GBP")).isEqualTo(Money.of("125.50", "GBP"));
        assertThat(Money.of("125.50", "GBP")).isNotEqualTo(Money.of("125.50", "EUR"));
    }

    @Test
    void arithmeticAndComparisonRequireSameCurrency() {
        Money gbp = Money.of("1.00", "GBP");
        Money eur = Money.of("1.00", "EUR");

        assertThat(gbp.plus(Money.of("0.50", "GBP"))).isEqualTo(Money.of("1.50", "GBP"));
        assertThat(gbp.compareTo(Money.of("2.00", "GBP"))).isNegative();
        assertThatThrownBy(() -> gbp.plus(eur)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gbp.compareTo(eur)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void smallestUnitDependsOnCurrency() {
        assertThat(Money.of("5.00", "GBP").smallestUnit()).isEqualTo(Money.of("0.01", "GBP"));
        assertThat(Money.of("5", "JPY").smallestUnit()).isEqualTo(Money.of("1", "JPY"));
    }

    @Test
    void limitsAndSignChecks() {
        assertThat(Money.of("1000000.00", "GBP").exceeds(Money.MAX_AMOUNT)).isFalse();
        assertThat(Money.of("1000000.01", "GBP").exceeds(Money.MAX_AMOUNT)).isTrue();
        assertThat(Money.of("0.00", "GBP").isPositive()).isFalse();
        assertThat(Money.of("0.01", "GBP").isPositive()).isTrue();
        assertThat(Money.supportedCurrencies())
                .containsExactly("EUR", "GBP", "IDR", "JPY", "SGD", "USD");
        assertThat(Money.of("125.50", "GBP")).hasToString("125.50 GBP");
    }
}
