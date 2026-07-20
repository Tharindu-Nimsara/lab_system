"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import Nav from "@/components/Nav";
import { api, PageResponse, Patient } from "@/lib/api";

const PAGE_SIZE = 20;

export default function PatientsPage() {
  const [query, setQuery] = useState("");
  const [patients, setPatients] = useState<Patient[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  const searching = query.trim().length >= 2;

  const loadBrowse = useCallback((p: number) => {
    setLoading(true);
    api<PageResponse<Patient>>(`/patients/browse?page=${p}&size=${PAGE_SIZE}`)
      .then((res) => {
        setPatients(res.content);
        setPage(res.page);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch(() => setPatients([]))
      .finally(() => setLoading(false));
  }, []);

  // Default view: browse all registered patients, newest first.
  useEffect(() => {
    if (!searching) loadBrowse(0);
  }, [searching, loadBrowse]);

  // While typing 2+ characters, switch to a flat search result instead.
  useEffect(() => {
    if (!searching) return;
    const t = setTimeout(() => {
      setLoading(true);
      api<Patient[]>(`/patients?search=${encodeURIComponent(query.trim())}`)
        .then(setPatients)
        .catch(() => setPatients([]))
        .finally(() => setLoading(false));
    }, 250);
    return () => clearTimeout(t);
  }, [query, searching]);

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-4xl p-6">
        <div className="mb-4 flex items-center justify-between">
          <h1 className="text-lg font-semibold">Patients</h1>
          {!searching && totalElements > 0 && (
            <span className="text-sm text-gray-500">{totalElements} registered</span>
          )}
        </div>
        <input
          placeholder="Search by phone or name (min 2 characters)… leave blank to browse all"
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
                <th className="px-4 py-2">Age</th>
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
                  <td className="px-4 py-2">{p.age != null ? `${p.age} yrs` : "—"}</td>
                </tr>
              ))}
              {patients.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                    {loading ? "Loading…" : searching ? "No matches" : "No patients registered yet"}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        {!searching && totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between text-sm">
            <button
              disabled={page <= 0}
              onClick={() => loadBrowse(page - 1)}
              className="rounded border border-gray-300 px-3 py-1 disabled:opacity-40 dark:border-gray-700"
            >
              ← Previous
            </button>
            <span className="text-gray-500">
              Page {page + 1} of {totalPages}
            </span>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => loadBrowse(page + 1)}
              className="rounded border border-gray-300 px-3 py-1 disabled:opacity-40 dark:border-gray-700"
            >
              Next →
            </button>
          </div>
        )}
      </main>
    </div>
  );
}
