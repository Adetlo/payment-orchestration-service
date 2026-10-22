package io.github.adetlo.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.AuditEntry;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditAppendOnlyIT extends IntegrationTestSupport {

    @Autowired private AuditEntryRepository auditEntryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private AuditEntry persisted() {
        return auditEntryRepository.save(
                AuditEntry.of(
                        UUID.randomUUID(),
                        "test.action",
                        AuditActor.SYSTEM,
                        "{\"k\":\"v\"}",
                        Instant.now()));
    }

    @Test
    void updateOnAuditEntriesIsRejectedByTrigger() {
        AuditEntry entry = persisted();

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE audit_entries SET action = 'tampered' WHERE id = ?",
                                        entry.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT action FROM audit_entries WHERE id = ?",
                                String.class,
                                entry.id()))
                .isEqualTo("test.action");
    }

    @Test
    void deleteOnAuditEntriesIsRejectedByTrigger() {
        AuditEntry entry = persisted();

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "DELETE FROM audit_entries WHERE id = ?", entry.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_entries WHERE id = ?",
                                Long.class,
                                entry.id()))
                .isEqualTo(1L);
    }

    @Test
    void repositoryExposesNoMutationMethods() {
        assertThat(AuditEntryRepository.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("delete") || name.startsWith("update"));
        assertThat(
                        auditEntryRepository.findByPaymentIdOrderByOccurredAtAscIdAsc(
                                persisted().paymentId()))
                .hasSize(1);
    }
}
