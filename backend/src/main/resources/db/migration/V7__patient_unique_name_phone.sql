-- Hard backstop against exact duplicate patients (same phone + same name).
-- The service layer already rejects these at creation; this partial unique index
-- guarantees it at the database level too, even against races or bulk imports.
-- Scoped to active rows so a soft-deleted/merged record never blocks re-use of a
-- name+phone. Name is normalized (trimmed, lower-cased) to match the service check.
--
-- NOTE: this migration fails if exact duplicates already exist — merge them first
-- (the app's patient merge tool, or the admin /api/patients/merge endpoint).
CREATE UNIQUE INDEX uq_patients_active_name_phone
    ON patients (LOWER(TRIM(name)), phone)
    WHERE deleted_at IS NULL;
