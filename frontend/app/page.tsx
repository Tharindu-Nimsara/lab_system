"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { api, Me } from "@/lib/api";

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    api<Me>("/auth/me")
      .then((me) => router.replace(me.role === "LAB_STAFF" ? "/worklist" : "/pos"))
      .catch(() => router.replace("/login"));
  }, [router]);

  return (
    <main className="flex min-h-screen items-center justify-center text-sm text-gray-500">
      Loading…
    </main>
  );
}
