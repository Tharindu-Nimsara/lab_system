CREATE TABLE branches (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    address    TEXT,
    phone      TEXT,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    branch_id     BIGINT NOT NULL REFERENCES branches(id),
    name          TEXT NOT NULL,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('ADMIN','RECEPTIONIST','LAB_STAFF','MARKETING')),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE patients (
    id               BIGSERIAL PRIMARY KEY,
    patient_no       TEXT NOT NULL UNIQUE,        -- e.g. P-000123
    name             TEXT NOT NULL,
    nic_or_id        TEXT,
    dob              DATE,
    gender           TEXT,
    phone            TEXT NOT NULL,               -- primary lookup key
    email            TEXT,
    address          TEXT,
    consent_email    BOOLEAN NOT NULL DEFAULT FALSE,
    consent_whatsapp BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_patients_phone ON patients(phone);
CREATE INDEX idx_patients_name  ON patients(name);
CREATE SEQUENCE patient_no_seq START 1;

CREATE TABLE test_templates (
    id     BIGSERIAL PRIMARY KEY,
    name   TEXT NOT NULL,
    -- example: [{"key":"glucose","label":"Fasting Glucose","unit":"mg/dL",
    --            "refLow":70,"refHigh":100,"type":"number"}]
    fields JSONB NOT NULL
);

CREATE TABLE tests (
    id            BIGSERIAL PRIMARY KEY,
    code          TEXT NOT NULL UNIQUE,           -- e.g. FBS, LIPID
    name          TEXT NOT NULL,
    category      TEXT NOT NULL,                  -- Biochemistry, Hematology, ...
    price         NUMERIC(10,2) NOT NULL,
    specimen_type TEXT,
    template_id   BIGINT NOT NULL REFERENCES test_templates(id),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE invoices (
    id             BIGSERIAL PRIMARY KEY,
    invoice_no     TEXT NOT NULL UNIQUE,          -- e.g. INV-20260718-0042
    branch_id      BIGINT NOT NULL REFERENCES branches(id),
    patient_id     BIGINT NOT NULL REFERENCES patients(id),
    created_by     BIGINT NOT NULL REFERENCES users(id),
    subtotal       NUMERIC(10,2) NOT NULL,
    discount       NUMERIC(10,2) NOT NULL DEFAULT 0,
    total          NUMERIC(10,2) NOT NULL,
    payment_method TEXT NOT NULL,                 -- CASH | CARD
    status         TEXT NOT NULL DEFAULT 'PAID',  -- PAID | VOID
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoices_created_at ON invoices(created_at);
CREATE SEQUENCE invoice_no_seq START 1;

CREATE TABLE invoice_items (
    id            BIGSERIAL PRIMARY KEY,
    invoice_id    BIGINT NOT NULL REFERENCES invoices(id),
    test_id       BIGINT NOT NULL REFERENCES tests(id),
    price_at_sale NUMERIC(10,2) NOT NULL
);

CREATE TABLE orders (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_item_id     BIGINT NOT NULL UNIQUE REFERENCES invoice_items(id),
    status              TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','COLLECTED','IN_PROGRESS','COMPLETED','VERIFIED')),
    sample_collected_at TIMESTAMPTZ,
    result_entered_by   BIGINT REFERENCES users(id),
    verified_by         BIGINT REFERENCES users(id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE results (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    result_values JSONB NOT NULL,                 -- {"glucose": 126}
    flags         JSONB,                          -- {"glucose": "H"}
    entered_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reports (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT NOT NULL REFERENCES patients(id),
    invoice_id       BIGINT NOT NULL UNIQUE REFERENCES invoices(id),
    pdf_path         TEXT,
    finalized_at     TIMESTAMPTZ,
    sent_email_at    TIMESTAMPTZ,
    sent_whatsapp_at TIMESTAMPTZ
);

CREATE TABLE expenses (
    id           BIGSERIAL PRIMARY KEY,
    branch_id    BIGINT NOT NULL REFERENCES branches(id),
    category     TEXT NOT NULL,                   -- SALARY|KITS|EQUIPMENT|UTILITY|OTHER
    description  TEXT,
    amount       NUMERIC(10,2) NOT NULL,
    expense_date DATE NOT NULL,
    entered_by   BIGINT NOT NULL REFERENCES users(id)
);

CREATE TABLE audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    action     TEXT NOT NULL,                     -- VIEW|CREATE|UPDATE|VOID|LOGIN ...
    entity     TEXT NOT NULL,                     -- Patient|Invoice|Result ...
    entity_id  BIGINT,
    details    JSONB,
    ip         TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs(entity, entity_id);
