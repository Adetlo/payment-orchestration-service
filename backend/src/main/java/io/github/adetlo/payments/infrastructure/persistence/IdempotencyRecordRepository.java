package io.github.adetlo.payments.infrastructure.persistence;

import io.github.adetlo.payments.domain.IdempotencyRecord;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for idempotency records. */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Inserts an IN_PROGRESS record unless the key already exists. Returns 1 when this caller won
     * the key, 0 when another request holds it; the primary key is the only source of truth.
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO idempotency_records"
                            + " (idempotency_key, request_hash, state, created_at, expires_at)"
                            + " VALUES (:key, :hash, 'IN_PROGRESS', :now, :expiresAt)"
                            + " ON CONFLICT (idempotency_key) DO NOTHING",
            nativeQuery = true)
    int insertInProgress(
            @Param("key") String key,
            @Param("hash") String hash,
            @Param("now") Instant now,
            @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
