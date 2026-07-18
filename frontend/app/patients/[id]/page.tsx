"use client";

import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import Nav from "@/components/Nav";
import { api, ApiError, Invoice, Patient } from "@/lib/api";

interface EditForm {
  name: string;
  nicOrId: string;
  dob: string;
  gender: string;
  phone: string;
  email: string;
  address: string;
  specialNote: string;
  consentEmail: boolean;
  consentWhatsapp: boolean;
}

export default function PatientDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [patient, setPatient] = useState<Patient | null>(null);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<EditForm | null>(null);
  const [saved, setSaved] = useState(false);

  const load = useCallback(() => {
    api<Patient>(`/patients/${id}`)
      .then(setPatient)
      .catch((e) => setError(e.message));
    api<Invoice[]>(`/invoices?patientId=${id}`)
      .then(setInvoices)
      .catch(() => {});
  }, [id]);

  useEffect(load, [load]);

  function startEdit() {
    if (!patient) return;
    setForm({
      name: patient.name,
      nicOrId: patient.nicOrId ?? "",
      dob: patient.dob ?? "",
      gender: patient.gender ?? "",
      phone: patient.phone,
      email: patient.email ?? "",
      address: patient.address ?? "",
      specialNote: patient.specialNote ?? "",
      consentEmail: patient.consentEmail,
      consentWhatsapp: patient.consentWhatsapp,
    });
    setSaved(false);
    setEditing(true);
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!form) return;
    setError("");
    try {
      const updated = await api<Patient>(`/patients/${id}`, {
        method: "PUT",
        body: JSON.stringify({ ...form, dob: form.dob || null }),
      });
      setPatient(updated);
      setEditing(false);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save");
    }
  }

  const input =
    "w-full rounded border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800";

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-4xl space-y-6 p-6">
        {error && <p className="text-sm text-red-600">{error}</p>}
        {saved && <p className="text-sm text-green-600">Patient updated.</p>}

        {patient && !editing && (
          <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
            <div className="flex items-start justify-between">
              <h1 className="text-lg font-semibold">
                {patient.name}{" "}
                <span className="text-sm font-normal text-gray-500">({patient.patientNo})</span>
              </h1>
              <button
                onClick={startEdit}
                className="rounded border border-gray-300 px-3 py-1 text-sm hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
              >
                Edit
              </button>
            </div>
            {patient.specialNote && (
              <p className="mt-2 rounded bg-amber-100 px-3 py-2 text-sm text-amber-900 dark:bg-amber-900 dark:text-amber-100">
                ⚠ {patient.specialNote}
              </p>
            )}
            <dl className="mt-3 grid grid-cols-2 gap-x-8 gap-y-2 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-gray-500">Phone</dt>
                <dd>{patient.phone}</dd>
              </div>
              <div>
                <dt className="text-gray-500">NIC / ID</dt>
                <dd>{patient.nicOrId ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-gray-500">Age</dt>
                <dd>
                  {patient.age != null ? `${patient.age} yrs` : "—"}
                  {patient.dob && (
                    <span className="text-gray-400"> (DOB {patient.dob})</span>
                  )}
                </dd>
              </div>
              <div>
                <dt className="text-gray-500">Gender</dt>
                <dd>{patient.gender ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-gray-500">Email</dt>
                <dd>{patient.email ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-gray-500">Consent</dt>
                <dd>
                  {[
                    patient.consentEmail && "Email",
                    patient.consentWhatsapp && "WhatsApp",
                  ]
                    .filter(Boolean)
                    .join(", ") || "None"}
                </dd>
              </div>
            </dl>
          </section>
        )}

        {patient && editing && form && (
          <form
            onSubmit={save}
            className="space-y-3 rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900"
          >
            <h1 className="text-lg font-semibold">Edit {patient.patientNo}</h1>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="col-span-2">
                <label className="mb-1 block text-xs text-gray-500">Full name *</label>
                <input
                  required
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className={input}
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-gray-500">Phone *</label>
                <input
                  required
                  value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                  className={input}
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-gray-500">NIC / ID</label>
                <input
                  value={form.nicOrId}
                  onChange={(e) => setForm({ ...form, nicOrId: e.target.value })}
                  className={input}
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-gray-500">
                  Date of birth (sets exact age)
                </label>
                <input
                  type="date"
                  value={form.dob}
                  onChange={(e) => setForm({ ...form, dob: e.target.value })}
                  className={input}
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-gray-500">Gender</label>
                <select
                  value={form.gender}
                  onChange={(e) => setForm({ ...form, gender: e.target.value })}
                  className={input}
                >
                  <option value="">—</option>
                  <option>Male</option>
                  <option>Female</option>
                  <option>Other</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs text-gray-500">Email</label>
                <input
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className={input}
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-gray-500">Address</label>
                <input
                  value={form.address}
                  onChange={(e) => setForm({ ...form, address: e.target.value })}
                  className={input}
                />
              </div>
              <div className="col-span-2">
                <label className="mb-1 block text-xs text-gray-500">Special note</label>
                <textarea
                  rows={2}
                  value={form.specialNote}
                  onChange={(e) => setForm({ ...form, specialNote: e.target.value })}
                  className={input}
                />
              </div>
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
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="rounded border border-gray-300 px-3 py-1.5 text-sm dark:border-gray-700"
              >
                Cancel
              </button>
              <button className="rounded bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700">
                Save
              </button>
            </div>
          </form>
        )}

        <section className="rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <h2 className="border-b border-gray-100 px-5 py-3 font-semibold dark:border-gray-800">
            Visit history
          </h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-gray-500 dark:bg-gray-800">
                <tr>
                  <th className="px-4 py-2">Invoice</th>
                  <th className="px-4 py-2">Date</th>
                  <th className="px-4 py-2">Total</th>
                  <th className="px-4 py-2">Payment</th>
                  <th className="px-4 py-2">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {invoices.map((inv) => (
                  <tr key={inv.id}>
                    <td className="px-4 py-2">{inv.invoiceNo}</td>
                    <td className="px-4 py-2">{new Date(inv.createdAt).toLocaleString()}</td>
                    <td className="px-4 py-2">{Number(inv.total).toFixed(2)}</td>
                    <td className="px-4 py-2">{inv.paymentMethod}</td>
                    <td className="px-4 py-2">
                      <span
                        className={
                          inv.status === "VOID" ? "text-red-500" : "text-green-600"
                        }
                      >
                        {inv.status}
                      </span>
                    </td>
                  </tr>
                ))}
                {invoices.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                      No visits yet
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  );
}
