# Implementation Progress

**Project:** Medical Laboratory Management System
**Stack:** Spring Boot 4 (Java 21) · PostgreSQL 16 · Next.js 16 + Tailwind
**Repo:** `origin` → https://github.com/Tharindu-Nimsara/lab_ms_frontend.git
**Active branch:** `feature/phase-1-mvp`

> Living document. Updated as work lands. Dates are ISO (yyyy-mm-dd).

---

## Legend
✅ done & verified · 🟡 partial / needs polish · ⬜ not started · 🔜 next up

---

## Phase 0 — Foundations
| Item | Status | Notes |
|---|---|---|
| Spring Boot 4 scaffold, Maven wrapper | ✅ | Boot 4 modular auto-config (starter-flyway, session-jdbc) |
| Flyway V1 schema | ✅ | All core tables + sequences |
| Spring Security session auth + RBAC | ✅ | BCrypt, SPA CSRF, `@PreAuthorize` + URL rules |
| Base React/Next layout + Nav | ✅ | POS, Worklist, Patients, Admin, Login |
| Seed data | ✅ | 1 branch, 3 users, 2 templates (FBS, Lipid) |
| Deployment pipeline to staging | ⬜ | No CI/CD yet |

## Phase 1 — MVP (Money Path)
| Item | Status | Notes |
|---|---|---|
| Test catalog CRUD — backend | ✅ | `CatalogController` tests + templates, admin-gated |
| Test catalog CRUD — **admin UI** | ✅ | `/catalog` page: create/edit/activate tests, view templates |
| Patient registration + search/auto-fill | ✅ | By age (auto-advancing), editable DOB, special note |
| Transactional POS billing + dual bill print | ✅ | `BillingService` atomic; `BillPdfService` A5 dual copy |
| Lab worklist + result entry from templates | ✅ | `WorklistController`, JSONB-driven form |
| Server-side H/L flagging | ✅ | `FlaggingService` + unit test |
| JasperReports lab report PDF | 🟡 | OpenPDF placeholder until real samples collected |
| Report finalize + print | ✅ | `ReportService` finalize gate, `ReportPdfService` A4 |
| **Merge-duplicates tool** (§5.2) | ✅ | Admin merge on patient detail; repoints invoices+reports, audited |
| **Duplicate-phone warning at registration** | ✅ | POS quick-create warns + offers existing records |
| Soft-delete for patients | ✅ | V3 `deleted_at`; search/lookup exclude deleted |

## Phase 2 — Delivery & Financials
| Item | Status | Notes |
|---|---|---|
| Expense tracking | ✅ | `FinanceController` + admin UI form |
| Daily cash-flow / Monthly P&L | ✅ | Admin-only summaries |
| Dashboard KPIs + revenue charts | ✅ | `AdminController` /stats, 14-day series |
| Email report delivery (consent-gated) | ✅ | SMTP (Spring Mail); PDF attachment; gated on email+consent; `sent_email_at` tracked |
| WhatsApp Business API delivery | ⬜ | Apply early — Meta approval slow |

## Phase 3 — Analytics & Hardening
| Item | Status | Notes |
|---|---|---|
| Audit log viewer | 🟡 | `/audit` endpoint exists; UI minimal |
| Anomaly alert queue | ✅ | `/anomalies` queue of flagged results; acknowledge/dismiss, audited |
| Disease trend charts (nightly `@Scheduled`) | ✅ | V5 `disease_trends`; nightly job + on-demand refresh; abnormal-rate heat table on dashboard |
| Login rate limiting | ✅ | In-memory throttle per IP+email; 429 + Retry-After; configurable |
| Automated encrypted backups + restore drill | ⬜ | Ops |
| Perf pass (indexes, slow queries) | ⬜ | |

## Testing & CI
| Item | Status | Notes |
|---|---|---|
| Unit tests (money + medicine logic) | ✅ | Flagging, patient-age, order-status, rate-limiter |
| Integration tests (Testcontainers) | ✅ | Money path: invoice→orders→results→report; skips w/o Docker |
| API RBAC tests (MockMvc) | ✅ | 8 tests: each role reaches only its endpoints |
| E2E smoke (Playwright) | ⬜ | Money path |
| GitHub Actions CI (`mvn verify`) | ⬜ | |

---

## Session log
- **2026-07-18** — Pushed `feature/phase-1-mvp` to remote; updated origin to renamed repo `lab_ms_frontend`. Removed 4 stale scaffold stub files. Started PROGRESS.md.
- **2026-07-18** — Shipped: patient soft-delete (V3 migration), duplicate-phone warning in POS quick-create, admin merge-duplicates tool (repoints invoices+reports, audited), and the test-catalog admin UI (`/catalog`). All 12 backend tests pass; frontend typechecks clean.
- **2026-07-18** — Hardening + tests: login rate limiting (in-memory per IP+email throttle, 429 + `Retry-After`, configurable via `app.login.*`), 8 MockMvc RBAC tests (all role boundaries), and a Testcontainers money-path integration test (real Postgres; `@EnabledIfDockerAvailable` so it skips where Docker is absent). Suite: 24 pass + 1 skipped, `BUILD SUCCESS`.
- **2026-07-18** — Anomaly alert queue (plan §5.6): V4 migration adds anomaly-review columns to `results`; `/api/anomalies` lists flagged+unreviewed results with patient/test context; acknowledge/dismiss endpoints stamp who/when/what and audit-log it (lab-staff + admin). New `/anomalies` UI page with H/L chips + Nav link. RBAC test extended; integration test now asserts the flag surfaces in the queue and clears on acknowledge.
- **2026-07-18** — Email report delivery (plan §5.4): `spring-boot-starter-mail` (SMTP, all config env-overridable). `EmailService` sends the finalized report PDF as a MIME attachment; `ReportService.emailReport` gates on finalized + email-on-file + **email consent**, stamps `sent_email_at`, and audits `SEND_EMAIL`. Endpoints `POST /reports/{id}/email` + `GET /reports/config`. Worklist page shows an "Email report" button when mail is enabled. `app.mail.enabled=false` by default so the app boots without SMTP. 3 consent-gate unit tests. Suite: 27 pass + 1 skipped.

## Config to go live with email
Set `MAIL_ENABLED=true`, `MAIL_HOST`/`MAIL_PORT`, `MAIL_USERNAME`/`MAIL_PASSWORD`, `MAIL_FROM` (e.g. Gmail app-password, or Mailtrap for staging).

## Next build order
1. Disease trend charts via nightly `@Scheduled` aggregate job
2. Audit log viewer UI (endpoint already exists)
3. GitHub Actions CI running `mvn verify`
4. WhatsApp Business API delivery (needs Meta approval — apply early)
