-- Performance pass (plan §3 Phase 3): index the foreign keys and filter columns
-- on hot query paths. Postgres does NOT auto-index foreign keys, so the joins in
-- billing, the worklist, finalize/report assembly, finance summaries, disease
-- trends, and the audit viewer were doing sequential scans as data grows.

-- Billing / visit history / stats: invoices are looked up per patient constantly.
CREATE INDEX idx_invoices_patient_id ON invoices (patient_id);

-- Worklist, finalize, report assembly, disease trends all join through these.
CREATE INDEX idx_invoice_items_invoice_id ON invoice_items (invoice_id);
CREATE INDEX idx_invoice_items_test_id    ON invoice_items (test_id);

-- Orders are filtered by status on every worklist and pending-KPI query.
-- (orders.invoice_item_id is already UNIQUE, so it is indexed implicitly.)
CREATE INDEX idx_orders_status ON orders (status);

-- Merge repoints reports by patient; patient detail lists their reports.
CREATE INDEX idx_reports_patient_id ON reports (patient_id);

-- Daily cash-flow / monthly P&L filter and group by the expense date.
CREATE INDEX idx_expenses_expense_date ON expenses (expense_date);

-- Audit viewer joins to users and orders by recency.
CREATE INDEX idx_audit_user_id    ON audit_logs (user_id);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
