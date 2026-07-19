import { defineConfig, devices } from "@playwright/test";

/**
 * E2E smoke tests for the money path (plan §8). These are full-stack: the Spring
 * Boot backend (port 4000) with a seeded DB MUST already be running, and this
 * config starts the Next.js dev server (port 3000) automatically.
 *
 * Run: `npm run test:e2e` (after starting the backend). Not part of `next build`.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
