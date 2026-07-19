"use client";

import { useEffect, useMemo, useState } from "react";
import Nav from "@/components/Nav";
import {
  api,
  ApiError,
  apiUrl,
  InvoiceDetail,
  LabPrice,
  LabTest,
  Patient,
} from "@/lib/api";

/** A chosen test line: which lab fulfils it and at what price. */
interface SelectedLine {
  test: LabTest;
  labId: number;
  labName: string;
  outsourced: boolean;
  price: number;
}

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
  const [highlight, setHighlight] = useState(0);
  const [patient, setPatient] = useState<Patient | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  const [dupes, setDupes] = useState<Patient[]>([]);

  const [tests, setTests] = useState<LabTest[]>([]);
  const [selected, setSelected] = useState<Map<number, SelectedLine>>(new Map());
  // The test whose lab-price comparison is currently open (test-then-lab flow).
  const [labPickerTest, setLabPickerTest] = useState<LabTest | null>(null);
  const [labPrices, setLabPrices] = useState<LabPrice[]>([]);
  const [labPricesLoading, setLabPricesLoading] = useState(false);
  const [discount, setDiscount] = useState("0");
  const [payNow, setPayNow] = useState(""); // blank = pay full total
  const [paymentMethod, setPaymentMethod] = useState<"CASH" | "CARD">("CASH");
  const [testFilter, setTestFilter] = useState("");

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [lastInvoice, setLastInvoice] = useState<InvoiceDetail | null>(null);

  const [noteEditing, setNoteEditing] = useState(false);
  const [noteDraft, setNoteDraft] = useState("");
  const [noteSaving, setNoteSaving] = useState(false);

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
        .then((rows) => {
          setMatches(rows);
          setHighlight(0); // reset keyboard cursor to the top result
        })
        .catch(() => setMatches([]));
    }, 250);
    return () => clearTimeout(t);
  }, [query]);

  // Warn if the phone typed into the new-patient form already belongs to someone.
  useEffect(() => {
    const phone = form.phone.trim();
    if (!showCreate || phone.length < 4) {
      setDupes([]);
      return;
    }
    const t = setTimeout(() => {
      api<Patient[]>(`/patients/duplicates?phone=${encodeURIComponent(phone)}`)
        .then(setDupes)
        .catch(() => setDupes([]));
    }, 300);
    return () => clearTimeout(t);
  }, [form.phone, showCreate]);

  const subtotal = useMemo(
    () => [...selected.values()].reduce((s, line) => s + line.price, 0),
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

  function removeTest(testId: number) {
    setSelected((prev) => {
      const next = new Map(prev);
      next.delete(testId);
      return next;
    });
  }

  function addLine(test: LabTest, p: LabPrice) {
    setSelected((prev) => {
      const next = new Map(prev);
      next.set(test.id, {
        test,
        labId: p.labId,
        labName: p.labName,
        outsourced: p.outsourced,
        price: Number(p.price),
      });
      return next;
    });
  }

  /**
   * Test-then-lab flow: click a test → load every lab's price for it. If our
   * in-house lab offers it, add it at that price by default; then open the
   * comparison so reception can switch labs. If in-house doesn't offer it, no
   * default — the comparison stays open until a lab is chosen.
   */
  async function loadLabPrices(test: LabTest) {
    setLabPickerTest(test);
    setLabPrices([]);
    setLabPricesLoading(true);
    setError("");
    try {
      const prices = await api<LabPrice[]>(`/catalog/tests/${test.id}/prices`);
      setLabPrices(prices);
      return prices;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load lab prices");
      return [];
    } finally {
      setLabPricesLoading(false);
    }
  }

  async function pickTest(test: LabTest) {
    if (selected.has(test.id)) {
      removeTest(test.id);
      return;
    }
    const prices = await loadLabPrices(test);
    const inHouse = prices.find((p) => !p.outsourced);
    if (inHouse) addLine(test, inHouse); // default to in-house when available
  }

  /** Open the comparison for an already-selected line (to change its lab). */
  function openLabPicker(test: LabTest) {
    loadLabPrices(test);
  }

  function selectPatient(p: Patient) {
    setPatient(p);
    setQuery("");
    setMatches([]);
    setShowCreate(false);
  }

  /**
   * Open the registration form with the typed search text prefilled. Digits →
   * phone, anything else → name, so a phone search and a name search each land
   * in the right field with nothing to retype.
   */
  function openCreatePrefilled(text: string) {
    const trimmed = text.trim();
    const isPhone = trimmed.length > 0 && /^[0-9+\-\s]+$/.test(trimmed);
    setForm({
      ...EMPTY_FORM,
      name: isPhone ? "" : trimmed,
      phone: isPhone ? trimmed.replace(/[^0-9+]/g, "") : "",
    });
    setShowCreate(true);
    setMatches([]);
    // Focus the first empty of the two prefill fields after render.
    setTimeout(() => {
      const first = document.querySelector<HTMLInputElement>(
        isPhone ? "#reg-name" : "#reg-phone",
      );
      first?.focus();
    }, 0);
  }

  // Search box: ↓/↑ move the highlight, Enter selects it (or opens a prefilled
  // new-patient form when there's no match to select).
  function onSearchKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setHighlight((h) => Math.min(h + 1, matches.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (matches.length > 0 && matches[highlight]) {
        selectPatient(matches[highlight]);
      } else if (query.trim().length >= 2) {
        openCreatePrefilled(query);
      }
    }
  }

  // Registration form: Enter and ↓ move to the next field (Enter submits on the
  // last), ↑ moves to the previous field, so the whole form is navigable from the
  // keyboard. Escape cancels. Arrow keys are left to their native behavior inside
  // the number (Age) and select (Gender) fields, where they increment/choose.
  function onFormKeyDown(e: React.KeyboardEvent<HTMLFormElement>) {
    if (e.key === "Escape") {
      e.preventDefault();
      setShowCreate(false);
      document.querySelector<HTMLInputElement>("#patient-search")?.focus();
      return;
    }

    const target = e.target as HTMLElement;
    const isEnter = e.key === "Enter";
    const isDown = e.key === "ArrowDown";
    const isUp = e.key === "ArrowUp";
    if (!isEnter && !isDown && !isUp) return;

    // Textareas and the submit button keep their native keys.
    if (target.tagName === "TEXTAREA" || target.tagName === "BUTTON") return;

    // Arrow keys stay native where they mean something (spin the number, pick an
    // option); only Enter advances out of those fields.
    const isNumberOrSelect =
      target.tagName === "SELECT" ||
      (target instanceof HTMLInputElement && target.type === "number");
    if ((isDown || isUp) && isNumberOrSelect) return;

    e.preventDefault();
    const focusable = Array.from(
      e.currentTarget.querySelectorAll<HTMLElement>("[data-reg-field]"),
    );
    const idx = focusable.indexOf(target);
    if (idx === -1) return;

    if (isUp) {
      if (idx > 0) focusable[idx - 1].focus();
    } else if (idx < focusable.length - 1) {
      focusable[idx + 1].focus(); // Enter or ↓
    } else if (isEnter) {
      e.currentTarget.requestSubmit(); // Enter on the last field submits
    }
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
      setDupes([]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create patient");
    }
  }

  function startNoteEdit() {
    if (!patient) return;
    setNoteDraft(patient.specialNote ?? "");
    setNoteEditing(true);
  }

  // Save the special note inline without leaving POS (keeps reception fast).
  async function saveNote() {
    if (!patient) return;
    setNoteSaving(true);
    setError("");
    try {
      const updated = await api<Patient>(`/patients/${patient.id}`, {
        method: "PUT",
        body: JSON.stringify({
          name: patient.name,
          nicOrId: patient.nicOrId ?? null,
          dob: patient.dob ?? null,
          gender: patient.gender ?? null,
          phone: patient.phone,
          email: patient.email ?? null,
          address: patient.address ?? null,
          specialNote: noteDraft.trim() || null,
          consentEmail: patient.consentEmail,
          consentWhatsapp: patient.consentWhatsapp,
        }),
      });
      setPatient(updated);
      setNoteEditing(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save note");
    } finally {
      setNoteSaving(false);
    }
  }

  // Clear the whole POS screen back to a fresh sale.
  function resetPos() {
    setQuery("");
    setMatches([]);
    setHighlight(0);
    setPatient(null);
    setShowCreate(false);
    setForm(EMPTY_FORM);
    setDupes([]);
    setSelected(new Map());
    setLabPickerTest(null);
    setDiscount("0");
    setPayNow("");
    setPaymentMethod("CASH");
    setTestFilter("");
    setError("");
    setLastInvoice(null);
    setNoteEditing(false);
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
          lines: [...selected.values()].map((l) => ({ testId: l.test.id, labId: l.labId })),
          discount: Number(discount || 0),
          paymentMethod,
          // blank = pay the full total; a number records a partial deposit.
          amountPaid: payNow === "" ? null : Number(payNow),
        }),
      });
      setLastInvoice(detail);
      setSelected(new Map());
      setLabPickerTest(null);
      setDiscount("0");
      setPayNow("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save invoice");
    } finally {
      setSaving(false);
    }
  }

  const hasWork =
    patient !== null ||
    query !== "" ||
    showCreate ||
    selected.size > 0 ||
    lastInvoice !== null;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-6xl p-6">
        <div className="mb-4 flex items-center justify-between">
          <h1 className="text-xl font-semibold">Point of Sale</h1>
          <button
            onClick={resetPos}
            disabled={!hasWork}
            className="rounded border border-gray-300 px-3 py-1.5 text-sm hover:bg-gray-100 disabled:opacity-40 dark:border-gray-700 dark:hover:bg-gray-800"
          >
            ↺ Reset
          </button>
        </div>
        <div className="grid gap-6 lg:grid-cols-2">
        {/* Left: patient */}
        <section className="space-y-4">
          <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <h2 className="mb-2 text-lg font-semibold">1 · Patient</h2>
            {patient ? (
              <div className="rounded-lg bg-blue-50 p-3 text-base dark:bg-blue-950">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="font-semibold">
                      {patient.name}{" "}
                      <span className="font-normal text-gray-500">({patient.patientNo})</span>
                      {(patient.gender || patient.age != null) && (
                        <span className="font-normal text-gray-500">
                          {" · "}
                          {[patient.gender, patient.age != null ? `${patient.age} yrs` : null]
                            .filter(Boolean)
                            .join(" · ")}
                        </span>
                      )}
                    </p>
                    <p className="text-gray-600 dark:text-gray-300">{patient.phone}</p>
                    {patient.specialNote && (
                      <p className="mt-1 rounded bg-amber-100 px-2 py-1 text-xs text-amber-900 dark:bg-amber-900 dark:text-amber-100">
                        <span className="font-semibold">⚠ Special note:</span>{" "}
                        {patient.specialNote}
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

                {/* Quick actions */}
                <div className="mt-2 flex flex-wrap gap-2">
                  <button
                    onClick={() => window.open(`/patients/${patient.id}`, "_blank")}
                    className="rounded border border-blue-300 px-2 py-1 text-xs text-blue-700 hover:bg-blue-100 dark:border-blue-800 dark:text-blue-300 dark:hover:bg-blue-900"
                  >
                    ✎ Edit patient details
                  </button>
                  {!noteEditing && (
                    <button
                      onClick={startNoteEdit}
                      className="rounded border border-amber-300 px-2 py-1 text-xs text-amber-800 hover:bg-amber-100 dark:border-amber-700 dark:text-amber-200 dark:hover:bg-amber-900"
                    >
                      ⚠ {patient.specialNote ? "Edit special note" : "Add special note"}
                    </button>
                  )}
                </div>

                {noteEditing && (
                  <div className="mt-2">
                    <textarea
                      autoFocus
                      rows={2}
                      value={noteDraft}
                      onChange={(e) => setNoteDraft(e.target.value)}
                      placeholder="Special note (allergies, doctor referrals, …)"
                      className="w-full rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
                    />
                    <div className="mt-1 flex justify-end gap-2">
                      <button
                        onClick={() => setNoteEditing(false)}
                        className="rounded border border-gray-300 px-2 py-1 text-xs dark:border-gray-700"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={saveNote}
                        disabled={noteSaving}
                        className="rounded bg-blue-600 px-3 py-1 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-40"
                      >
                        {noteSaving ? "Saving…" : "Save note"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <>
                <input
                  id="patient-search"
                  placeholder="Search by phone or name…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onKeyDown={onSearchKeyDown}
                  autoFocus
                  className="w-full rounded border border-gray-300 px-3 py-2.5 text-lg dark:border-gray-700 dark:bg-gray-800"
                />
                {query.trim().length >= 2 && (
                  <p className="mt-1 text-xs text-gray-400">
                    ↑↓ to move · Enter to {matches.length > 0 ? "select" : "register new"}
                  </p>
                )}
                <ul className="mt-2 divide-y divide-gray-100 dark:divide-gray-800">
                  {matches.map((p, i) => (
                    <li key={p.id}>
                      <button
                        onClick={() => selectPatient(p)}
                        onMouseEnter={() => setHighlight(i)}
                        className={`flex w-full items-center justify-between px-3 py-2.5 text-left text-base ${
                          i === highlight
                            ? "bg-blue-50 dark:bg-blue-950"
                            : "hover:bg-gray-50 dark:hover:bg-gray-800"
                        }`}
                      >
                        <span>
                          {p.name}{" "}
                          <span className="text-gray-400">({p.patientNo})</span>
                          {(p.gender || p.age != null) && (
                            <span className="text-gray-400">
                              {" · "}
                              {[p.gender, p.age != null ? `${p.age} yrs` : null]
                                .filter(Boolean)
                                .join(" · ")}
                            </span>
                          )}
                        </span>
                        <span className="text-gray-500">{p.phone}</span>
                      </button>
                    </li>
                  ))}
                </ul>
                <button
                  onClick={() => openCreatePrefilled(query)}
                  className="mt-2 text-base text-blue-600 hover:underline"
                >
                  + New patient <span className="text-gray-400">(or press Enter)</span>
                </button>
                {showCreate && (
                  <form
                    onSubmit={createPatient}
                    onKeyDown={onFormKeyDown}
                    className="mt-3 grid grid-cols-2 gap-2 text-base"
                  >
                    <p className="col-span-2 text-xs text-gray-400">
                      Enter / ↓ next field · ↑ previous · Esc cancels
                    </p>
                    <input
                      id="reg-name"
                      data-reg-field
                      required
                      placeholder="Full name *"
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                      className="col-span-2 rounded border border-gray-300 px-3 py-2.5 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <input
                      id="reg-phone"
                      data-reg-field
                      required
                      placeholder="Phone *"
                      value={form.phone}
                      onChange={(e) => setForm({ ...form, phone: e.target.value })}
                      className="rounded border border-gray-300 px-3 py-2.5 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <input
                      data-reg-field
                      type="number"
                      min="0"
                      max="150"
                      placeholder="Age"
                      value={form.age}
                      onChange={(e) => setForm({ ...form, age: e.target.value })}
                      className="rounded border border-gray-300 px-3 py-2.5 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <select
                      data-reg-field
                      value={form.gender}
                      onChange={(e) => setForm({ ...form, gender: e.target.value })}
                      className="rounded border border-gray-300 px-3 py-2.5 dark:border-gray-700 dark:bg-gray-800"
                    >
                      <option value="">Gender…</option>
                      <option>Male</option>
                      <option>Female</option>
                      <option>Other</option>
                    </select>
                    <input
                      data-reg-field
                      placeholder="NIC / ID (optional)"
                      value={form.nicOrId}
                      onChange={(e) => setForm({ ...form, nicOrId: e.target.value })}
                      className="col-span-2 rounded border border-gray-300 px-3 py-2.5 dark:border-gray-700 dark:bg-gray-800"
                    />
                    {dupes.length > 0 && (
                      <div className="col-span-2 rounded border border-amber-300 bg-amber-50 p-2 text-xs text-amber-900 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-100">
                        <p className="mb-1 font-medium">
                          ⚠ {dupes.length} patient{dupes.length === 1 ? "" : "s"} already on this
                          phone — use an existing record instead of creating a duplicate?
                        </p>
                        <ul className="space-y-1">
                          {dupes.map((d) => (
                            <li key={d.id}>
                              <button
                                type="button"
                                onClick={() => {
                                  setPatient(d);
                                  setShowCreate(false);
                                  setForm(EMPTY_FORM);
                                  setDupes([]);
                                }}
                                className="w-full rounded bg-white px-2 py-1 text-left hover:bg-amber-100 dark:bg-gray-900 dark:hover:bg-gray-800"
                              >
                                <span className="font-medium">{d.name}</span>{" "}
                                <span className="text-gray-500">
                                  ({d.patientNo}){d.age != null ? ` · ${d.age} yrs` : ""}
                                </span>
                              </button>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                    <input
                      data-reg-field
                      type="email"
                      placeholder="Email (optional)"
                      value={form.email}
                      onChange={(e) => setForm({ ...form, email: e.target.value })}
                      className="col-span-2 rounded border border-gray-300 px-3 py-2.5 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <textarea
                      placeholder="Special note — optional (allergies, doctor referrals, …)"
                      value={form.specialNote}
                      onChange={(e) => setForm({ ...form, specialNote: e.target.value })}
                      rows={2}
                      className="col-span-2 rounded border border-gray-300 px-3 py-2.5 dark:border-gray-700 dark:bg-gray-800"
                    />
                    <label className="flex items-center gap-2">
                      <input
                        data-reg-field
                        type="checkbox"
                        checked={form.consentEmail}
                        onChange={(e) => setForm({ ...form, consentEmail: e.target.checked })}
                      />
                      Email consent
                    </label>
                    <label className="flex items-center gap-2">
                      <input
                        data-reg-field
                        type="checkbox"
                        checked={form.consentWhatsapp}
                        onChange={(e) => setForm({ ...form, consentWhatsapp: e.target.checked })}
                      />
                      WhatsApp consent
                    </label>
                    <button className="col-span-2 rounded bg-blue-600 py-2.5 font-medium text-white hover:bg-blue-700">
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
              {selected.size === 0 ? (
                <p className="rounded border border-dashed border-gray-300 px-3 py-2 text-center text-gray-400 dark:border-gray-700">
                  No tests selected yet
                </p>
              ) : (
                [...selected.values()].map((line) => (
                  <div
                    key={line.test.id}
                    className={`flex items-center justify-between rounded-md border px-3 py-1.5 ${
                      line.outsourced
                        ? "border-purple-300 bg-purple-50 dark:border-purple-800 dark:bg-purple-950"
                        : "border-blue-200 bg-blue-50 dark:border-blue-900 dark:bg-blue-950"
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <button
                        onClick={() => removeTest(line.test.id)}
                        aria-label={`Remove ${line.test.name}`}
                        title="Remove"
                        className="text-gray-400 hover:text-red-500"
                      >
                        ✕
                      </button>
                      <span>
                        <span className="font-medium">{line.test.name}</span>
                        <button
                          onClick={() => openLabPicker(line.test)}
                          className={`ml-2 rounded px-1.5 py-0.5 text-xs ${
                            line.outsourced
                              ? "bg-purple-200 text-purple-900 dark:bg-purple-900 dark:text-purple-100"
                              : "bg-blue-200 text-blue-900 dark:bg-blue-900 dark:text-blue-100"
                          }`}
                          title="Change lab"
                        >
                          {line.outsourced ? "⇄ " : ""}
                          {line.labName}
                        </button>
                      </span>
                    </span>
                    <span className="tabular-nums">{line.price.toFixed(2)}</span>
                  </div>
                ))
              )}
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
              <div className="flex items-center justify-between">
                <span>
                  Paying now
                  <span className="ml-1 text-xs text-gray-400">(blank = full)</span>
                </span>
                <input
                  type="number"
                  min="0"
                  max={total}
                  placeholder={total.toFixed(2)}
                  value={payNow}
                  onChange={(e) => setPayNow(e.target.value)}
                  className="w-24 rounded border border-gray-300 px-2 py-1 text-right dark:border-gray-700 dark:bg-gray-800"
                />
              </div>
              {payNow !== "" && Number(payNow) < total && (
                <div className="flex justify-between text-amber-700 dark:text-amber-400">
                  <span>Balance due</span>
                  <span className="font-medium">
                    {(total - Number(payNow || 0)).toFixed(2)}
                  </span>
                </div>
              )}
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
                  {Number(lastInvoice.invoice.balance) > 0 && (
                    <p className="font-medium text-amber-700 dark:text-amber-400">
                      Paid {Number(lastInvoice.invoice.amountPaid).toFixed(2)} · balance due{" "}
                      {Number(lastInvoice.invoice.balance).toFixed(2)}
                    </p>
                  )}
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
                  onClick={() => pickTest(t)}
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
        </div>
      </main>

      {/* Lab price comparison — pick which lab fulfils the test */}
      {labPickerTest && (
        <div
          className="fixed inset-0 z-20 flex items-center justify-center bg-black/40 p-4"
          onClick={() => setLabPickerTest(null)}
        >
          <div
            className="w-full max-w-md rounded-xl bg-white p-5 shadow-xl dark:bg-gray-900"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-1 flex items-center justify-between">
              <h3 className="font-semibold">{labPickerTest.name} — choose lab</h3>
              <button
                onClick={() => setLabPickerTest(null)}
                className="text-sm text-gray-500 hover:underline"
              >
                Close
              </button>
            </div>
            <p className="mb-3 text-xs text-gray-500">
              Prices at each lab that offers this test. Our lab is the default; outsourced
              partners are highlighted.
            </p>
            {labPricesLoading ? (
              <p className="text-sm text-gray-400">Loading…</p>
            ) : labPrices.length === 0 ? (
              <p className="text-sm text-gray-400">No labs offer this test yet.</p>
            ) : (
              <ul className="space-y-1">
                {labPrices.map((p) => {
                  const chosen = selected.get(labPickerTest.id)?.labId === p.labId;
                  return (
                    <li key={p.labId}>
                      <button
                        onClick={() => addLine(labPickerTest, p)}
                        className={`flex w-full items-center justify-between rounded-md border px-3 py-2 text-left text-sm ${
                          chosen
                            ? "border-blue-500 bg-blue-50 dark:border-blue-500 dark:bg-blue-950"
                            : p.outsourced
                              ? "border-purple-200 hover:bg-purple-50 dark:border-purple-900 dark:hover:bg-purple-950"
                              : "border-gray-200 hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-800"
                        }`}
                      >
                        <span className="flex items-center gap-2">
                          {chosen && <span className="text-blue-600">✓</span>}
                          <span className="font-medium">{p.labName}</span>
                          {p.outsourced ? (
                            <span className="rounded bg-purple-200 px-1.5 py-0.5 text-xs text-purple-900 dark:bg-purple-900 dark:text-purple-100">
                              outsourced
                            </span>
                          ) : (
                            <span className="rounded bg-green-200 px-1.5 py-0.5 text-xs text-green-900 dark:bg-green-900 dark:text-green-100">
                              our lab
                            </span>
                          )}
                        </span>
                        <span className="tabular-nums font-medium">
                          {Number(p.price).toFixed(2)}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
            <div className="mt-3 flex justify-end">
              <button
                onClick={() => setLabPickerTest(null)}
                className="rounded bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
