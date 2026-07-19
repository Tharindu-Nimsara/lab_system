package com.lab.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    interface MethodTotal {
        String getMethod();
        BigDecimal getTotal();
    }

    // Cash actually received by method (amount_paid), across all non-void invoices,
    // so partially-paid invoices contribute the portion collected. Attributed to the
    // invoice's created day (later balance settlements are not split out by day —
    // that would need a payments ledger).
    @Query(value = """
        SELECT payment_method AS method, SUM(amount_paid) AS total
        FROM invoices
        WHERE status <> 'VOID'
          AND amount_paid > 0
          AND CAST(created_at AS date) BETWEEN :from AND :to
        GROUP BY payment_method
        ORDER BY payment_method
        """, nativeQuery = true)
    List<MethodTotal> revenueByMethod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    interface CategoryTotal {
        String getCategory();
        BigDecimal getTotal();
    }

    // Value of tests sold by category (billed price_at_sale), across non-void
    // invoices. This is the "revenue/profit by category" view — the billed value
    // of what was sold, independent of how much has been collected yet.
    @Query(value = """
        SELECT t.category AS category, SUM(ii.price_at_sale) AS total
        FROM invoice_items ii
        JOIN invoices i ON i.id = ii.invoice_id
        JOIN tests t ON t.id = ii.test_id
        WHERE i.status <> 'VOID'
          AND CAST(i.created_at AS date) BETWEEN :from AND :to
        GROUP BY t.category
        ORDER BY t.category
        """, nativeQuery = true)
    List<CategoryTotal> revenueByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT nextval('invoice_no_seq')", nativeQuery = true)
    long nextInvoiceNo();
}
