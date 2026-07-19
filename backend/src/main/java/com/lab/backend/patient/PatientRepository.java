package com.lab.backend.patient;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("""
        SELECT p FROM Patient p
        WHERE p.deletedAt IS NULL
          AND (p.phone LIKE CONCAT('%', :q, '%')
           OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY p.createdAt DESC
        """)
    List<Patient> search(@Param("q") String query, Pageable page);

    /** Active patients whose phone matches exactly — powers duplicate warnings. */
    @Query("SELECT p FROM Patient p WHERE p.deletedAt IS NULL AND p.phone = :phone ORDER BY p.createdAt")
    List<Patient> findActiveByPhone(@Param("phone") String phone);

    @Query(value = "SELECT nextval('patient_no_seq')", nativeQuery = true)
    long nextPatientNo();

    // --- Merge support: repoint a duplicate's records onto the surviving patient ---

    @Modifying
    @Query("UPDATE Invoice i SET i.patientId = :target WHERE i.patientId = :source")
    int repointInvoices(@Param("source") Long source, @Param("target") Long target);

    @Modifying
    @Query("UPDATE Report r SET r.patientId = :target WHERE r.patientId = :source")
    int repointReports(@Param("source") Long source, @Param("target") Long target);
}
