package com.lab.backend.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestLabPriceRepository extends JpaRepository<TestLabPrice, Long> {

    List<TestLabPrice> findByTestId(Long testId);

    List<TestLabPrice> findByTestIdAndIsActiveTrue(Long testId);

    Optional<TestLabPrice> findByTestIdAndLabId(Long testId, Long labId);
}
