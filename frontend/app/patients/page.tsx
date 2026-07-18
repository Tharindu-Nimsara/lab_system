"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import Nav from "@/components/Nav";
import { api, Patient } from "@/lib/api";

export default function PatientsPage() {
  const [query, setQuery] = useState("");
  const [patients, setPatients] = useState<Patient[]>([]);

  useEffect(() => {
    if (query.trim().length < 2) {
      setPatients([]);
      return;
    }
    const t = setTimeout(() => {
      api<Patient[]>(`/patients?search=${encodeURIComponent(query.trim())}`)
        .then(setPatients)
        .catch(() => setPatients([]));
    }, 250);
    return () => clearTimeout(t);
  }, [query]);

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-4xl p-6">
        <h1 className="mb-4 text-lg font-semibold">Patients</h1>
        <input
          placeholder="Search by phone or name (min 2 characters)…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          autoFocus
          className="mb-4 w-full rounded border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
        />
        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-gray-500 dark:bg-gray-800">
              <tr>
                <th className="px-4 py-2">Patient No</th>
                <th className="px-4 py-2">Name</th>
                <th className="px-4 py-2">Phone</th>
                <th className="px-4 py-2">Gender</th>
                <th className="px-4 py-2">DOB</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {patients.map((p) => (
                <tr key={p.id} className="hover:bg-gray-50 dark:hover:bg-gray-800">
                  <td className="px-4 py-2">
                    <Link href={`/patients/${p.id}`} className="text-blue-600 hover:underline">
                      {p.patientNo}
                    </Link>
                  </td>
                  <td className="px-4 py-2">{p.name}</td>
                  <td className="px-4 py-2">{p.phone}</td>
                  <td className="px-4 py-2">{p.gender ?? "—"}</td>
                  <td className="px-4 py-2">{p.dob ?? "—"}</td>
                </tr>
              ))}
              {patients.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                    {query.trim().length < 2 ? "Type to search patients" : "No matches"}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </main>
    </div>
  );
}
