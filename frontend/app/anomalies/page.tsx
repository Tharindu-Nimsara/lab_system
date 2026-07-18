"use client";

import { useCallback, useEffect, useState } from "react";
import Nav from "@/components/Nav";
import { AnomalyItem, api, ApiError } from "@/lib/api";

export default function AnomaliesPage() {
  const [items, setItems] = useState<AnomalyItem[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(() => {
    api<AnomalyItem[]>("/anomalies")
      .then(setItems)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load"));
  }, []);

  useEffect(load, [load]);

  async function review(orderId: number, action: "acknowledge" | "dismiss") {
    setBusy(orderId);
    setError("");
    try {
      await api(`/anomalies/${orderId}/${action}`, { method: "POST" });
      // Drop the reviewed item locally so the queue updates instantly.
      setItems((prev) => prev.filter((i) => i.orderId !== orderId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Action failed");
    } finally {
      setBusy(null);
    }
  }

  /** Human-readable "field value (FLAG)" list for the flagged fields only. */
  function flaggedFields(item: AnomalyItem): { label: string; value: number; flag: string }[] {
    return Object.entries(item.flags).map(([key, flag]) => ({
      label: key,
      value: item.values[key],
      flag,
    }));
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-4xl space-y-4 p-6">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-semibold">
            Anomaly alerts{" "}
            {items.length > 0 && (
              <span className="rounded-full bg-red-100 px-2 py-0.5 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
                {items.length}
              </span>
            )}
          </h1>
          <button
            onClick={load}
            className="rounded border border-gray-300 px-3 py-1 text-sm hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
          >
            Refresh
          </button>
        </div>
        <p className="text-xs text-gray-500">
          Out-of-range results awaiting review. Acknowledge (seen, flagged for the doctor) or
          dismiss (not clinically significant). This is aggregate review, not diagnosis.
        </p>
        {error && <p className="text-sm text-red-600">{error}</p>}

        <ul className="space-y-3">
          {items.map((item) => (
            <li
              key={item.resultId}
              className="rounded-xl border border-amber-200 bg-white p-4 dark:border-amber-900 dark:bg-gray-900"
            >
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-medium">
                    {item.patientName}{" "}
                    <span className="font-normal text-gray-500">({item.patientNo})</span>
                  </p>
                  <p className="text-sm text-gray-500">
                    {item.testName} · {item.testCode} ·{" "}
                    {new Date(item.enteredAt).toLocaleString()}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    disabled={busy === item.orderId}
                    onClick={() => review(item.orderId, "acknowledge")}
                    className="rounded bg-blue-600 px-3 py-1 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-40"
                  >
                    Acknowledge
                  </button>
                  <button
                    disabled={busy === item.orderId}
                    onClick={() => review(item.orderId, "dismiss")}
                    className="rounded border border-gray-300 px-3 py-1 text-xs hover:bg-gray-100 disabled:opacity-40 dark:border-gray-700 dark:hover:bg-gray-800"
                  >
                    Dismiss
                  </button>
                </div>
              </div>
              <div className="mt-2 flex flex-wrap gap-2">
                {flaggedFields(item).map((f) => (
                  <span
                    key={f.label}
                    className={`rounded px-2 py-1 text-xs font-medium ${
                      f.flag === "H"
                        ? "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300"
                        : "bg-indigo-100 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-300"
                    }`}
                  >
                    {f.label}: {f.value} {f.flag === "H" ? "▲ High" : "▼ Low"}
                  </span>
                ))}
              </div>
            </li>
          ))}
          {items.length === 0 && !error && (
            <li className="rounded-xl border border-gray-200 bg-white p-8 text-center text-gray-400 dark:border-gray-800 dark:bg-gray-900">
              No open anomalies — all flagged results have been reviewed.
            </li>
          )}
        </ul>
      </main>
    </div>
  );
}
