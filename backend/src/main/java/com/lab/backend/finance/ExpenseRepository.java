package com.lab.backend.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate from, LocalDate to);

    interface CategoryAmount {
        String getCategory();
        BigDecimal getAmount();
    }

    @Query(value = """
        SELECT category AS category, SUM(amount) AS amount
        FROM expenses
        WHERE expense_date BETWEEN :from AND :to
        GROUP BY category
        ORDER BY category
        """, nativeQuery = true)
    List<CategoryAmount> byCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
