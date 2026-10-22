package io.github.adetlo.payments.infrastructure.persistence;

import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for the payment aggregate. */
public interface PaymentRepository
        extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    /**
     * Claims due retries with a row lock so concurrent scheduler instances never double-process.
     */
    @Query(
            value =
                    "SELECT * FROM payments WHERE status = 'RETRY_PENDING' AND next_retry_at <="
                            + " :now ORDER BY next_retry_at LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Payment> findDueRetriesForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    @Query("SELECT p.status AS status, COUNT(p) AS count FROM Payment p GROUP BY p.status")
    List<StatusCount> countByStatusGrouped();

    List<Payment> findByStatusInOrderByCreatedAtAscPaymentIdAsc(List<PaymentStatus> statuses);

    long countByIdempotencyKey(String idempotencyKey);

    /** Projection for {@link #countByStatusGrouped()}. */
    interface StatusCount {
        PaymentStatus getStatus();

        long getCount();
    }
}
