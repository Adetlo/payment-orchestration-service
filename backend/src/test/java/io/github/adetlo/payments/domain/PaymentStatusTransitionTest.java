package io.github.adetlo.payments.domain;

import static io.github.adetlo.payments.domain.PaymentStatus.CANCELLED;
import static io.github.adetlo.payments.domain.PaymentStatus.COMPLETED;
import static io.github.adetlo.payments.domain.PaymentStatus.FAILED;
import static io.github.adetlo.payments.domain.PaymentStatus.PROCESSING;
import static io.github.adetlo.payments.domain.PaymentStatus.RECEIVED;
import static io.github.adetlo.payments.domain.PaymentStatus.REJECTED;
import static io.github.adetlo.payments.domain.PaymentStatus.RETRY_PENDING;
import static io.github.adetlo.payments.domain.PaymentStatus.VALIDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.adetlo.payments.application.PaymentStateMachine;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** The explicit transition table from DESIGN.md section 4.3. */
class PaymentStatusTransitionTest {

    static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(RECEIVED, VALIDATED),
                Arguments.of(RECEIVED, REJECTED),
                Arguments.of(RECEIVED, CANCELLED),
                Arguments.of(VALIDATED, PROCESSING),
                Arguments.of(VALIDATED, CANCELLED),
                Arguments.of(PROCESSING, COMPLETED),
                Arguments.of(PROCESSING, RETRY_PENDING),
                Arguments.of(PROCESSING, FAILED),
                Arguments.of(RETRY_PENDING, PROCESSING),
                Arguments.of(RETRY_PENDING, FAILED),
                Arguments.of(RETRY_PENDING, CANCELLED));
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(RECEIVED, PROCESSING),
                Arguments.of(RECEIVED, COMPLETED),
                Arguments.of(VALIDATED, COMPLETED),
                Arguments.of(VALIDATED, REJECTED),
                Arguments.of(PROCESSING, CANCELLED),
                Arguments.of(PROCESSING, VALIDATED),
                Arguments.of(RETRY_PENDING, COMPLETED),
                Arguments.of(COMPLETED, FAILED),
                Arguments.of(FAILED, PROCESSING),
                Arguments.of(CANCELLED, RECEIVED),
                Arguments.of(REJECTED, VALIDATED));
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @MethodSource("validTransitions")
    void allowsTransitionsFromTheTable(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
        PaymentStateMachine.assertAllowed(UUID.randomUUID(), from, to);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @MethodSource("invalidTransitions")
    void rejectsTransitionsOutsideTheTable(PaymentStatus from, PaymentStatus to) {
        UUID paymentId = UUID.randomUUID();

        assertThat(from.canTransitionTo(to)).isFalse();
        assertThatThrownBy(() -> PaymentStateMachine.assertAllowed(paymentId, from, to))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name())
                .satisfies(
                        e -> {
                            InvalidStateTransitionException ex =
                                    (InvalidStateTransitionException) e;
                            assertThat(ex.paymentId()).isEqualTo(paymentId);
                            assertThat(ex.from()).isEqualTo(from);
                            assertThat(ex.to()).isEqualTo(to);
                        });
    }

    @ParameterizedTest(name = "{0} is terminal")
    @EnumSource(
            value = PaymentStatus.class,
            names = {"COMPLETED", "REJECTED", "FAILED", "CANCELLED"})
    void terminalStatusesHaveNoOutgoingTransitions(PaymentStatus status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.allowedTransitions()).isEmpty();
        assertThat(status.isCancellable()).isFalse();
    }

    @Test
    void tableMatchesTheDesignExactly() {
        assertThat(RECEIVED.allowedTransitions())
                .isEqualTo(EnumSet.of(VALIDATED, REJECTED, CANCELLED));
        assertThat(VALIDATED.allowedTransitions()).isEqualTo(EnumSet.of(PROCESSING, CANCELLED));
        assertThat(PROCESSING.allowedTransitions())
                .isEqualTo(EnumSet.of(COMPLETED, RETRY_PENDING, FAILED));
        assertThat(RETRY_PENDING.allowedTransitions())
                .isEqualTo(EnumSet.of(PROCESSING, FAILED, CANCELLED));
        assertThat(EnumSet.allOf(PaymentStatus.class).stream().filter(PaymentStatus::isCancellable))
                .containsExactlyInAnyOrder(RECEIVED, VALIDATED, RETRY_PENDING);
    }

    @Test
    void aggregateEnforcesTheTableItself() {
        Payment payment = PaymentFixtures.paymentIn(COMPLETED);

        assertThatThrownBy(() -> payment.transitionTo(FAILED, Instant.now(), "late"))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(payment.status()).isEqualTo(COMPLETED);
    }
}
