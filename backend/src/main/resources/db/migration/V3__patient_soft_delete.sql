-- Soft-delete for patients: medical records are never hard-deleted (plan §5.2).
-- A row with deleted_at set is hidden from search and treated as merged-away.
ALTER TABLE patients ADD COLUMN deleted_at TIMESTAMPTZ;

-- Fast returning-patient lookup by phone, only over active rows.
CREATE INDEX idx_patients_phone_active ON patients (phone) WHERE deleted_at IS NULL;
