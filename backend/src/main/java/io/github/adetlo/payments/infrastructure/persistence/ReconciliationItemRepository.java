package io.github.adetlo.payments.infrastructure.persistence;

import io.github.adetlo.payments.domain.ReconciliationItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for reconciliation items. */
public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, Long> {

    List<ReconciliationItem> findByRunIdOrderByIdAsc(UUID runId);
}
