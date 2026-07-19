-- Anomaly alert queue (plan §5.6): out-of-range results await doctor review.
-- A result with a non-empty flags object and no review row is "open" in the queue;
-- acknowledging or dismissing it stamps who/when/what and removes it from the queue.
ALTER TABLE results ADD COLUMN anomaly_reviewed_at  TIMESTAMPTZ;
ALTER TABLE results ADD COLUMN anomaly_reviewed_by  BIGINT REFERENCES users(id);
ALTER TABLE results ADD COLUMN anomaly_action       TEXT;   -- ACKNOWLEDGED | DISMISSED

-- Fast lookup of the open queue: flagged results not yet reviewed.
CREATE INDEX idx_results_open_anomalies ON results (entered_at)
    WHERE anomaly_reviewed_at IS NULL AND flags IS NOT NULL AND flags::text <> '{}';
