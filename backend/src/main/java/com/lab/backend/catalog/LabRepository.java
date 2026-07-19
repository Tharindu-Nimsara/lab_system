package com.lab.backend.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabRepository extends JpaRepository<Lab, Long> {

    List<Lab> findByIsActiveTrueOrderBySortOrderAscNameAsc();

    Optional<Lab> findFirstByIsOutsourcedFalse();
}
