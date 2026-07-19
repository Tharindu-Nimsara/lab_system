"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Nav from "@/components/Nav";
import { api, ApiError } from "@/lib/api";

interface AuditRow {
  id: number;
  user_name: string;
  action: string;
  entity: string;
  entity_id: number | null;
  ip: string | null;
  created_at: string;
}

const ACTION_COLORS: Record<string, string> = {
  LOGIN: "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300",
  CREATE: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  UPDATE: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  VIEW: "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400",
  VOID: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  MERGE: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  FINALIZE: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200",
  SEND_EMAIL: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200",
  ACKNOWLEDGED: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  DISMISSED: "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400",
};

export default function AuditPage() {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [error, setError] = useState("");
  const [actionFilter, setActionFilter] = useState("");
  const [query, setQuery] = useState("");

  const load = useCallback(() => {
    api<AuditRow[]>("/admin/audit")
      .then(setRows)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load audit log"));
  }, []);

  useEffect(load, [load]);

  const actions = useMemo(
    () => Array.from(new Set(rows.map((r) => r.action))).sort(),
    [rows],
  );

  const filtered = useMemo(
    () =>
      rows.filter((r) => {
        if (actionFilter && r.action !== actionFilter) return false;
        if (query) {
          const q = query.toLowerCase();
          return (
            r.user_name?.toLowerCase().includes(q) ||
            r.entity?.toLowerCase().includes(q) ||
            String(r.entity_id ?? "").includes(q) ||
            r.ip?.toLowerCase().includes(q)
          );
        }
        return true;
      }),
    [rows, actionFilter, query],
  );

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-5xl space-y-4 p-6">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-lg font-semibold">Audit log</h1>
          <select
            value={actionFilter}
            onChange={(e) => setActionFilter(e.target.value)}
            className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <option value="">All actions</option>
            {actions.map((a) => (
              <option key={a}>{a}</option>
            ))}
          </select>
          <input
            placeholder="Filter by user, entity, IP…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="grow rounded border border-gray-300 px-3 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
          />
          <button onClick={load} className="text-sm text-blue-600 hover:underline">
            Refresh
          </button>
        </div>
        <p className="text-xs text-gray-500">
          Most recent 100 sensitive actions (who, what, when, from where). Read-only.
        </p>
        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-gray-500 dark:bg-gray-800">
              <tr>
                <th className="px-4 py-2">When</th>
                <th className="px-4 py-2">User</th>
                <th className="px-4 py-2">Action</th>
                <th className="px-4 py-2">Entity</th>
                <th className="px-4 py-2">IP</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {filtered.map((r) => (
                <tr key={r.id}>
                  <td className="whitespace-nowrap px-4 py-2 text-gray-500">
                    {new Date(r.created_at).toLocaleString()}
                  </td>
                  <td className="px-4 py-2">{r.user_name}</td>
                  <td className="px-4 py-2">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        ACTION_COLORS[r.action] ??
                        "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300"
                      }`}
                    >
                      {r.action}
                    </span>
                  </td>
                  <td className="px-4 py-2">
                    {r.entity}
                    {r.entity_id != null && (
                      <span className="text-gray-400"> #{r.entity_id}</span>
                    )}
                  </td>
                  <td className="px-4 py-2 font-mono text-xs text-gray-500">{r.ip ?? "—"}</td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                    {rows.length === 0 ? "No audit entries" : "No matches"}
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
