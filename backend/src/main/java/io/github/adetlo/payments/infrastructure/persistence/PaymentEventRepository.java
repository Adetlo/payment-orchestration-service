package io.github.adetlo.payments.infrastructure.persistence;

import io.github.adetlo.payments.domain.PaymentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for payment history events. */
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    List<PaymentEvent> findByPaymentIdOrderByOccurredAtAscIdAsc(UUID paymentId);

    long countByPaymentId(UUID paymentId);
}
