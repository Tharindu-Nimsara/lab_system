"use client";

import { useCallback, useEffect, useState } from "react";
import Nav from "@/components/Nav";
import { Analytics, api, ApiError } from "@/lib/api";

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

interface TrendPoint {
  testCode: string;
  testName: string;
  month: string;
  totalTests: number;
  abnormalCount: number;
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
  const [trends, setTrends] = useState<TrendPoint[]>([]);
  const [refreshingTrends, setRefreshingTrends] = useState(false);
  const [period, setPeriod] = useState<"week" | "month">("week");
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
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
    api<TrendPoint[]>("/admin/disease-trends?months=6").then(setTrends).catch(() => {});
  }, []);

  useEffect(load, [load]);

  useEffect(() => {
    api<Analytics>(`/admin/analytics?period=${period}`)
      .then(setAnalytics)
      .catch(() => setAnalytics(null));
  }, [period]);

  async function refreshTrends() {
    setRefreshingTrends(true);
    setError("");
    try {
      await api("/admin/disease-trends/refresh", { method: "POST" });
      const fresh = await api<TrendPoint[]>("/admin/disease-trends?months=6");
      setTrends(fresh);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to refresh trends");
    } finally {
      setRefreshingTrends(false);
    }
  }

  // Group trend points into { month → {testCode → abnormal %} } for a small table/heat view.
  const trendMonths = Array.from(new Set(trends.map((t) => t.month))).sort();
  const trendTests = Array.from(
    new Map(trends.map((t) => [t.testCode, t.testName])).entries(),
  );
  const abnormalPct = (testCode: string, month: string): number | null => {
    const p = trends.find((t) => t.testCode === testCode && t.month === month);
    if (!p || p.totalTests === 0) return null;
    return Math.round((p.abnormalCount / p.totalTests) * 100);
  };

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

  const DOW = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const busyHours = analytics?.busyHours ?? [];
  const busyMax = Math.max(1, ...busyHours.map((c) => c.count));
  const busyAt = (dow: number, hour: number) =>
    busyHours.find((c) => c.dow === dow && c.hour === hour)?.count ?? 0;
  // Only show the hour range that actually has activity (keeps the grid compact).
  const activeHours = busyHours.length
    ? {
        min: Math.min(...busyHours.map((c) => c.hour)),
        max: Math.max(...busyHours.map((c) => c.hour)),
      }
    : { min: 8, max: 18 };
  const hourList: number[] = [];
  for (let h = activeHours.min; h <= activeHours.max; h++) hourList.push(h);

  const maxTestRev = Math.max(1, ...(analytics?.revenueByTest ?? []).map((t) => Number(t.amount)));
  const maxTop = Math.max(1, ...(analytics?.topTests ?? []).map((t) => t.count));
  const totalCommission = (analytics?.revenueByLab ?? []).reduce(
    (s, l) => s + Number(l.commission),
    0,
  );

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

        {/* ---- Period analytics ---- */}
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold">
            Business analytics
            {analytics && (
              <span className="ml-2 text-xs font-normal text-gray-500">
                {analytics.from} → {analytics.to}
              </span>
            )}
          </h2>
          <div className="flex gap-1">
            {(["week", "month"] as const).map((p) => (
              <button
                key={p}
                onClick={() => setPeriod(p)}
                className={`rounded px-3 py-1 text-sm ${
                  period === p
                    ? "bg-blue-600 text-white"
                    : "border border-gray-300 dark:border-gray-700"
                }`}
              >
                {p === "week" ? "This week" : "This month"}
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Tile
            label={`Patients (${period})`}
            value={analytics ? String(analytics.patients) : "—"}
          />
          <Tile
            label="New / returning"
            value={analytics ? `${analytics.newPatients} / ${analytics.returningPatients}` : "—"}
          />
          <Tile
            label={`Revenue (${period})`}
            value={analytics ? Number(analytics.revenue).toFixed(2) : "—"}
          />
          <Tile
            label="Commission (outsourcing)"
            value={analytics ? totalCommission.toFixed(2) : "—"}
          />
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          {/* Revenue by test */}
          <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <h2 className="mb-3 font-semibold">Revenue by test</h2>
            {analytics && analytics.revenueByTest.length > 0 ? (
              <ul className="space-y-2 text-sm">
                {analytics.revenueByTest.slice(0, 8).map((t) => (
                  <li key={t.name}>
                    <div className="mb-0.5 flex justify-between">
                      <span>{t.name}</span>
                      <span className="tabular-nums text-gray-500">
                        {Number(t.amount).toFixed(2)}{" "}
                        <span className="text-gray-400">({t.count})</span>
                      </span>
                    </div>
                    <div className="h-2 rounded bg-gray-100 dark:bg-gray-800">
                      <div
                        className="h-2 rounded bg-blue-600"
                        style={{ width: `${(Number(t.amount) / maxTestRev) * 100}%` }}
                      />
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-gray-400">No revenue in this period.</p>
            )}
          </section>

          {/* Top 5 selling tests (by volume) */}
          <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <h2 className="mb-3 font-semibold">Top 5 tests (by volume)</h2>
            {analytics && analytics.topTests.length > 0 ? (
              <ul className="space-y-2 text-sm">
                {analytics.topTests.map((t, i) => (
                  <li key={t.name} className="flex items-center gap-3">
                    <span className="w-5 text-center font-semibold text-gray-400">
                      {i + 1}
                    </span>
                    <span className="flex-1">{t.name}</span>
                    <div className="h-2 w-24 rounded bg-gray-100 dark:bg-gray-800">
                      <div
                        className="h-2 rounded bg-emerald-500"
                        style={{ width: `${(t.count / maxTop) * 100}%` }}
                      />
                    </div>
                    <span className="w-8 text-right tabular-nums">{t.count}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-gray-400">No tests sold in this period.</p>
            )}
          </section>
        </div>

        {/* Revenue by outsourcing (admin only — commission) */}
        <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
          <h2 className="mb-1 font-semibold">Revenue by lab (outsourcing)</h2>
          <p className="mb-3 text-xs text-gray-500">
            Billed value routed to each lab. Commission is what we earn on outsourced tests
            (set per test per lab in the catalog) — admin only, never shown on bills.
          </p>
          {analytics && analytics.revenueByLab.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="text-left text-gray-500">
                  <tr>
                    <th className="py-1">Lab</th>
                    <th className="py-1 text-right">Billed</th>
                    <th className="py-1 text-right">Commission</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                  {analytics.revenueByLab.map((l) => (
                    <tr key={l.lab}>
                      <td className="py-1.5">
                        {l.lab}
                        {l.outsourced ? (
                          <span className="ml-2 rounded bg-purple-100 px-1.5 py-0.5 text-xs text-purple-800 dark:bg-purple-950 dark:text-purple-300">
                            outsourced
                          </span>
                        ) : (
                          <span className="ml-2 rounded bg-green-100 px-1.5 py-0.5 text-xs text-green-800 dark:bg-green-950 dark:text-green-300">
                            in-house
                          </span>
                        )}
                      </td>
                      <td className="py-1.5 text-right tabular-nums">
                        {Number(l.billed).toFixed(2)}
                      </td>
                      <td className="py-1.5 text-right tabular-nums">
                        {l.outsourced ? Number(l.commission).toFixed(2) : "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-sm text-gray-400">No lab revenue in this period.</p>
          )}
        </section>

        {/* Busy hours */}
        <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
          <h2 className="mb-1 font-semibold">Busy hours</h2>
          <p className="mb-3 text-xs text-gray-500">
            Invoices by day of week and hour — darker = busier. Helps plan reception staffing.
          </p>
          {busyHours.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="text-xs">
                <thead>
                  <tr>
                    <th className="p-1"></th>
                    {hourList.map((h) => (
                      <th key={h} className="p-1 text-center font-normal text-gray-400">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {DOW.map((label, dow) => (
                    <tr key={dow}>
                      <td className="pr-2 text-right font-medium text-gray-500">{label}</td>
                      {hourList.map((h) => {
                        const c = busyAt(dow, h);
                        const intensity = c === 0 ? 0 : 0.15 + (c / busyMax) * 0.85;
                        return (
                          <td key={h} className="p-0.5">
                            <div
                              title={`${label} ${h}:00 — ${c} invoice${c === 1 ? "" : "s"}`}
                              className="h-6 w-6 rounded"
                              style={{
                                backgroundColor:
                                  c === 0
                                    ? "transparent"
                                    : `rgba(37, 99, 235, ${intensity})`,
                                border: c === 0 ? "1px solid rgba(148,163,184,.2)" : "none",
                              }}
                            />
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-sm text-gray-400">No activity in this period yet.</p>
          )}
        </section>

        <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
          <div className="mb-3 flex items-center justify-between">
            <div>
              <h2 className="font-semibold">Disease trends — abnormal result rate</h2>
              <p className="text-xs text-gray-500">
                Share of results flagged out-of-range per test, by month. Aggregate statistics,
                not diagnosis. Recomputed nightly.
              </p>
            </div>
            <button
              onClick={refreshTrends}
              disabled={refreshingTrends}
              className="rounded border border-gray-300 px-3 py-1 text-sm hover:bg-gray-100 disabled:opacity-40 dark:border-gray-700 dark:hover:bg-gray-800"
            >
              {refreshingTrends ? "Refreshing…" : "Refresh now"}
            </button>
          </div>
          {trends.length === 0 ? (
            <p className="text-sm text-gray-400">
              No trend data yet — enter some results, then refresh (or wait for the nightly job).
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500">
                    <th className="px-2 py-1">Test</th>
                    {trendMonths.map((m) => (
                      <th key={m} className="px-2 py-1 text-center">
                        {m.slice(0, 7)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {trendTests.map(([code, name]) => (
                    <tr key={code} className="border-t border-gray-100 dark:border-gray-800">
                      <td className="px-2 py-1">
                        {name} <span className="text-gray-400">{code}</span>
                      </td>
                      {trendMonths.map((m) => {
                        const pct = abnormalPct(code, m);
                        return (
                          <td key={m} className="px-2 py-1 text-center">
                            {pct == null ? (
                              <span className="text-gray-300">—</span>
                            ) : (
                              <span
                                className="inline-block min-w-[3rem] rounded px-2 py-0.5 tabular-nums"
                                style={{
                                  // green (0%) → red (100%) abnormal rate.
                                  backgroundColor: `hsl(${Math.round(120 - pct * 1.2)}, 70%, 88%)`,
                                  color: `hsl(${Math.round(120 - pct * 1.2)}, 60%, 25%)`,
                                }}
                                title={`${pct}% abnormal`}
                              >
                                {pct}%
                              </span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
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
