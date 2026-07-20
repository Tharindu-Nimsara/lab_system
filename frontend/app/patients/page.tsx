"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import Nav from "@/components/Nav";
import { api, PageResponse, Patient } from "@/lib/api";

const PAGE_SIZE = 20;

/** Day-window tabs. `days` is passed to the browse endpoint; null = every patient. */
const TABS: { key: string; label: string; days: number | null }[] = [
  { key: "today", label: "Today", days: 1 },
  { key: "yesterday", label: "Yesterday", days: 2 },
  { key: "past3", label: "Past 3 days", days: 3 },
  { key: "all", label: "All patients", days: null },
];

function formatRegistered(iso?: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function PatientsPage() {
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState("today");
  const [patients, setPatients] = useState<Patient[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  const searching = query.trim().length >= 2;
  const activeTab = TABS.find((t) => t.key === tab) ?? TABS[0];

  const loadBrowse = useCallback(
    (p: number, days: number | null) => {
      setLoading(true);
      const daysParam = days != null ? `&days=${days}` : "";
      api<PageResponse<Patient>>(`/patients/browse?page=${p}&size=${PAGE_SIZE}${daysParam}`)
        .then((res) => {
          setPatients(res.content);
          setPage(res.page);
          setTotalPages(res.totalPages);
          setTotalElements(res.totalElements);
        })
        .catch(() => setPatients([]))
        .finally(() => setLoading(false));
    },
    []
  );

  // Default view: browse the selected day window, newest first.
  useEffect(() => {
    if (!searching) loadBrowse(0, activeTab.days);
  }, [searching, activeTab.days, loadBrowse]);

  // While typing 2+ characters, switch to a flat search result across all patients.
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
          {!searching && (
            <span className="text-sm text-gray-500">
              {totalElements} {activeTab.days == null ? "registered" : "in this window"}
            </span>
          )}
        </div>

        <input
          placeholder="Search all patients by phone or name (min 2 characters)… leave blank to browse"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          autoFocus
          className="mb-4 w-full rounded border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
        />

        {/* Day-window tabs — hidden while searching, which spans everyone. */}
        {!searching && (
          <div className="mb-4 flex flex-wrap gap-2">
            {TABS.map((t) => (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
                className={`rounded-full border px-4 py-1.5 text-sm transition ${
                  t.key === tab
                    ? "border-blue-600 bg-blue-600 text-white"
                    : "border-gray-300 bg-white text-gray-600 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:bg-gray-800"
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>
        )}

        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-gray-500 dark:bg-gray-800">
              <tr>
                <th className="px-4 py-2">Patient No</th>
                <th className="px-4 py-2">Name</th>
                <th className="px-4 py-2">Phone</th>
                <th className="px-4 py-2">Gender</th>
                <th className="px-4 py-2">Age</th>
                <th className="px-4 py-2">Registered</th>
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
                  <td className="px-4 py-2 whitespace-nowrap text-gray-500">
                    {formatRegistered(p.createdAt)}
                  </td>
                </tr>
              ))}
              {patients.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                    {loading
                      ? "Loading…"
                      : searching
                        ? "No matches"
                        : activeTab.days == null
                          ? "No patients registered yet"
                          : `No patients registered in this window (${activeTab.label.toLowerCase()})`}
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
              onClick={() => loadBrowse(page - 1, activeTab.days)}
              className="rounded border border-gray-300 px-3 py-1 disabled:opacity-40 dark:border-gray-700"
            >
              ← Previous
            </button>
            <span className="text-gray-500">
              Page {page + 1} of {totalPages}
            </span>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => loadBrowse(page + 1, activeTab.days)}
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
