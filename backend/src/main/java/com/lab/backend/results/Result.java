package com.lab.backend.results;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "results")
@Getter
@Setter
@NoArgsConstructor
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_values", nullable = false, columnDefinition = "jsonb")
    private String resultValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String flags;

    @Column(name = "entered_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime enteredAt;

    // --- Anomaly review (plan §5.6): set when a flagged result is acted on ---

    @Column(name = "anomaly_reviewed_at")
    private OffsetDateTime anomalyReviewedAt;

    @Column(name = "anomaly_reviewed_by")
    private Long anomalyReviewedBy;

    /** ACKNOWLEDGED or DISMISSED once reviewed; null while open in the queue. */
    @Column(name = "anomaly_action")
    private String anomalyAction;
}
