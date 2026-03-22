import { test, expect } from '@playwright/test';

/**
 * Live UI test: real browser against a running portal + API gateway + IAM + global-search
 * (and entity-builder for record hits). No mocks.
 *
 * Env (optional; defaults match IAM `default-bootstrap` seed in README):
 *   E2E_TENANT_SLUG   default `ai`
 *   E2E_USER_EMAIL     default `superadmin@ai.com`
 *   E2E_USER_PASSWORD  default `SuperAdminDev123!`
 *   PORTAL_BASE_URL    default `http://localhost:5173`
 *
 * Prereqs: CockroachDB, IAM, entity-builder, global-search, api-gateway, and either
 * `npm run dev` for the portal or `PORTAL_E2E_SERVE=1` on the test command.
 */
const tenant = process.env.E2E_TENANT_SLUG ?? 'ai';
const email = process.env.E2E_USER_EMAIL ?? 'superadmin@ai.com';
const password = process.env.E2E_USER_PASSWORD ?? 'SuperAdminDev123!';

test.describe.serial('Global search omnibox (live)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Tenant slug or ID').fill(tenant);
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/home$/);
  });

  test('opens from header trigger and GET /v1/search/omnibox returns 200', async ({ page }) => {
    await page.locator('.global-search-trigger').click();
    await expect(page.getByRole('dialog', { name: 'Global search' })).toBeVisible();

    const respPromise = page.waitForResponse(
      (r) =>
        r.url().includes('/v1/search/omnibox') &&
        r.request().method() === 'GET' &&
        new URL(r.url()).searchParams.get('q')?.length === 2 &&
        r.status() === 200
    );

    await page.getByPlaceholder('Search navigation and records…').fill('en');
    const resp = await respPromise;

    expect(resp.status(), `expected omnibox 200, got ${resp.status()} for ${resp.url()}`).toBe(200);
    await expect(page.locator('.global-search-hint.text-error')).not.toBeVisible();
  });

  test('opens from keyboard shortcut and shows no client-side error for a second query', async ({
    page,
  }) => {
    // Chromium on Windows binds Ctrl+K to the browser URL bar; use ⌘K on macOS only.
    const isMac = process.platform === 'darwin';
    if (isMac) {
      await page.keyboard.press('Meta+K');
    } else {
      await page.locator('.global-search-trigger').click();
    }
    await expect(page.getByRole('dialog', { name: 'Global search' })).toBeVisible();

    const respPromise = page.waitForResponse(
      (r) =>
        r.url().includes('/v1/search/omnibox') &&
        r.request().method() === 'GET' &&
        new URL(r.url()).searchParams.get('q') === 'ap' &&
        r.status() === 200
    );

    await page.getByPlaceholder('Search navigation and records…').fill('ap');
    const resp = await respPromise;

    expect(resp.status(), `expected omnibox 200, got ${resp.status()}`).toBe(200);
    await expect(page.locator('.global-search-hint.text-error')).not.toBeVisible();
  });
});
