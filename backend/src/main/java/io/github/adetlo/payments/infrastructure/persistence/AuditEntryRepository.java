package io.github.adetlo.payments.infrastructure.persistence;

import io.github.adetlo.payments.domain.AuditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Append-only repository: deliberately exposes no update or delete operation. */
public interface AuditEntryRepository extends Repository<AuditEntry, Long> {

    AuditEntry save(AuditEntry entry);

    List<AuditEntry> findByPaymentIdOrderByOccurredAtAscIdAsc(UUID paymentId);

    long countByPaymentId(UUID paymentId);

    long countByAction(String action);

    long count();
}
