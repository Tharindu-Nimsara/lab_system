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

    // ---- Rich analytics (admin dashboard) ----

    public record NamedAmount(String name, BigDecimal amount, long count) {}
    public record LabRevenue(String lab, boolean outsourced, BigDecimal billed,
                             BigDecimal commission) {}
    public record BusyCell(int dow, int hour, long count) {}
    public record Analytics(String period, LocalDate from, LocalDate to,
                            long patients, long newPatients, long returningPatients,
                            BigDecimal revenue,
                            List<NamedAmount> revenueByTest,
                            List<NamedAmount> topTests,
                            List<LabRevenue> revenueByLab,
                            List<BusyCell> busyHours) {}

    /**
     * Business analytics for a period. {@code period} = "week" (last 7 days) or
     * "month" (current calendar month). Revenue is cash received (amount_paid),
     * attributed to the invoice day; excludes void invoices. Commission is
     * admin-only and never appears on invoices.
     */
    @GetMapping("/analytics")
    public Analytics analytics(@RequestParam(name = "period", defaultValue = "week") String period) {
        LocalDate today = LocalDate.now();
        boolean month = "month".equalsIgnoreCase(period);
        LocalDate from = month ? today.withDayOfMonth(1) : today.minusDays(6);
        LocalDate to = today;

        // Unique patients in the period, and how many were new (first-ever invoice
        // falls inside the period) vs returning.
        long patients = jdbc.sql("""
                SELECT COUNT(DISTINCT patient_id) FROM invoices
                WHERE status <> 'VOID' AND CAST(created_at AS date) BETWEEN :from AND :to
                """).param("from", from).param("to", to).query(Long.class).single();

        long newPatients = jdbc.sql("""
                SELECT COUNT(*) FROM (
                    SELECT patient_id, MIN(CAST(created_at AS date)) AS first_visit
                    FROM invoices WHERE status <> 'VOID'
                    GROUP BY patient_id
                ) f
                WHERE f.first_visit BETWEEN :from AND :to
                """).param("from", from).param("to", to).query(Long.class).single();
        long returningPatients = Math.max(0, patients - newPatients);

        BigDecimal revenue = jdbc.sql("""
                SELECT COALESCE(SUM(amount_paid), 0) FROM invoices
                WHERE status <> 'VOID' AND CAST(created_at AS date) BETWEEN :from AND :to
                """).param("from", from).param("to", to).query(BigDecimal.class).single();

        // Revenue by test = billed value of that test's lines in the period.
        List<NamedAmount> revenueByTest = jdbc.sql("""
                SELECT t.name AS name,
                       COALESCE(SUM(ii.price_at_sale), 0) AS amount,
                       COUNT(*) AS cnt
                FROM invoice_items ii
                JOIN invoices i ON i.id = ii.invoice_id
                JOIN tests t    ON t.id = ii.test_id
                WHERE i.status <> 'VOID'
                  AND CAST(i.created_at AS date) BETWEEN :from AND :to
                GROUP BY t.name
                ORDER BY amount DESC
                """).param("from", from).param("to", to)
                .query((rs, n) -> new NamedAmount(rs.getString("name"),
                        rs.getBigDecimal("amount"), rs.getLong("cnt")))
                .list();

        // Top tests by volume (count of lines).
        List<NamedAmount> topTests = jdbc.sql("""
                SELECT t.name AS name,
                       COALESCE(SUM(ii.price_at_sale), 0) AS amount,
                       COUNT(*) AS cnt
                FROM invoice_items ii
                JOIN invoices i ON i.id = ii.invoice_id
                JOIN tests t    ON t.id = ii.test_id
                WHERE i.status <> 'VOID'
                  AND CAST(i.created_at AS date) BETWEEN :from AND :to
                GROUP BY t.name
                ORDER BY cnt DESC
                LIMIT 5
                """).param("from", from).param("to", to)
                .query((rs, n) -> new NamedAmount(rs.getString("name"),
                        rs.getBigDecimal("amount"), rs.getLong("cnt")))
                .list();

        // Revenue routed to each lab, with our commission on outsourced ones
        // (billed × per-test-per-lab commission_rate%). Admin-only.
        List<LabRevenue> revenueByLab = jdbc.sql("""
                SELECT l.name AS lab,
                       l.is_outsourced AS outsourced,
                       COALESCE(SUM(ii.price_at_sale), 0) AS billed,
                       COALESCE(SUM(ii.price_at_sale * COALESCE(tlp.commission_rate, 0) / 100), 0)
                           AS commission
                FROM invoice_items ii
                JOIN invoices i ON i.id = ii.invoice_id
                JOIN labs l     ON l.id = ii.lab_id
                LEFT JOIN test_lab_prices tlp
                       ON tlp.test_id = ii.test_id AND tlp.lab_id = ii.lab_id
                WHERE i.status <> 'VOID'
                  AND CAST(i.created_at AS date) BETWEEN :from AND :to
                GROUP BY l.name, l.is_outsourced
                ORDER BY billed DESC
                """).param("from", from).param("to", to)
                .query((rs, n) -> new LabRevenue(rs.getString("lab"),
                        rs.getBoolean("outsourced"),
                        rs.getBigDecimal("billed"), rs.getBigDecimal("commission")))
                .list();

        // Busy hours: invoice count by day-of-week (0=Sun) × hour, local time.
        List<BusyCell> busyHours = jdbc.sql("""
                SELECT EXTRACT(DOW FROM created_at)::int AS dow,
                       EXTRACT(HOUR FROM created_at)::int AS hour,
                       COUNT(*) AS cnt
                FROM invoices
                WHERE status <> 'VOID'
                  AND CAST(created_at AS date) BETWEEN :from AND :to
                GROUP BY dow, hour
                ORDER BY dow, hour
                """).param("from", from).param("to", to)
                .query((rs, n) -> new BusyCell(rs.getInt("dow"), rs.getInt("hour"),
                        rs.getLong("cnt")))
                .list();

        return new Analytics(month ? "month" : "week", from, to,
                patients, newPatients, returningPatients, revenue,
                revenueByTest, topTests, revenueByLab, busyHours);
    }
}
