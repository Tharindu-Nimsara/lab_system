import { test, expect } from "@playwright/test";

/**
 * Money-path smoke test (plan §8): log in → register a patient at POS → pick a
 * test → save the invoice. Requires the backend running on :4000 with seed data
 * (admin@lab.local). Credentials overridable via E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD.
 */
const EMAIL = process.env.E2E_ADMIN_EMAIL ?? "admin@lab.local";
const PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? "ChangeMe123!";

test("reception can register a patient and bill a test", async ({ page }) => {
  // --- Log in ---
  await page.goto("/login");
  await page.getByLabel("Email").fill(EMAIL);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  // Admin lands on the POS screen.
  await expect(page).toHaveURL(/\/pos$/);
  await expect(page.getByRole("heading", { name: /Patient/ })).toBeVisible();

  // --- Register a new patient (unique phone so the run is repeatable) ---
  const phone = "07" + Date.now().toString().slice(-8);
  await page.getByRole("button", { name: "+ New patient" }).click();
  await page.getByPlaceholder("Full name *").fill("E2E Test Patient");
  await page.getByPlaceholder("Phone *").fill(phone);
  await page.getByPlaceholder("Age").fill("40");
  await page.getByRole("button", { name: "Register patient" }).click();

  // Selected patient banner shows the name + a Change link.
  await expect(page.getByText("E2E Test Patient")).toBeVisible();
  await expect(page.getByRole("button", { name: "Change" })).toBeVisible();

  // --- Pick the first available test ---
  const firstTest = page.locator("section:has-text('2 · Tests') li button").first();
  await expect(firstTest).toBeVisible();
  await firstTest.click();

  // --- Save the invoice ---
  await page.getByRole("button", { name: "Save invoice" }).click();

  // Success banner names the saved invoice.
  await expect(page.getByText(/Saved .* — total/)).toBeVisible({ timeout: 10_000 });
  await expect(page.getByRole("button", { name: /Print bill/ })).toBeVisible();
});
