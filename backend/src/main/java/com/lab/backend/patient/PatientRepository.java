package com.lab.backend.patient;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("""
        SELECT p FROM Patient p
        WHERE p.phone LIKE CONCAT('%', :q, '%')
           OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY p.createdAt DESC
        """)
    List<Patient> search(@Param("q") String query, Pageable page);

    @Query(value = "SELECT nextval('patient_no_seq')", nativeQuery = true)
    long nextPatientNo();
}
