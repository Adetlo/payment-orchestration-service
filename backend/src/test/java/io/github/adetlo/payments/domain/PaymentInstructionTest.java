package io.github.adetlo.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentInstructionTest {

    private static final AccountReference PAYER = new AccountReference("GB12PAYLAB00000001");
    private static final AccountReference PAYEE = new AccountReference("GB12PAYLAB00000002");

    @Test
    void buildsValidInstruction() {
        PaymentInstruction instruction =
                new PaymentInstruction(PAYER, PAYEE, Money.of("10.00", "GBP"), "INV-1001 ./-");

        assertThat(instruction.reference()).isEqualTo("INV-1001 ./-");
        assertThat(instruction.payerEqualsPayee()).isFalse();
        assertThat(
                        new PaymentInstruction(PAYER, PAYER, Money.of("10.00", "GBP"), "X")
                                .payerEqualsPayee())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "INV_1001", "INV#1", "123456789012345678901234567890123456"})
    void rejectsInvalidReferences(String reference) {
        assertThatThrownBy(
                        () ->
                                new PaymentInstruction(
                                        PAYER, PAYEE, Money.of("10.00", "GBP"), reference))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("reference");
    }

    @Test
    void rejectsNullReferenceAndMissingParts() {
        Money money = Money.of("10.00", "GBP");

        assertThatThrownBy(() -> new PaymentInstruction(PAYER, PAYEE, money, null))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new PaymentInstruction(null, PAYEE, money, "X"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentInstruction(PAYER, PAYEE, null, "X"))
                .isInstanceOf(NullPointerException.class);
    }
}
