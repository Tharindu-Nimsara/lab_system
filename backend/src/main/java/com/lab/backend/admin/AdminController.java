package com.lab.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
                WHERE status = 'PAID' AND CAST(created_at AS date) = :d
                """).param("d", today).query(Long.class).single();

        BigDecimal revenueToday = jdbc.sql("""
                SELECT COALESCE(SUM(total), 0) FROM invoices
                WHERE status = 'PAID' AND CAST(created_at AS date) = :d
                """).param("d", today).query(BigDecimal.class).single();

        long pendingOrders = jdbc.sql("""
                SELECT COUNT(*) FROM orders o
                JOIN invoice_items ii ON ii.id = o.invoice_item_id
                JOIN invoices i ON i.id = ii.invoice_id
                WHERE i.status = 'PAID' AND o.status IN ('PENDING','COLLECTED','IN_PROGRESS')
                """).query(Long.class).single();

        long completedToday = jdbc.sql("""
                SELECT COUNT(*) FROM orders
                WHERE status IN ('COMPLETED','VERIFIED') AND CAST(updated_at AS date) = :d
                """).param("d", today).query(Long.class).single();

        List<DayPoint> series = jdbc.sql("""
                SELECT d.day AS day,
                       COALESCE(COUNT(DISTINCT i.patient_id), 0) AS patients,
                       COALESCE(SUM(i.total), 0) AS revenue
                FROM generate_series(CAST(:from AS date), CAST(:to AS date), INTERVAL '1 day') AS d(day)
                LEFT JOIN invoices i
                  ON CAST(i.created_at AS date) = d.day AND i.status = 'PAID'
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
}
