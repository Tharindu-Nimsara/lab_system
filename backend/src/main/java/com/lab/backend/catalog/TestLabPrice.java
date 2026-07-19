package com.lab.backend.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** The price of one test at one lab. A test only has rows for labs that offer it. */
@Entity
@Table(name = "test_lab_prices")
@Getter
@Setter
@NoArgsConstructor
public class TestLabPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(name = "lab_id", nullable = false)
    private Long labId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
