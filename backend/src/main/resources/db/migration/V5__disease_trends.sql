-- Precomputed disease-trend aggregates (plan §5.6): for each test and month,
-- how many results were entered and how many were flagged abnormal. Refreshed
-- by a nightly @Scheduled job so dashboards stay fast. This is aggregate
-- statistics, not diagnosis.
CREATE TABLE disease_trends (
    id             BIGSERIAL PRIMARY KEY,
    test_code      TEXT NOT NULL,
    test_name      TEXT NOT NULL,
    month          DATE NOT NULL,          -- first day of the month bucket
    total_tests    BIGINT NOT NULL,
    abnormal_count BIGINT NOT NULL,
    refreshed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (test_code, month)
);

CREATE INDEX idx_disease_trends_month ON disease_trends (month);
