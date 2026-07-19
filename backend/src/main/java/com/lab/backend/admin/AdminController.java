package com.lab.backend.admin;

import com.lab.backend.analytics.DiseaseTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final JdbcClient jdbc;
    private final DiseaseTrendService diseaseTrends;

    public record DayPoint(LocalDate day, long patients, BigDecimal revenue) {}

    public record Stats(long patientsToday,
                        BigDecimal revenueToday,
                        long pendingOrders,
                        long completedToday,
                        List<DayPoint> last14Days) {}

    @GetMapping("/stats")
    public Stats stats() {
        LocalDate today = LocalDate.now();

        long patientsToday = jdbc.sql("""
                SELECT COUNT(DISTINCT patient_id) FROM invoices
                WHERE status <> 'VOID' AND CAST(created_at AS date) = :d
                """).param("d", today).query(Long.class).single();

        // Cash actually received today (amount_paid), including deposits on
        // partially-paid invoices.
        BigDecimal revenueToday = jdbc.sql("""
                SELECT COALESCE(SUM(amount_paid), 0) FROM invoices
                WHERE status <> 'VOID' AND CAST(created_at AS date) = :d
                """).param("d", today).query(BigDecimal.class).single();

        long pendingOrders = jdbc.sql("""
                SELECT COUNT(*) FROM orders o
                JOIN invoice_items ii ON ii.id = o.invoice_item_id
                JOIN invoices i ON i.id = ii.invoice_id
                WHERE i.status <> 'VOID' AND o.status IN ('PENDING','COLLECTED','IN_PROGRESS')
                """).query(Long.class).single();

        long completedToday = jdbc.sql("""
                SELECT COUNT(*) FROM orders
                WHERE status IN ('COMPLETED','VERIFIED') AND CAST(updated_at AS date) = :d
                """).param("d", today).query(Long.class).single();

        List<DayPoint> series = jdbc.sql("""
                SELECT d.day AS day,
                       COALESCE(COUNT(DISTINCT i.patient_id), 0) AS patients,
                       COALESCE(SUM(i.amount_paid), 0) AS revenue
                FROM generate_series(CAST(:from AS date), CAST(:to AS date), INTERVAL '1 day') AS d(day)
                LEFT JOIN invoices i
                  ON CAST(i.created_at AS date) = d.day AND i.status <> 'VOID'
                GROUP BY d.day
                ORDER BY d.day
                """)
                .param("from", today.minusDays(13))
                .param("to", today)
                .query((rs, n) -> new DayPoint(
                        rs.getDate("day").toLocalDate(),
                        rs.getLong("patients"),
                        rs.getBigDecimal("revenue")))
                .list();

        return new Stats(patientsToday, revenueToday, pendingOrders, completedToday, series);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit() {
        return jdbc.sql("""
                SELECT a.id, u.name AS user_name, a.action, a.entity, a.entity_id, a.ip, a.created_at
                FROM audit_logs a JOIN users u ON u.id = a.user_id
                ORDER BY a.id DESC LIMIT 100
                """).query().listOfRows();
    }

    /** Precomputed disease-trend buckets for the last N months (default 12). */
    @GetMapping("/disease-trends")
    public List<DiseaseTrendService.TrendPoint> diseaseTrends(
            @RequestParam(name = "months", defaultValue = "12") int months) {
        return diseaseTrends.recent(months);
    }

    /** Recompute the aggregate on demand (otherwise runs nightly). */
    @PostMapping("/disease-trends/refresh")
    public Map<String, Object> refreshDiseaseTrends() {
        int rows = diseaseTrends.refresh();
        return Map.of("buckets", rows);
    }
}
