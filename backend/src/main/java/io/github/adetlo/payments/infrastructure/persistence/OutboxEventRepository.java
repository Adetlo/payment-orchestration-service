package io.github.adetlo.payments.infrastructure.persistence;

import io.github.adetlo.payments.domain.OutboxEvent;
import io.github.adetlo.payments.domain.OutboxStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for the transactional outbox. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Claims a batch of publishable rows; competing publishers skip locked rows. */
    @Query(
            value =
                    "SELECT * FROM outbox_events WHERE status = 'NEW' AND next_attempt_at <= :now"
                            + " ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("now") Instant now, @Param("limit") int limit);

    long countByStatus(OutboxStatus status);

    List<OutboxEvent> findByAggregateIdOrderByIdAsc(String aggregateId);
}
