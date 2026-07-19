-- Commission on outsourced tests: for each (test, lab), the percentage of the
-- billed price we earn as commission. Admin-only analytics — never shown on
-- invoices or bills. Default 0 until real rates are entered later.
ALTER TABLE test_lab_prices
    ADD COLUMN commission_rate NUMERIC(5,2) NOT NULL DEFAULT 0;   -- percent, e.g. 15.00
