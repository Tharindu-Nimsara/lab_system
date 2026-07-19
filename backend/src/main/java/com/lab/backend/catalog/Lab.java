package com.lab.backend.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A lab that fulfils tests: our in-house lab or an outsourcing partner. */
@Entity
@Table(name = "labs")
@Getter
@Setter
@NoArgsConstructor
public class Lab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** false = our in-house lab; true = an external/outsourced lab. */
    @Column(name = "is_outsourced", nullable = false)
    private boolean isOutsourced = true;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
