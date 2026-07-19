package com.lab.backend.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {

    List<LabTest> findByIsActiveTrueOrderByCategoryAscNameAsc();
}
