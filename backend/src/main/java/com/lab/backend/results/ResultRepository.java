package com.lab.backend.results;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ResultRepository extends JpaRepository<Result, Long> {

    Optional<Result> findByOrderId(Long orderId);

    /** One flagged, unreviewed result in the anomaly queue, with patient/test context. */
    interface AnomalyRow {
        Long getResultId();
        Long getOrderId();
        String getTestCode();
        String getTestName();
        String getPatientNo();
        String getPatientName();
        String getResultValues();   // raw JSONB text
        String getFlags();          // raw JSONB text
        Instant getEnteredAt();
    }

    @Query(value = """
        SELECT r.id            AS resultId,
               o.id            AS orderId,
               t.code          AS testCode,
               t.name          AS testName,
               p.patient_no    AS patientNo,
               p.name          AS patientName,
               r.result_values AS resultValues,
               r.flags         AS flags,
               r.entered_at    AS enteredAt
        FROM results r
        JOIN orders o         ON o.id = r.order_id
        JOIN invoice_items ii ON ii.id = o.invoice_item_id
        JOIN invoices i       ON i.id = ii.invoice_id
        JOIN patients p       ON p.id = i.patient_id
        JOIN tests t          ON t.id = ii.test_id
        WHERE r.anomaly_reviewed_at IS NULL
          AND r.flags IS NOT NULL
          AND r.flags::text <> '{}'
          AND i.status <> 'VOID'
        ORDER BY r.entered_at DESC
        """, nativeQuery = true)
    List<AnomalyRow> openAnomalies();
}
