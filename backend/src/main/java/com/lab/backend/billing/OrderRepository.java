package com.lab.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<LabOrder, Long> {

    List<LabOrder> findByInvoiceItemIdIn(Collection<Long> invoiceItemIds);

    interface WorklistRow {
        Long getOrderId();
        String getStatus();
        String getTestCode();
        String getTestName();
        String getPatientNo();
        String getPatientName();
        Long getInvoiceId();
        String getInvoiceNo();
        Instant getBilledAt();
        Instant getSampleCollectedAt();
    }

    @Query(value = """
        SELECT o.id                  AS orderId,
               o.status              AS status,
               t.code                AS testCode,
               t.name                AS testName,
               p.patient_no          AS patientNo,
               p.name                AS patientName,
               i.id                  AS invoiceId,
               i.invoice_no          AS invoiceNo,
               i.created_at          AS billedAt,
               o.sample_collected_at AS sampleCollectedAt
        FROM orders o
        JOIN invoice_items ii ON ii.id = o.invoice_item_id
        JOIN invoices i       ON i.id = ii.invoice_id
        JOIN patients p       ON p.id = i.patient_id
        JOIN tests t          ON t.id = ii.test_id
        WHERE i.status = 'PAID'
          AND (CAST(:status AS text) IS NULL OR o.status = :status)
          AND (CAST(:day AS date) IS NULL OR CAST(i.created_at AS date) = CAST(:day AS date))
        ORDER BY o.id
        """, nativeQuery = true)
    List<WorklistRow> worklist(@Param("status") String status, @Param("day") LocalDate day);
}
