-- Outsourcing: tests may be fulfilled by our in-house lab or by external labs,
-- each with its own price. Some tests we don't do at all (outsource-only); some
-- we outsource even when we could do them. So price lives per (test, lab) pair,
-- and a test our lab doesn't offer simply has no in-house price row.

CREATE TABLE labs (
    id            BIGSERIAL PRIMARY KEY,
    name          TEXT NOT NULL UNIQUE,
    is_outsourced BOOLEAN NOT NULL DEFAULT TRUE,   -- false = our in-house lab
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order    INT NOT NULL DEFAULT 0
);

-- In-house first (default), then the outsourcing partners.
INSERT INTO labs (name, is_outsourced, sort_order) VALUES
    ('Medi Lab Deraniyagala', FALSE, 0),
    ('Durdans',  TRUE, 1),
    ('Hemas',    TRUE, 2),
    ('Nawaloka', TRUE, 3),
    ('Asiri',    TRUE, 4),
    ('Sinha',    TRUE, 5);

-- One price per test per lab that offers it.
CREATE TABLE test_lab_prices (
    id        BIGSERIAL PRIMARY KEY,
    test_id   BIGINT NOT NULL REFERENCES tests(id),
    lab_id    BIGINT NOT NULL REFERENCES labs(id),
    price     NUMERIC(10,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (test_id, lab_id)
);

CREATE INDEX idx_test_lab_prices_test ON test_lab_prices (test_id);

-- Backfill: existing tests keep their current price as the in-house price.
INSERT INTO test_lab_prices (test_id, lab_id, price)
SELECT t.id, (SELECT id FROM labs WHERE is_outsourced = FALSE), t.price
FROM tests t;

-- Which lab fulfilled each invoice line. Existing lines are our in-house lab.
ALTER TABLE invoice_items
    ADD COLUMN lab_id BIGINT REFERENCES labs(id);
UPDATE invoice_items
    SET lab_id = (SELECT id FROM labs WHERE is_outsourced = FALSE)
    WHERE lab_id IS NULL;
ALTER TABLE invoice_items ALTER COLUMN lab_id SET NOT NULL;
