-- Partial payments: patients may pay a deposit at billing and settle the balance
-- when collecting results. Track how much has actually been paid; the balance is
-- total − amount_paid, and status is derived (UNPAID / PARTIAL / PAID) in the app.
ALTER TABLE invoices ADD COLUMN amount_paid NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Backfill: every existing non-void invoice was fully paid under the old model
-- (status was PAID on creation), so amount_paid = total. Void invoices stay at 0.
UPDATE invoices SET amount_paid = total WHERE status <> 'VOID';

-- The status column previously held only PAID | VOID. It now also carries
-- UNPAID | PARTIAL for the payment lifecycle; VOID is unchanged and independent.
-- Normalize any legacy 'PAID' rows: they are genuinely fully paid (paid = total).
