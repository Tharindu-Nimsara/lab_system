package com.lab.backend.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no", nullable = false, unique = true)
    private String invoiceNo;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    /** How much has actually been paid so far; balance = total − amountPaid. */
    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /** UNPAID | PARTIAL | PAID (derived from payment) — or VOID (independent). */
    @Column(nullable = false)
    private String status = "UNPAID";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Outstanding amount still owed. */
    @Transient
    public BigDecimal getBalance() {
        return total == null ? BigDecimal.ZERO : total.subtract(amountPaid);
    }

    /**
     * Recompute the payment status from amountPaid vs total. Does not touch VOID —
     * a voided invoice stays void regardless of what was paid.
     */
    public void recomputeStatus() {
        if ("VOID".equals(status)) {
            return;
        }
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            status = "UNPAID";
        } else if (amountPaid.compareTo(total) >= 0) {
            status = "PAID";
        } else {
            status = "PARTIAL";
        }
    }
}
