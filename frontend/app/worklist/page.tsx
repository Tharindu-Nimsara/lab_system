"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Nav from "@/components/Nav";
import {
  api,
  ApiError,
  LabTest,
  OrderStatus,
  ResultResponse,
  TemplateField,
  TestTemplate,
  WorklistRow,
} from "@/lib/api";

const STATUSES: OrderStatus[] = ["PENDING", "COLLECTED", "IN_PROGRESS", "COMPLETED", "VERIFIED"];

const STATUS_COLORS: Record<OrderStatus, string> = {
  PENDING: "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300",
  COLLECTED: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  IN_PROGRESS: "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  COMPLETED: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  VERIFIED: "bg-emerald-200 text-emerald-900 dark:bg-emerald-800 dark:text-emerald-100",
};

export default function WorklistPage() {
  const [rows, setRows] = useState<WorklistRow[]>([]);
  const [statusFilter, setStatusFilter] = useState<OrderStatus | "">("");
  const [today, setToday] = useState(true);
  const [tests, setTests] = useState<LabTest[]>([]);
  const [templates, setTemplates] = useState<TestTemplate[]>([]);
  const [entryOrder, setEntryOrder] = useState<WorklistRow | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [lastResult, setLastResult] = useState<ResultResponse | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(() => {
    const params = new URLSearchParams();
    if (statusFilter) params.set("status", statusFilter);
    if (today) params.set("date", new Date().toISOString().slice(0, 10));
    api<WorklistRow[]>(`/orders?${params}`).then(setRows).catch((e) => setError(e.message));
  }, [statusFilter, today]);

  useEffect(load, [load]);

  useEffect(() => {
    api<LabTest[]>("/catalog/tests").then(setTests).catch(() => {});
    api<TestTemplate[]>("/catalog/templates").then(setTemplates).catch(() => {});
  }, []);

  const fieldsFor = useMemo(() => {
    return (testCode: string): TemplateField[] => {
      const test = tests.find((t) => t.code === testCode);
      const template = templates.find((tp) => tp.id === test?.templateId);
      return template?.fields ?? [];
    };
  }, [tests, templates]);

  async function advance(row: WorklistRow, target: OrderStatus) {
    setError("");
    try {
      await api(`/orders/${row.orderId}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: target }),
      });
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to update status");
    }
  }

  async function submitResult(e: React.FormEvent) {
    e.preventDefault();
    if (!entryOrder) return;
    setError("");
    const numeric: Record<string, number> = {};
    for (const [k, v] of Object.entries(values)) {
      if (v !== "") numeric[k] = Number(v);
    }
    try {
      const res = await api<ResultResponse>(`/orders/${entryOrder.orderId}/result`, {
        method: "POST",
        body: JSON.stringify({ values: numeric }),
      });
      setLastResult(res);
      setEntryOrder(null);
      setValues({});
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save result");
    }
  }

  async function verify(row: WorklistRow) {
    setError("");
    try {
      await api(`/orders/${row.orderId}/verify`, { method: "POST" });
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to verify");
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-6xl p-6">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <h1 className="text-lg font-semibold">Lab Worklist</h1>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as OrderStatus | "")}
            className="rounded border border-gray-300 px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={today} onChange={(e) => setToday(e.target.checked)} />
            Today only
          </label>
          <button onClick={load} className="text-sm text-blue-600 hover:underline">
            Refresh
          </button>
        </div>

        {error && <p className="mb-3 text-sm text-red-600">{error}</p>}
        {lastResult && (
          <p className="mb-3 rounded bg-green-50 p-2 text-sm text-green-700 dark:bg-green-950 dark:text-green-300">
            Result saved for order #{lastResult.orderId}
            {Object.keys(lastResult.flags ?? {}).length > 0 && (
              <>
                {" "}— flags:{" "}
                {Object.entries(lastResult.flags).map(([k, f]) => (
                  <span key={k} className="mr-2 font-semibold text-red-600">
                    {k}: {f}
                  </span>
                ))}
              </>
            )}
          </p>
        )}

        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-gray-500 dark:bg-gray-800">
              <tr>
                <th className="px-4 py-2">Order</th>
                <th className="px-4 py-2">Patient</th>
                <th className="px-4 py-2">Test</th>
                <th className="px-4 py-2">Invoice</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {rows.map((row) => (
                <tr key={row.orderId}>
                  <td className="px-4 py-2">#{row.orderId}</td>
                  <td className="px-4 py-2">
                    {row.patientName}{" "}
                    <span className="text-gray-400">({row.patientNo})</span>
                  </td>
                  <td className="px-4 py-2">
                    {row.testName} <span className="text-gray-400">{row.testCode}</span>
                  </td>
                  <td className="px-4 py-2">{row.invoiceNo}</td>
                  <td className="px-4 py-2">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLORS[row.status]}`}>
                      {row.status.replace("_", " ")}
                    </span>
                  </td>
                  <td className="space-x-2 px-4 py-2">
                    {row.status === "PENDING" && (
                      <button
                        onClick={() => advance(row, "COLLECTED")}
                        className="rounded border border-gray-300 px-2 py-1 text-xs hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-800"
                      >
                        Mark collected
                      </button>
                    )}
                    {row.status === "COLLECTED" && (
                      <>
                        <button
                          onClick={() => advance(row, "IN_PROGRESS")}
                          className="rounded border border-gray-300 px-2 py-1 text-xs hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-800"
                        >
                          Start
                        </button>
                        <button
                          onClick={() => {
                            setEntryOrder(row);
                            setValues({});
                          }}
                          className="rounded bg-blue-600 px-2 py-1 text-xs text-white hover:bg-blue-700"
                        >
                          Enter result
                        </button>
                      </>
                    )}
                    {row.status === "IN_PROGRESS" && (
                      <button
                        onClick={() => {
                          setEntryOrder(row);
                          setValues({});
                        }}
                        className="rounded bg-blue-600 px-2 py-1 text-xs text-white hover:bg-blue-700"
                      >
                        Enter result
                      </button>
                    )}
                    {row.status === "COMPLETED" && (
                      <button
                        onClick={() => verify(row)}
                        className="rounded bg-emerald-600 px-2 py-1 text-xs text-white hover:bg-emerald-700"
                      >
                        Verify
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                    No orders in the queue
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Result entry modal */}
        {entryOrder && (
          <div className="fixed inset-0 z-10 flex items-center justify-center bg-black/40 p-4">
            <form
              onSubmit={submitResult}
              className="w-full max-w-md space-y-3 rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900"
            >
              <h2 className="font-semibold">
                {entryOrder.testName} — {entryOrder.patientName}
              </h2>
              {fieldsFor(entryOrder.testCode).map((f) => (
                <div key={f.key}>
                  <label className="mb-1 block text-sm text-gray-600 dark:text-gray-300">
                    {f.label}
                    {f.unit && <span className="text-gray-400"> ({f.unit})</span>}
                    {(f.refLow != null || f.refHigh != null) && (
                      <span className="ml-2 text-xs text-gray-400">
                        ref {f.refLow ?? "—"}–{f.refHigh ?? "—"}
                      </span>
                    )}
                  </label>
                  <input
                    type="number"
                    step="any"
                    required
                    value={values[f.key] ?? ""}
                    onChange={(e) => setValues({ ...values, [f.key]: e.target.value })}
                    className="w-full rounded border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800"
                  />
                </div>
              ))}
              {fieldsFor(entryOrder.testCode).length === 0 && (
                <p className="text-sm text-gray-400">No template fields found for this test.</p>
              )}
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setEntryOrder(null)}
                  className="rounded border border-gray-300 px-3 py-1.5 text-sm dark:border-gray-700"
                >
                  Cancel
                </button>
                <button className="rounded bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700">
                  Save result
                </button>
              </div>
            </form>
          </div>
        )}
      </main>
    </div>
  );
}
