# Manual Tasks — Your Action Required

These are the things **only you can do** — they need external accounts, credentials,
hardware, money, legal advice, or human judgment. The code that consumes each of these
is (or will be) built and waiting; these steps turn features on or make them
production-ready.

> Legend: 🔴 blocks go-live · 🟠 needed within the phase · 🟢 nice-to-have / later

---

## 1. Accounts & credentials

### 🔴 SMTP for email report delivery
The code is done and config-gated. To turn it on, set these env vars where the app runs:
- `MAIL_ENABLED=true`
- `MAIL_HOST`, `MAIL_PORT` (587 for STARTTLS)
- `MAIL_USERNAME`, `MAIL_PASSWORD`
- `MAIL_FROM` (e.g. `reports@yourlab.lk`)

Options: a Gmail account with an **app password** (quickest), **Mailtrap** for staging
tests, or a transactional provider (Resend/SES/Postmark) via SMTP. Verify SPF/DKIM on
your sending domain or reports will land in spam.

### 🟠 WhatsApp Business Cloud API (Meta) — apply NOW, approval is slow
Report-delivery code parallels email and is config-gated. To enable you must:
1. Create a **Meta Business account** and a WhatsApp Business app.
2. Get a **phone number ID** and a **permanent access token**.
3. Register and get approval for a **message template** for document delivery.
4. Set env vars: `WHATSAPP_ENABLED=true`, `WHATSAPP_PHONE_NUMBER_ID`,
   `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_API_BASE` (default is Meta's Graph API).

Meta approval can take days–weeks. Email + print are the fallbacks meanwhile.

---

## 2. Legal & compliance

### 🔴 Sri Lanka PDPA (Act No. 9 of 2022) consultation
Health data is a special category. Before launch, get a local lawyer to confirm:
- Consent wording captured at registration (email/WhatsApp consent checkboxes exist).
- Data-retention periods and a documented retention policy.
- Breach-notification duties.

The system already captures consent and audits access; the **wording and policy** are
a legal deliverable you must sign off.

---

## 3. Real-world artifacts (needed to finish features)

### 🔴 Collect 3–5 real report samples + the current paper bill
The report/bill PDFs are currently generated with OpenPDF as a **functional placeholder**.
To produce pixel-perfect, lab-branded output (JasperReports templates), the developer
needs your actual paper forms. Until then the placeholder is fully usable but generic.
→ Bring physical copies / scans.

### 🔴 Print testing on the real printer (week 3, not week 8)
Test dual bill printing (A5) and the A4 report on the **lab's actual thermal/A5 printer**
early. Confirm margins, paper size, and legibility on real hardware. Report any layout
issues so the templates can be adjusted.

### 🟠 Lab letterhead details
Set the real values (env vars, defaults shown):
- `LAB_NAME`, `LAB_ADDRESS`, `LAB_PHONE`

---

## 4. Infrastructure & operations

### 🔴 Provision hosting + database
- A VPS (Hetzner/DigitalOcean, ~4 GB RAM) **or** container host.
- **Managed PostgreSQL 16** recommended (or self-hosted with full-disk encryption).
- HTTPS via Let's Encrypt; enable HSTS.
- Set DB env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD` (do NOT ship the dev defaults).

### 🔴 Change all seeded credentials
Dev seed users are `admin@ / reception@ / lab@lab.local` with a default password.
- Set `APP_SEED_ADMIN_PASSWORD` (or disable the seeder) before production.
- Rotate/replace the seeded accounts with real staff accounts.

### 🔴 Automated encrypted backups + a tested restore
- Daily `pg_dump` → off-site (S3 / Backblaze B2), **encrypted**, 30-day retention.
- **Quarterly restore drill** — an untested backup is not a backup.
- (Infra/ops task; the app doesn't do this itself.)

### 🟠 Monitoring & alerting
- Uptime check on `/actuator/health` (UptimeRobot or similar).
- Error tracking (Sentry Java SDK) — needs a Sentry account/DSN.
- Disk/CPU alerts on the host.

### 🟠 CI secrets
A GitHub Actions workflow is committed. For deploy-on-merge you must add repo secrets
(server SSH key, host, DB creds for migrations). CI runs tests without secrets already.

---

## 5. People & process

### 🔴 UAT with real reception + lab staff
Sit actual staff in front of the POS and worklist before each phase goes live. They find
workflow problems no test catches. Run the new system **in parallel** with the old paper
process for 1–2 weeks before cutover.

### 🟠 Workflow walkthrough before design tweaks
30 minutes each with a receptionist and a lab technician to validate the screens match
how they actually work.

---

## 6. Quick pre-launch checklist
- [ ] SMTP configured + a test email received (not in spam)
- [ ] WhatsApp API approved (or accept print/email-only at launch)
- [ ] PDPA consent wording + retention policy signed off by a lawyer
- [ ] Real report/bill samples collected; templates finalized
- [ ] Dual-bill + report printed correctly on the real printer
- [ ] Production VPS + managed Postgres provisioned, HTTPS/HSTS on
- [ ] Seeded passwords changed; real staff accounts created
- [ ] Encrypted off-site backups running; one restore tested
- [ ] Uptime + error monitoring live
- [ ] UAT done; 1–2 week parallel run scheduled

---

_The code side is tracked in [PROGRESS.md](PROGRESS.md). This file is only the tasks that
need you, a person, an account, or hardware._
