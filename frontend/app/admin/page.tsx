"use client";

import { useCallback, useEffect, useState } from "react";
import Nav from "@/components/Nav";
import { api, ApiError } from "@/lib/api";

interface DayPoint {
  day: string;
  patients: number;
  revenue: string | number;
}

interface Stats {
  patientsToday: number;
  revenueToday: string | number;
  pendingOrders: number;
  completedToday: number;
  last14Days: DayPoint[];
}

interface Summary {
  from: string;
  to: string;
  revenueByMethod: Record<string, number>;
  revenueByCategory: Record<string, number>;
  expensesByCategory: Record<string, number>;
  totalRevenue: number;
  totalExpenses: number;
  net: number;
}

const EXPENSE_CATEGORIES = ["SALARY", "KITS", "EQUIPMENT", "UTILITY", "OTHER"];

function Tile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}

function MoneyRows({ rows }: { rows: Record<string, number> }) {
  const entries = Object.entries(rows);
  if (entries.length === 0) return <p className="text-sm text-gray-400">None</p>;
  return (
    <dl className="space-y-1 text-sm">
      {entries.map(([k, v]) => (
        <div key={k} className="flex justify-between">
          <dt className="text-gray-500">{k}</dt>
          <dd className="tabular-nums">{Number(v).toFixed(2)}</dd>
        </div>
      ))}
    </dl>
  );
}

export default function AdminPage() {
  const [stats, setStats] = useState<Stats | null>(null);
  const [monthly, setMonthly] = useState<Summary | null>(null);
  const [error, setError] = useState("");
  const [expense, setExpense] = useState({
    category: "KITS",
    description: "",
    amount: "",
    expenseDate: new Date().toISOString().slice(0, 10),
  });

  const load = useCallback(() => {
    api<Stats>("/admin/stats").then(setStats).catch((e) => setError(e.message));
    api<Summary>(`/finance/monthly?month=${new Date().toISOString().slice(0, 7)}`)
      .then(setMonthly)
      .catch(() => {});
  }, []);

  useEffect(load, [load]);

  async function addExpense(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      await api("/finance/expenses", {
        method: "POST",
        body: JSON.stringify({ ...expense, amount: Number(expense.amount) }),
      });
      setExpense({ ...expense, description: "", amount: "" });
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to add expense");
    }
  }

  const maxRevenue = Math.max(1, ...(stats?.last14Days ?? []).map((d) => Number(d.revenue)));

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-6xl space-y-6 p-6">
        <h1 className="text-lg font-semibold">Dashboard</h1>
        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Tile label="Patients today" value={String(stats?.patientsToday ?? "—")} />
          <Tile
            label="Revenue today"
            value={stats ? Number(stats.revenueToday).toFixed(2) : "—"}
          />
          <Tile label="Pending orders" value={String(stats?.pendingOrders ?? "—")} />
          <Tile label="Results completed today" value={String(stats?.completedToday ?? "—")} />
        </div>

        <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
          <h2 className="mb-3 font-semibold">Revenue — last 14 days</h2>
          <div className="flex h-40 items-end gap-1">
            {(stats?.last14Days ?? []).map((d) => (
              <div
                key={d.day}
                className="group relative flex-1"
                title={`${d.day}: ${Number(d.revenue).toFixed(2)} (${d.patients} patients)`}
              >
                <div
                  className="mx-auto w-full rounded-t bg-blue-600 transition-colors group-hover:bg-blue-700"
                  style={{ height: `${(Number(d.revenue) / maxRevenue) * 152}px` }}
                />
              </div>
            ))}
          </div>
          <div className="mt-1 flex gap-1 text-[10px] text-gray-400">
            {(stats?.last14Days ?? []).map((d) => (
              <span key={d.day} className="flex-1 text-center">
                {d.day.slice(8)}
              </span>
            ))}
          </div>
        </section>

        <div className="grid gap-6 lg:grid-cols-3">
          <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <h2 className="mb-3 font-semibold">This month</h2>
            {monthly ? (
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <dt className="text-gray-500">Revenue</dt>
                  <dd className="tabular-nums">{Number(monthly.totalRevenue).toFixed(2)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Expenses</dt>
                  <dd className="tabular-nums">{Number(monthly.totalExpenses).toFixed(2)}</dd>
                </div>
                <div className="flex justify-between border-t border-gray-200 pt-2 font-semibold dark:border-gray-800">
                  <dt>Net</dt>
                  <dd className="tabular-nums">{Number(monthly.net).toFixed(2)}</dd>
                </div>
              </dl>
            ) : (
              <p className="text-sm text-gray-400">Loading…</p>
            )}
          </section>

          <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <h2 className="mb-3 font-semibold">Revenue by category</h2>
            {monthly && <MoneyRows rows={monthly.revenueByCategory} />}
          </section>

          <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <h2 className="mb-3 font-semibold">Expenses by category</h2>
            {monthly && <MoneyRows rows={monthly.expensesByCategory} />}
          </section>
        </div>

        <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
          <h2 className="mb-3 font-semibold">Add expense</h2>
          <form onSubmit={addExpense} className="flex flex-wrap items-end gap-3 text-sm">
            <div>
              <label className="mb-1 block text-xs text-gray-500">Category</label>
              <select
                value={expense.category}
                onChange={(e) => setExpense({ ...expense, category: e.target.value })}
                className="rounded border border-gray-300 px-2 py-2 dark:border-gray-700 dark:bg-gray-800"
              >
                {EXPENSE_CATEGORIES.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </div>
            <div className="grow">
              <label className="mb-1 block text-xs text-gray-500">Description</label>
              <input
                value={expense.description}
                onChange={(e) => setExpense({ ...expense, description: e.target.value })}
                className="w-full rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-gray-500">Amount</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                required
                value={expense.amount}
                onChange={(e) => setExpense({ ...expense, amount: e.target.value })}
                className="w-32 rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-gray-500">Date</label>
              <input
                type="date"
                required
                value={expense.expenseDate}
                onChange={(e) => setExpense({ ...expense, expenseDate: e.target.value })}
                className="rounded border border-gray-300 px-3 py-2 dark:border-gray-700 dark:bg-gray-800"
              />
            </div>
            <button className="rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700">
              Add
            </button>
          </form>
        </section>
      </main>
    </div>
  );
}
