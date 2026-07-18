"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import Nav from "@/components/Nav";
import { api, Invoice, Patient } from "@/lib/api";

export default function PatientDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [patient, setPatient] = useState<Patient | null>(null);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Patient>(`/patients/${id}`)
      .then(setPatient)
      .catch((e) => setError(e.message));
    api<Invoice[]>(`/invoices?patientId=${id}`)
      .then(setInvoices)
      .catch(() => {});
  }, [id]);

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
        <Nav />
        <main className="p-6 text-sm text-red-600">{error}</main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-4xl space-y-6 p-6">
        {patient && (
          <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
            <h1 className="text-lg font-semibold">
              {patient.name}{" "}
              <span className="text-sm font-normal text-gray-500">({patient.patientNo})</span>
            </h1>
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
                <dt className="text-gray-500">DOB</dt>
                <dd>{patient.dob ?? "—"}</dd>
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
