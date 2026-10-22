package io.github.adetlo.payments.infrastructure.persistence;

import io.github.adetlo.payments.domain.ReconciliationRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for reconciliation runs. */
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    Optional<ReconciliationRun> findByRunId(UUID runId);

    Optional<ReconciliationRun> findFirstByOrderByStartedAtDescIdDesc();
}
