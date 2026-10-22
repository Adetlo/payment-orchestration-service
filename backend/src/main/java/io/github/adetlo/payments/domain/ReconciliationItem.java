package io.github.adetlo.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One compared record of a reconciliation run. */
@Entity
@Table(name = "reconciliation_items")
public class ReconciliationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "external_reference", length = 64)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 32)
    private ReconciliationClassification classification;

    @Column(name = "internal_amount", precision = 19, scale = 4)
    private BigDecimal internalAmount;

    @Column(name = "external_amount", precision = 19, scale = 4)
    private BigDecimal externalAmount;

    @Column(name = "internal_status", length = 32)
    private String internalStatus;

    @Column(name = "external_status", length = 32)
    private String externalStatus;

    @Column(name = "details", length = 512)
    private String details;

    protected ReconciliationItem() {
        // JPA
    }

    public static ReconciliationItem of(
            UUID runId,
            UUID paymentId,
            String externalReference,
            ReconciliationClassification classification,
            BigDecimal internalAmount,
            BigDecimal externalAmount,
            String internalStatus,
            String externalStatus,
            String details) {
        ReconciliationItem item = new ReconciliationItem();
        item.runId = runId;
        item.paymentId = paymentId;
        item.externalReference = externalReference;
        item.classification = classification;
        item.internalAmount = internalAmount;
        item.externalAmount = externalAmount;
        item.internalStatus = internalStatus;
        item.externalStatus = externalStatus;
        item.details = details;
        return item;
    }

    public Long id() {
        return id;
    }

    public UUID runId() {
        return runId;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public String externalReference() {
        return externalReference;
    }

    public ReconciliationClassification classification() {
        return classification;
    }

    public BigDecimal internalAmount() {
        return internalAmount;
    }

    public BigDecimal externalAmount() {
        return externalAmount;
    }

    public String internalStatus() {
        return internalStatus;
    }

    public String externalStatus() {
        return externalStatus;
    }

    public String details() {
        return details;
    }
}
