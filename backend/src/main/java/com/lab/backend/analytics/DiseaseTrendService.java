package com.lab.backend.analytics;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Disease-trend analytics (plan §5.6): for each test and month, the share of
 * results flagged out-of-range. Recomputed by a nightly job into the
 * {@code disease_trends} table so dashboards read a small precomputed set rather
 * than scanning every result. Aggregate statistics only — not diagnosis.
 */
@Service
@RequiredArgsConstructor
public class DiseaseTrendService {

    private static final Logger log = LoggerFactory.getLogger(DiseaseTrendService.class);

    private final JdbcClient jdbc;

    public record TrendPoint(String testCode, String testName, LocalDate month,
                             long totalTests, long abnormalCount) {}

    /** Nightly at 02:30 server time. Also runnable on demand via {@link #refresh()}. */
    @Scheduled(cron = "${app.analytics.disease-trend-cron:0 30 2 * * *}")
    public void scheduledRefresh() {
        int rows = refresh();
        log.info("Disease-trend aggregation refreshed {} test-month buckets", rows);
    }

    /**
     * Rebuild the aggregate from results. A result counts as abnormal when its
     * flags object is non-empty. Idempotent upsert keyed by (test_code, month).
     *
     * @return number of test-month buckets written
     */
    @Transactional
    public int refresh() {
        return jdbc.sql("""
                INSERT INTO disease_trends (test_code, test_name, month, total_tests, abnormal_count, refreshed_at)
                SELECT t.code AS test_code,
                       MAX(t.name) AS test_name,
                       date_trunc('month', r.entered_at)::date AS month,
                       COUNT(*) AS total_tests,
                       COUNT(*) FILTER (
                           WHERE r.flags IS NOT NULL AND r.flags::text <> '{}'
                       ) AS abnormal_count,
                       now() AS refreshed_at
                FROM results r
                JOIN orders o         ON o.id = r.order_id
                JOIN invoice_items ii ON ii.id = o.invoice_item_id
                JOIN invoices i       ON i.id = ii.invoice_id
                JOIN tests t          ON t.id = ii.test_id
                WHERE i.status <> 'VOID'
                GROUP BY t.code, date_trunc('month', r.entered_at)
                ON CONFLICT (test_code, month) DO UPDATE
                    SET test_name      = EXCLUDED.test_name,
                        total_tests    = EXCLUDED.total_tests,
                        abnormal_count = EXCLUDED.abnormal_count,
                        refreshed_at   = EXCLUDED.refreshed_at
                """).update();
    }

    /** Trend rows for the last {@code months} calendar months, oldest first. */
    public List<TrendPoint> recent(int months) {
        LocalDate from = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1L);
        return jdbc.sql("""
                SELECT test_code, test_name, month, total_tests, abnormal_count
                FROM disease_trends
                WHERE month >= :from
                ORDER BY month, test_code
                """)
                .param("from", from)
                .query((rs, n) -> new TrendPoint(
                        rs.getString("test_code"),
                        rs.getString("test_name"),
                        rs.getDate("month").toLocalDate(),
                        rs.getLong("total_tests"),
                        rs.getLong("abnormal_count")))
                .list();
    }
}
