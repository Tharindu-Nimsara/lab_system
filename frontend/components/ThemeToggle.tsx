"use client";

import { useEffect, useState } from "react";

/**
 * Light/dark toggle. The initial class is applied before paint by the inline
 * script in layout.tsx; this button flips the `.dark` class on <html> and
 * persists the choice to localStorage.
 */
export default function ThemeToggle() {
  const [dark, setDark] = useState(false);

  // Sync state to whatever the pre-paint script already applied.
  useEffect(() => {
    setDark(document.documentElement.classList.contains("dark"));
  }, []);

  function toggle() {
    const next = !dark;
    setDark(next);
    document.documentElement.classList.toggle("dark", next);
    try {
      localStorage.setItem("theme", next ? "dark" : "light");
    } catch {
      /* ignore storage errors (private mode, etc.) */
    }
  }

  return (
    <button
      onClick={toggle}
      aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
      title={dark ? "Switch to light mode" : "Switch to dark mode"}
      className="rounded border border-gray-300 px-2 py-1 text-sm hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
    >
      {dark ? "☀️" : "🌙"}
    </button>
  );
}
