"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, Me } from "@/lib/api";

const LINKS: { href: string; label: string; roles: string[] }[] = [
  { href: "/pos", label: "POS", roles: ["ADMIN", "RECEPTIONIST"] },
  { href: "/patients", label: "Patients", roles: ["ADMIN", "RECEPTIONIST"] },
  { href: "/worklist", label: "Worklist", roles: ["ADMIN", "LAB_STAFF"] },
  { href: "/anomalies", label: "Anomalies", roles: ["ADMIN", "LAB_STAFF"] },
  { href: "/catalog", label: "Catalog", roles: ["ADMIN"] },
  { href: "/admin", label: "Dashboard", roles: ["ADMIN"] },
];

export default function Nav() {
  const [me, setMe] = useState<Me | null>(null);
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    api<Me>("/auth/me").then(setMe).catch(() => setMe(null));
  }, []);

  async function logout() {
    await api("/auth/logout", { method: "POST" });
    router.push("/login");
  }

  return (
    <nav className="flex items-center gap-6 border-b border-gray-200 bg-white px-6 py-3 dark:border-gray-800 dark:bg-gray-950">
      <span className="font-semibold tracking-tight">Lab System</span>
      {LINKS.filter((l) => !me || l.roles.includes(me.role)).map((l) => (
        <Link
          key={l.href}
          href={l.href}
          className={`text-sm ${
            pathname.startsWith(l.href)
              ? "font-semibold text-blue-600"
              : "text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-white"
          }`}
        >
          {l.label}
        </Link>
      ))}
      <div className="ml-auto flex items-center gap-3 text-sm">
        {me && (
          <>
            <span className="text-gray-500">
              {me.name} · {me.role}
            </span>
            <button
              onClick={logout}
              className="rounded border border-gray-300 px-2 py-1 text-xs hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-800"
            >
              Log out
            </button>
          </>
        )}
      </div>
    </nav>
  );
}
