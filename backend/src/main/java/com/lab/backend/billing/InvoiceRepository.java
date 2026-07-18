package com.lab.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    @Query(value = "SELECT nextval('invoice_no_seq')", nativeQuery = true)
    long nextInvoiceNo();
}
