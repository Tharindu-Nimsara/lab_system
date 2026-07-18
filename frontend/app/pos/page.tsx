"use client";

import { useEffect, useMemo, useState } from "react";
import Nav from "@/components/Nav";
import {
  api,
  ApiError,
  apiUrl,
  InvoiceDetail,
  LabTest,
  Patient,
} from "@/lib/api";

const EMPTY_FORM = {
  name: "",
  phone: "",
  nicOrId: "",
  age: "",
  gender: "",
  email: "",
  address: "",
  specialNote: "",
  consentEmail: false,
  consentWhatsapp: false,
};

export default function PosPage() {
  const [query, setQuery] = useState("");
  const [matches, setMatches] = useState<Patient[]>([]);
  const [patient, setPatient] = useState<Patient | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  const [tests, setTests] = useState<LabTest[]>([]);
  const [selected, setSelected] = useState<Map<number, LabTest>>(new Map());
  const [discount, setDiscount] = useState("0");
  const [paymentMethod, setPaymentMethod] = useState<"CASH" | "CARD">("CASH");
  const [testFilter, setTestFilter] = useState("");

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [lastInvoice, setLastInvoice] = useState<InvoiceDetail | null>(null);

  useEffect(() => {
    api<LabTest[]>("/catalog/tests").then(setTests).catch(() => {});
  }, []);

  useEffect(() => {
    if (query.trim().length < 2) {
      setMatches([]);
      return;
    }
    const t = setTimeout(() => {
      api<Patient[]>(`/patients?search=${encodeURIComponent(query.trim())}`)
        .then(setMatches)
        .catch(() => setMatches([]));
    }, 250);
    return () => clearTimeout(t);
  }, [query]);

  const subtotal = useMemo(
    () => [...selected.values()].reduce((s, t) => s + Number(t.price), 0),
    [selected],
  );
  const total = Math.max(0, subtotal - Number(discount || 0));

  const filteredTests = useMemo(
    () =>
      tests.filter(
        (t) =>
          !testFilter ||
          t.name.toLowerCase().includes(testFilter.toLowerCase()) ||
          t.code.toLowerCase().includes(testFilter.toLowerCase()) ||
          t.category.toLowerCase().includes(testFilter.toLowerCase()),
      ),
    [tests, testFilter],
  );

  function toggleTest(t: LabTest) {
    setSelected((prev) => {
      const next = new Map(prev);
      if (next.has(t.id)) next.delete(t.id);
      else next.set(t.id, t);
      return next;
    });
  }

  async function createPatient(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const p = await api<Patient>("/patients", {
        method: "POST",
        body: JSON.stringify({
          ...form,
          age: form.age === "" ? null : Number(form.age),
        }),
      });
      setPatient(p);
      setShowCreate(false);
      setForm(EMPTY_FORM);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create patient");
    }
  }

  async function saveInvoice() {
    if (!patient || selected.size === 0) return;
    setSaving(true);
    setError("");
    try {
      const detail = await api<InvoiceDetail>("/invoices", {
        method: "POST",
        body: JSON.stringify({
          patientId: patient.id,
          testIds: [...selected.keys()],
          discount: Number(discount || 0),
          paymentMethod,
        }),
      });
      setLastInvoice(detail);
      setSelected(new Map());
      setDiscount("0");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save invoice");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto grid max-w-6xl gap-6 p-6 lg:grid-cols-2">
        {/* Left: patient */}
        <section className="space-y-4">
          <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <h2 className="mb-2 font-semibold">1 · Patient</h2>
            {patient ? (
              <div className="flex items-start justify-between rounded-lg bg-blue-50 p-3 text-sm dark:bg-blue-950">
                <div>
                  <p className="font-semibold">
                    {patient.name}{" "}
                    <span className="font-normal text-gray-500">({patient.patientNo})</span>
                    {patient.age != null && (
                      <span className="font-normal text-gray-500"> · {patient.age} yrs</span>
                    )}
                  </p>
                  <p className="text-gray-600 dark:text-gray-300">{patient.phone}</p>
                  {patient.specialNote && (
                    <p className="mt-1 rounded bg-amber-100 px-2 py-1 text-xs text-amber-900 dark:bg-amber-900 dark:text-amber-100">
                      ⚠ {patient.specialNote}
                    </p>
                  )}
                </div>
                <button
                  onClick={() => setPatient(null)}
                  className="text-xs text-blue-600 hover:underline"
                >
                  Change
                </button>
              </div>
            ) : (
              <>
                <input
                  placeholder="Search by phone or name…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  autoFocus
                  className="w-full rounded border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
                />
                <ul className="mt-2 divide-y divide-gray-100 dark:divide-gray-800">
                  {matches.map((p) => (
                    <li key={p.id}>
                      <button
                        onClick={() => {
                          setPatient(p);
                          setQuery("");
                          setMatches([]);
                        }}
                        className="flex w-full justify-between px-2 py-2 text-left text-sm hover:bg-gray-50 dark:hover:bg-gray-800"
                      >
                        <span>
                          {p.name}{" "}
                          <span className="text-gray-400">({p.patientNo})</span>
                        </span>
                        <span className="text-gray-500">{p.phone}</span>
                      </button>
                    </li>
                  ))}
                </ul>
                <button
                  onClick={() => setShowCreate((v) => !v)}
                  className="mt-2 text-sm text-blue-600 hover:underline"
                >
                  + New patient
                </button>
                {showCreate && (
                  <form onSubmit={createPatient} className="mt-3 grid grid-cols-2 gap-2 text-sm">
                    <input
                      required
                      placeholder="Full name *"
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                      className="col-span-2 rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <input
                      required
                      placeholder="Phone *"
                      value={form.phone}
                      onChange={(e) => setForm({ ...form, phone: e.target.value })}
                      className="rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <input
                      placeholder="NIC / ID"
                      value={form.nicOrId}
                      onChange={(e) => setForm({ ...form, nicOrId: e.target.value })}
                      className="rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <input
                      type="number"
                      min="0"
                      max="150"
                      placeholder="Age"
                      value={form.age}
                      onChange={(e) => setForm({ ...form, age: e.target.value })}
                      className="rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <select
                      value={form.gender}
                      onChange={(e) => setForm({ ...form, gender: e.target.value })}
                      className="rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
                    >
                      <option value="">Gender…</option>
                      <option>Male</option>
                      <option>Female</option>
                      <option>Other</option>
                    </select>
                    <input
                      type="email"
                      placeholder="Email"
                      value={form.email}
                      onChange={(e) => setForm({ ...form, email: e.target.value })}
                      className="col-span-2 rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <textarea
                      placeholder="Special note (allergies, doctor referrals, …)"
                      value={form.specialNote}
                      onChange={(e) => setForm({ ...form, specialNote: e.target.value })}
                      rows={2}
                      className="col-span-2 rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <label className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={form.consentEmail}
                        onChange={(e) => setForm({ ...form, consentEmail: e.target.checked })}
                      />
                      Email consent
                    </label>
                    <label className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={form.consentWhatsapp}
                        onChange={(e) => setForm({ ...form, consentWhatsapp: e.target.checked })}
                      />
                      WhatsApp consent
                    </label>
                    <button className="col-span-2 rounded bg-blue-600 py-2 font-medium text-white hover:bg-blue-700">
                      Register patient
                    </button>
                  </form>
                )}
              </>
            )}
          </div>

          {/* Bill summary */}
          <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <h2 className="mb-2 font-semibold">3 · Payment</h2>
            <div className="space-y-2 text-sm">
              {[...selected.values()].map((t) => (
                <div key={t.id} className="flex justify-between">
                  <span>{t.name}</span>
                  <span>{Number(t.price).toFixed(2)}</span>
                </div>
              ))}
              <div className="flex justify-between border-t border-gray-200 pt-2 dark:border-gray-800">
                <span>Subtotal</span>
                <span>{subtotal.toFixed(2)}</span>
              </div>
              <div className="flex items-center justify-between">
                <span>Discount</span>
                <input
                  type="number"
                  min="0"
                  value={discount}
                  onChange={(e) => setDiscount(e.target.value)}
                  className="w-24 rounded border border-gray-300 px-2 py-1 text-right dark:border-gray-700 dark:bg-gray-800"
                />
              </div>
              <div className="flex justify-between text-base font-semibold">
                <span>Total</span>
                <span>{total.toFixed(2)}</span>
              </div>
              <div className="flex gap-2 pt-1">
                {(["CASH", "CARD"] as const).map((m) => (
                  <button
                    key={m}
                    onClick={() => setPaymentMethod(m)}
                    className={`rounded px-3 py-1 text-sm ${
                      paymentMethod === m
                        ? "bg-blue-600 text-white"
                        : "border border-gray-300 dark:border-gray-700"
                    }`}
                  >
                    {m}
                  </button>
                ))}
              </div>
              {error && <p className="text-red-600">{error}</p>}
              <button
                disabled={!patient || selected.size === 0 || saving}
                onClick={saveInvoice}
                className="mt-2 w-full rounded bg-green-600 py-2 font-medium text-white hover:bg-green-700 disabled:opacity-40"
              >
                {saving ? "Saving…" : "Save invoice"}
              </button>
              {lastInvoice && (
                <div className="rounded bg-green-50 p-2 text-green-700 dark:bg-green-950 dark:text-green-300">
                  <p>
                    Saved {lastInvoice.invoice.invoiceNo} — total{" "}
                    {Number(lastInvoice.invoice.total).toFixed(2)} ({lastInvoice.items.length} test
                    {lastInvoice.items.length === 1 ? "" : "s"})
                  </p>
                  <button
                    onClick={() =>
                      window.open(apiUrl(`/invoices/${lastInvoice.invoice.id}/pdf`), "_blank")
                    }
                    className="mt-1 rounded bg-green-600 px-3 py-1 text-xs font-medium text-white hover:bg-green-700"
                  >
                    Print bill (2 copies)
                  </button>
                </div>
              )}
            </div>
          </div>
        </section>

        {/* Right: test picker */}
        <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
          <h2 className="mb-2 font-semibold">2 · Tests</h2>
          <input
            placeholder="Filter tests…"
            value={testFilter}
            onChange={(e) => setTestFilter(e.target.value)}
            className="mb-3 w-full rounded border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
          />
          <ul className="max-h-[32rem] space-y-1 overflow-y-auto">
            {filteredTests.map((t) => (
              <li key={t.id}>
                <button
                  onClick={() => toggleTest(t)}
                  className={`flex w-full items-center justify-between rounded px-3 py-2 text-left text-sm ${
                    selected.has(t.id)
                      ? "bg-blue-600 text-white"
                      : "hover:bg-gray-50 dark:hover:bg-gray-800"
                  }`}
                >
                  <span>
                    <span className="font-medium">{t.name}</span>{" "}
                    <span className={selected.has(t.id) ? "text-blue-100" : "text-gray-400"}>
                      {t.code} · {t.category}
                    </span>
                  </span>
                  <span>{Number(t.price).toFixed(2)}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      </main>
    </div>
  );
}
