"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Nav from "@/components/Nav";
import { api, ApiError, LabTest, TestTemplate } from "@/lib/api";

interface TestForm {
  code: string;
  name: string;
  category: string;
  price: string;
  specimenType: string;
  templateId: string;
  isActive: boolean;
}

const EMPTY: TestForm = {
  code: "",
  name: "",
  category: "",
  price: "",
  specimenType: "",
  templateId: "",
  isActive: true,
};

export default function CatalogPage() {
  const [tests, setTests] = useState<LabTest[]>([]);
  const [templates, setTemplates] = useState<TestTemplate[]>([]);
  const [editingId, setEditingId] = useState<number | "new" | null>(null);
  const [form, setForm] = useState<TestForm>(EMPTY);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState("");

  const load = useCallback(() => {
    // /tests/all is admin-only and includes inactive tests.
    api<LabTest[]>("/catalog/tests/all")
      .then(setTests)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load tests"));
    api<TestTemplate[]>("/catalog/templates")
      .then(setTemplates)
      .catch(() => {});
  }, []);

  useEffect(load, [load]);

  const templateName = useMemo(() => {
    const m = new Map(templates.map((t) => [t.id, t.name]));
    return (id: number) => m.get(id) ?? `#${id}`;
  }, [templates]);

  const filtered = useMemo(
    () =>
      tests.filter(
        (t) =>
          !filter ||
          [t.name, t.code, t.category].some((v) =>
            v.toLowerCase().includes(filter.toLowerCase()),
          ),
      ),
    [tests, filter],
  );

  function startNew() {
    setForm({ ...EMPTY, templateId: templates[0] ? String(templates[0].id) : "" });
    setEditingId("new");
    setError("");
  }

  function startEdit(t: LabTest) {
    setForm({
      code: t.code,
      name: t.name,
      category: t.category,
      price: String(t.price),
      specimenType: t.specimenType ?? "",
      templateId: String(t.templateId),
      isActive: t.active,
    });
    setEditingId(t.id);
    setError("");
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    const body = JSON.stringify({
      code: form.code,
      name: form.name,
      category: form.category,
      price: Number(form.price),
      specimenType: form.specimenType || null,
      templateId: Number(form.templateId),
      isActive: form.isActive,
    });
    try {
      if (editingId === "new") {
        await api<LabTest>("/catalog/tests", { method: "POST", body });
      } else {
        await api<LabTest>(`/catalog/tests/${editingId}`, { method: "PUT", body });
      }
      setEditingId(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save");
    }
  }

  async function toggleActive(t: LabTest) {
    setError("");
    try {
      await api<LabTest>(`/catalog/tests/${t.id}`, {
        method: "PUT",
        body: JSON.stringify({
          code: t.code,
          name: t.name,
          category: t.category,
          price: Number(t.price),
          specimenType: t.specimenType ?? null,
          templateId: t.templateId,
          isActive: !t.active,
        }),
      });
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to update");
    }
  }

  const input =
    "w-full rounded border border-gray-300 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-800";

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-950">
      <Nav />
      <main className="mx-auto max-w-5xl space-y-6 p-6">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-semibold">Test catalog</h1>
          <button
            onClick={startNew}
            disabled={templates.length === 0}
            className="rounded bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-40"
          >
            + New test
          </button>
        </div>
        {templates.length === 0 && (
          <p className="text-sm text-amber-600">
            No result templates exist yet — a test needs a template before it can be created.
          </p>
        )}
        {error && <p className="text-sm text-red-600">{error}</p>}

        {editingId !== null && (
          <form
            onSubmit={save}
            className="grid grid-cols-2 gap-3 rounded-xl border border-gray-200 bg-white p-5 text-sm dark:border-gray-800 dark:bg-gray-900"
          >
            <h2 className="col-span-2 font-semibold">
              {editingId === "new" ? "New test" : "Edit test"}
            </h2>
            <div>
              <label className="mb-1 block text-xs text-gray-500">Code *</label>
              <input
                required
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                className={input}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-gray-500">Name *</label>
              <input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className={input}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-gray-500">Category *</label>
              <input
                required
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value })}
                className={input}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-gray-500">Price *</label>
              <input
                required
                type="number"
                min="0"
                step="0.01"
                value={form.price}
                onChange={(e) => setForm({ ...form, price: e.target.value })}
                className={input}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-gray-500">Specimen type</label>
              <input
                value={form.specimenType}
                onChange={(e) => setForm({ ...form, specimenType: e.target.value })}
                className={input}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-gray-500">Result template *</label>
              <select
                required
                value={form.templateId}
                onChange={(e) => setForm({ ...form, templateId: e.target.value })}
                className={input}
              >
                <option value="">—</option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </div>
            <label className="col-span-2 flex items-center gap-2">
              <input
                type="checkbox"
                checked={form.isActive}
                onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
              />
              Active (available in POS)
            </label>
            <div className="col-span-2 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEditingId(null)}
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

        <input
          placeholder="Filter tests…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          className={input}
        />

        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-gray-500 dark:bg-gray-800">
              <tr>
                <th className="px-4 py-2">Code</th>
                <th className="px-4 py-2">Name</th>
                <th className="px-4 py-2">Category</th>
                <th className="px-4 py-2">Template</th>
                <th className="px-4 py-2 text-right">Price</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {filtered.map((t) => (
                <tr
                  key={t.id}
                  className={t.active ? "" : "text-gray-400"}
                >
                  <td className="px-4 py-2 font-mono text-xs">{t.code}</td>
                  <td className="px-4 py-2">{t.name}</td>
                  <td className="px-4 py-2">{t.category}</td>
                  <td className="px-4 py-2">{templateName(t.templateId)}</td>
                  <td className="px-4 py-2 text-right">{Number(t.price).toFixed(2)}</td>
                  <td className="px-4 py-2">
                    <span
                      className={
                        t.active
                          ? "text-green-600"
                          : "text-gray-400"
                      }
                    >
                      {t.active ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-right">
                    <button
                      onClick={() => startEdit(t)}
                      className="mr-2 text-blue-600 hover:underline"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => toggleActive(t)}
                      className="text-gray-500 hover:underline"
                    >
                      {t.active ? "Deactivate" : "Activate"}
                    </button>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-gray-400">
                    No tests
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <section className="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
          <h2 className="mb-2 font-semibold">Result templates</h2>
          <p className="mb-3 text-xs text-gray-500">
            Templates define the fields, units, and reference ranges a test's result form uses.
          </p>
          <ul className="space-y-2 text-sm">
            {templates.map((t) => (
              <li key={t.id} className="rounded border border-gray-100 p-3 dark:border-gray-800">
                <p className="font-medium">{t.name}</p>
                <p className="mt-1 text-xs text-gray-500">
                  {t.fields.map((f) => f.label).join(" · ")}
                </p>
              </li>
            ))}
            {templates.length === 0 && <li className="text-gray-400">No templates</li>}
          </ul>
        </section>
      </main>
    </div>
  );
}
