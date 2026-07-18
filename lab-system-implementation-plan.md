# Medical Laboratory Management System — Implementation Plan (Java Edition)

**Version:** 2.0 · **Date:** July 2026 · **Based on:** Lab System Requirement Analysis
**Stack decision:** Java / Spring Boot backend, chosen for reliability, robustness, and healthcare-integration compatibility.

---

## 1. Executive Summary

This plan translates the requirements document into a concrete, phased build strategy. The system will be delivered in four phases over roughly 5–7 months, starting with a revenue-critical MVP (billing + patients + reports) and layering in financials, analytics, and predictive features afterward. The architecture is a monolithic Spring Boot application with a PostgreSQL database — an intentionally conservative, enterprise-grade foundation that is cheap to run, fails loudly and predictably, and is structured to scale to multiple branches later.

Guiding principles:

1. **Ship the money path first.** Reception billing and report delivery are what the lab runs on daily. Everything else can wait.
2. **Boring, battle-tested technology.** Spring Boot + PostgreSQL is the stack that banks and hospital systems trust. Fewer surprises, fewer 2 a.m. failures.
3. **Design for privacy from day one.** Medical data cannot be retrofitted for security. Encryption, access control, and audit logs go in at the schema level, not as a Phase 4 afterthought.
4. **Multi-branch ready, single-branch built.** A `branch_id` column costs nothing now and saves a painful migration later.

---

## 2. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Java 21 (LTS)** | Long-term support to 2031; virtual threads for effortless concurrency |
| Framework | **Spring Boot 3.x** | The industry standard for reliable business backends; mature transactions, security, and health-check tooling built in |
| Persistence | **Spring Data JPA (Hibernate)** | Type-safe repository pattern; `@Transactional` guarantees for billing integrity |
| Migrations | **Flyway** | Versioned SQL migrations, run automatically on startup |
| Database | **PostgreSQL 16** | Rock-solid relational DB; JSONB columns handle flexible test-result templates; strong backup tooling |
| Auth | **Spring Security** (session-based, BCrypt) with RBAC | Roles: Admin, Receptionist, Lab Staff, (later) Marketing; method-level `@PreAuthorize` enforcement |
| Validation | **Jakarta Bean Validation** | Declarative request validation at the API boundary |
| PDF generation | **JasperReports** (bills + lab reports) | Purpose-built for structured, professional medical/financial report templates with pixel-perfect print output |
| Email | **Amazon SES / Resend via SDK** | Cheap transactional email for report delivery |
| WhatsApp | **WhatsApp Business Cloud API (Meta)** | Official API for sending report PDFs; avoids account bans from unofficial libraries |
| Frontend | **React + TypeScript (Vite)**, Tailwind CSS + shadcn/ui | Unchanged — best-in-class for the POS and dashboard UIs |
| Charts | **Recharts** | Simple React charting for the admin dashboard |
| Build | **Maven** | Predictable, universally documented |
| Hosting | **VPS (Hetzner/DigitalOcean) — single fat JAR under systemd, or Docker** | $15–50/month to start; managed Postgres recommended |
| Backups | **Automated pg_dump → S3/Backblaze B2, daily, encrypted** | Off-site, tested restores |

Why this stack on your stated criteria: the JVM's decades of production hardening and per-request threading give **reliability** by default; compile-time typing plus checked exceptions plus Spring's transaction management give **robustness** as the path of least resistance; and Java's deep HL7/FHIR and enterprise-integration ecosystem gives **compatibility** with hospital LIS/HIS systems and lab analyzer machines if you ever integrate directly with them — healthcare IT is heavily a Java world.

**What to avoid at this stage:** microservices, Kubernetes, message queues, GraphQL, and a mobile app. A modular monolith earns you everything you need at far lower complexity.

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────────┐
│                    React SPA                        │
│  ┌──────────┐ ┌──────────┐ ┌─────────┐ ┌─────────┐  │
│  │ POS /    │ │ Lab      │ │ Reports │ │ Admin   │  │
│  │ Reception│ │ Worklist │ │ Module  │ │Dashboard│  │
│  └──────────┘ └──────────┘ └─────────┘ └─────────┘  │
└───────────────────────┬─────────────────────────────┘
                        │ HTTPS / REST JSON
┌───────────────────────▼─────────────────────────────┐
│              Spring Boot Application                │
│  Controllers (REST)  →  Services  →  Repositories   │
│  Spring Security (session + RBAC)                   │
│  ── Service modules ──                              │
│  Billing │ Patients │ Results │ Finance │ Admin     │
│  JasperReports PDF │ Email │ WhatsApp │             │
│  @Scheduled analytics jobs (nightly aggregates)     │
└───────────────────────┬─────────────────────────────┘
                        │ JDBC (HikariCP pool)
        ┌───────────────┼───────────────────┐
┌───────▼──────┐ ┌──────▼───────┐ ┌─────────▼────────┐
│ PostgreSQL   │ │ File storage │ │ External APIs    │
│ (primary DB) │ │ (PDFs: S3/   │ │ SES · WhatsApp   │
│ + Flyway     │ │  local disk) │ │ Cloud API        │
│ + daily      │ └──────────────┘ └──────────────────┘
│   backups    │
└──────────────┘
```

A single deployable JAR keeps development, debugging, and hosting simple. The layered structure (controller → service → repository) with one package per business module means any module can later be extracted into its own service if scale ever demands it — but a lab with even ten branches likely never will.

---

## 4. Database Design (Core Schema)

Managed as versioned Flyway SQL migrations.

```
branches        (id, name, address, phone, is_active)
users           (id, branch_id, name, email, password_hash, role, is_active)
patients        (id, patient_no, name, nic_or_id, dob, gender, phone,
                 email, address, consent_email, consent_whatsapp,
                 created_at)                          -- phone = primary lookup key
tests           (id, code, name, category, price, specimen_type,
                 template_id, is_active)
test_templates  (id, name, fields JSONB)              -- defines result fields,
                                                      -- units, reference ranges
invoices        (id, invoice_no, branch_id, patient_id, created_by,
                 subtotal, discount, total, payment_method, status, created_at)
invoice_items   (id, invoice_id, test_id, price_at_sale)
orders          (id, invoice_item_id, status, sample_collected_at,
                 result_entered_by, verified_by)       -- status: PENDING →
                                                      -- COLLECTED → IN_PROGRESS →
                                                      -- COMPLETED → VERIFIED
results         (id, order_id, values JSONB, flags JSONB, entered_at)
reports         (id, patient_id, invoice_id, pdf_path, finalized_at,
                 sent_email_at, sent_whatsapp_at)
expenses        (id, branch_id, category, description, amount,
                 expense_date, entered_by)
audit_logs      (id, user_id, action, entity, entity_id, details JSONB,
                 ip, created_at)
```

Key design decisions:

- **JSONB result templates.** Each test type (Blood, Urine, ECG…) defines its fields, units, and reference ranges in `test_templates.fields`. Lab staff get a dynamically rendered entry form; abnormal values are auto-flagged server-side by comparing against the reference range — this powers the "automated anomaly alerts" requirement without hard-coding every test. (JSONB is mapped in Hibernate via the Hypersistence Utils library.)
- **`price_at_sale` on invoice items.** Prices change; historical bills must not.
- **Order status pipeline.** Gives you the sample-tracking workflow (collected → in progress → completed → verified) and feeds turnaround-time metrics for the dashboard.
- **Audit logs on every sensitive action.** Who viewed/edited which patient record, when — essential for medical-data compliance and non-negotiable from Phase 1.
- **Phone number as the returning-patient lookup key**, with name + DOB as fallback. This drives the auto-fill requirement at reception.
- **Transactional billing.** Invoice + items + orders are created inside a single `@Transactional` service method — either everything is saved or nothing is. This is the category of correctness Spring makes trivially reliable.

---

## 5. Module Breakdown

### 5.1 POS & Billing (Reception)
The most performance-sensitive screen in the system. Target: bill a returning patient in under 30 seconds.

- Patient search-as-you-type by phone/name → auto-fill, or quick-create for new patients
- Test picker with category filters, search, and running total
- Discount field (admin-configurable permission), payment method (cash/card)
- On save: one transactional service call generates the invoice and per-test orders, then prints **two bill copies** (patient copy + lab worksheet copy with blank result spaces) from a JasperReports template
- Reprint and void (admin-only, audited) capabilities
- Test on the lab's actual thermal/A5 printer in week 3, not week 8

### 5.2 Patient Management
- Full profile: demographics, complete visit history, all past reports and invoices
- One-click access to any historical report PDF
- Merge-duplicates tool (duplicates *will* happen at reception)
- Soft delete only — medical records are never hard-deleted

### 5.3 Lab Worklist & Results Entry
- Queue view for lab staff: today's pending orders, filterable by status and test type
- Mark samples collected → in progress
- Result entry form rendered from the test's JSONB template, with units and reference ranges shown inline
- Server-side auto-flagging of out-of-range values (H/L markers) → feeds anomaly alerts
- Optional verification step by a senior staff member before a report can be finalized

### 5.4 Report Generation & Delivery
- Standardized, professional JasperReports template: lab letterhead, patient info, results table with reference ranges and flags, signature block
- Output paths: **print**, **email** (PDF attachment), **WhatsApp** (Business Cloud API document message)
- Delivery status tracked per channel on the `reports` table
- Consent flags captured at registration gate email/WhatsApp delivery (privacy requirement)

### 5.5 Financials & Expenses
- Expense entry with categories (salaries, test kits, equipment, utilities, other)
- Daily cash-flow report: revenue by payment method vs. expenses → net position
- Monthly P&L: revenue − expenses = net profit, with profit split by test category (computed from `invoice_items` joined to `tests.category`)

### 5.6 Admin Dashboard & Analytics
- KPI cards: today's patients, revenue, pending results, average turnaround time
- Trends: daily/monthly patient counts, new vs. returning ratio, revenue and profit charts
- Profit distribution by test category (pie/bar)
- **Disease trend analysis (Phase 3):** aggregate flagged-abnormal results by test type over time (e.g., % of glucose tests above range per month), precomputed by a nightly `@Scheduled` job so dashboards stay fast. This is aggregate statistics, not diagnosis.
- **Suggested test packages (Phase 4):** rule-based to start ("patients with X test history often book Y — suggest package") before anything ML-flavored. Rules are transparent, debuggable, and honest about what they are.
- **Anomaly alert queue:** list of recent out-of-range results for doctor review, with acknowledge/dismiss actions.

### 5.7 Administration
- User management with roles: **Admin** (everything), **Receptionist** (POS, patients, no financials), **Lab Staff** (worklist, results, no billing), **Marketing** (aggregate analytics only, no individual patient data) — enforced with `@PreAuthorize` at the method level
- Test catalog CRUD with price history
- Branch management (activated when branch #2 arrives)
- Audit log viewer

---

## 6. Phased Roadmap

### Phase 0 — Foundations (Weeks 1–2)
Project scaffolding (Spring Initializr, repo, CI, formatting), Flyway V1 schema, Spring Security with session auth + RBAC, base React layout, deployment pipeline to a staging server. **Exit criteria:** a logged-in admin sees an empty dashboard on a live HTTPS URL.

### Phase 1 — MVP: The Money Path (Weeks 3–8)
Test catalog CRUD → patient registration + search/auto-fill → transactional POS billing with dual bill printing → lab worklist + result entry from templates → JasperReports lab report PDF + printing. **Exit criteria: the lab can run a full day's operations on the system**, even if delivery is print-only and analytics don't exist yet. Run it in parallel with the old process for 1–2 weeks before cutting over.

### Phase 2 — Delivery & Financials (Weeks 9–13)
Email report delivery → WhatsApp Business API integration (apply for API access early — Meta approval takes time) → expense tracking → daily/monthly cash-flow and P&L reports → basic dashboard KPIs and revenue charts. **Exit criteria:** admin checks the dashboard instead of asking reception for numbers; patients receive reports on WhatsApp.

### Phase 3 — Analytics & Hardening (Weeks 14–19)
Full dashboard (trends, new-vs-returning, profit by category) → anomaly alert queue → disease trend charts via nightly aggregate jobs → audit log viewer → automated encrypted off-site backups with a tested restore drill → performance pass (indexes, slow-query review) → security review (see §7).

### Phase 4 — Growth Features (Weeks 20+)
Multi-branch activation (branch switcher, per-branch reporting, consolidated admin view) → rule-based test-package suggestions → patient SMS reminders → marketing export (aggregate, anonymized data only). If direct integration with analyzer machines or hospital systems ever becomes relevant, Java's HL7/FHIR libraries (HAPI FHIR) slot in here.

**Timeline honesty:** Spring Boot has a steeper initial learning curve than lighter frameworks. If the builder is new to Java, expect Phase 0–1 to take 1.5–2× these estimates; with an experienced Java developer they're realistic, and Phases 2–4 proceed *faster* than they would elsewhere because so much (transactions, security, scheduling, validation) is framework-provided. The phasing order is what matters and holds either way.

---

## 7. Security & Compliance

Medical data is the highest-liability asset in this system. Non-negotiables:

- **Transport:** HTTPS everywhere (Let's Encrypt), HSTS enabled.
- **At rest:** full-disk encryption on the server; encrypted backups; PDFs stored in private buckets with signed, expiring URLs — never publicly linkable.
- **Passwords:** BCrypt via Spring Security's `PasswordEncoder`; rate-limited login; forced strong passwords; session timeout on shared reception machines.
- **RBAC enforced server-side** with Spring Security filter rules plus method-level `@PreAuthorize` — never trust the frontend to hide buttons.
- **Audit trail:** every read/write of patient data logged (who, what, when, from where) via a service-layer aspect or explicit calls.
- **Input safety:** JPA parameterized queries (SQL injection), Bean Validation on every request DTO, CSRF protection on the session, output encoding in React.
- **Data minimization:** collect only what's needed; the Marketing role sees aggregates, never individual records.
- **Local law:** Sri Lanka's Personal Data Protection Act No. 9 of 2022 applies to health data as a special category — consult a local lawyer on consent wording, retention periods, and breach-notification duties before launch. Build consent capture and a data-retention policy into registration now.
- **Backups:** daily automated encrypted dumps to off-site storage, 30-day retention minimum, and a **quarterly restore test** — a backup you've never restored is a hope, not a backup.

---

## 8. Testing Strategy

- **Unit tests (JUnit 5 + Mockito)** on the logic that touches money and medicine: bill totals, discount math, reference-range flagging, report data assembly.
- **Integration tests (Spring Boot Test + Testcontainers)** running against a real throwaway PostgreSQL container for the critical flows: create invoice → orders created → results entered → report finalized. Testcontainers is the single biggest robustness win in the Java testing ecosystem — your tests hit a real database, not a mock.
- **API tests (MockMvc / RestAssured)** verifying RBAC: each role can reach exactly its endpoints and nothing more.
- **End-to-end smoke test (Playwright)** of the money path: register patient → bill → enter result → generate report. Run on every deploy.
- **Manual UAT with actual reception and lab staff** before each phase goes live — they will find workflow problems no test suite can.
- **Print testing** on the lab's real printers early in Phase 1; verify the JasperReports output at actual paper size.

---

## 9. Deployment & Operations

- **Artifact:** one fat JAR (`mvn package`) — the entire backend is a single file to deploy. Run under systemd or Docker.
- **Environments:** local dev → staging → production. Never test on production patient data.
- **CI/CD:** GitHub Actions — `mvn verify` (compile, tests, integration tests) on every push; deploy on merge to main. Flyway runs migrations automatically at startup, with a backup taken immediately before each deploy.
- **Monitoring:** Spring Boot Actuator health endpoints wired to an uptime check (UptimeRobot), error tracking (Sentry's Java SDK), disk/CPU alerts on the VPS.
- **JVM sizing:** 1–2 GB heap is ample at launch; a 4 GB VPS runs the app and leaves headroom.
- **Runbook:** a one-page document for "the system is down" — how to check logs (`journalctl -u lab-api`), restart the service, restore a backup. Write it before you need it.
- **Estimated running cost:** roughly USD 30–70/month at launch (slightly larger VPS than a Node deployment would need, + managed Postgres + email + backup storage); WhatsApp API adds per-conversation fees.

---

## 10. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Reception adoption failure (staff revert to paper) | Fatal | Co-design the POS screen with receptionists; optimize for speed; parallel-run before cutover |
| Spring learning curve stalls early progress | Delays MVP | Follow the build order strictly; lean on Spring Initializr defaults; resist customizing the framework early |
| WhatsApp API approval delays | Delays Phase 2 | Apply in Phase 1; email + print are fallbacks |
| Data breach of medical records | Existential (legal + reputational) | §7 controls from day one; least-privilege access; audit logs |
| Result-entry errors | Patient harm | Reference-range validation, mandatory units display, optional second-person verification step |
| Scope creep into analytics before MVP is stable | Burn without revenue | Strict phase gates; nothing from Phase 3 starts until the lab runs daily on Phase 1 |
| Solo-founder bus factor | Operations halt | Documented runbook, infrastructure-as-code notes, off-site backups |
| Printer/hardware incompatibility | Blocks reception | Test dual-bill printing on real hardware in week 3, not week 8 |

---

## 11. Success Metrics

- Time to bill a returning patient: **< 30 seconds**
- Report turnaround (sample → delivered): measured and trending down
- System uptime during lab hours: **> 99.5%**
- % of reports delivered digitally (email/WhatsApp) by end of Phase 2: **> 50%**
- Zero unauthorized-access incidents; quarterly restore test passed

---

## 12. Immediate Next Steps

1. Install JDK 21 and generate the project skeleton at start.spring.io (see the instructions document).
2. Set up the repo, staging server, and Postgres instance (Phase 0).
3. Collect 3–5 real report samples and the current paper bill from the lab — these define your JasperReports templates.
4. Apply for WhatsApp Business Cloud API access now.
5. Book a short consultation on Sri Lanka PDPA compliance for health data.
6. Schedule 30 minutes with a receptionist and a lab technician to walk through their current workflow before designing the POS screen.
