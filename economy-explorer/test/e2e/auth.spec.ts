import { test, expect, type BrowserContext } from '@playwright/test';

// These require the app running with E2E_TEST_AUTH=1 against the seeded test DB
// (the CI e2e job sets E2E_AUTH_SHIM=1 to enable them). Skipped otherwise so a
// run against a real deployment only exercises the public specs.
test.skip(!process.env.E2E_AUTH_SHIM, 'auth shim not enabled (set E2E_AUTH_SHIM=1 with a seeded test app)');

const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:3000';

async function loginAs(context: BrowserContext, role: 'player' | 'admin' | 'government') {
  await context.addCookies([{ name: 'e2e_viewer', value: role, url: baseURL }]);
}

test.describe('admin', () => {
  test.beforeEach(({ context }) => loginAs(context, 'admin'));

  test('can open the whole-economy transactions list', async ({ page }) => {
    await page.goto('/transactions');
    await expect(page.locator('.privacy-gate')).toHaveCount(0);
    await expect(page.locator('table.data-table')).toBeVisible();
  });

  test('can open admin API keys', async ({ page }) => {
    await page.goto('/admin/api-keys');
    await expect(page.locator('.privacy-gate')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'API keys' })).toBeVisible();
  });

  test('sees the staff-only Sales tab in the market nav', async ({ page }) => {
    await page.goto('/market');
    await expect(page.locator('.section-tabs a', { hasText: 'Sales' })).toBeVisible();
  });

  test('privileged reads are written to the audit log', async ({ page }) => {
    await page.goto('/transactions'); // a privileged read of everyone's transactions
    await page.goto('/admin/audit');
    await expect(page.locator('table.data-table')).toContainText('/transactions');
  });
});

test.describe('player own-data', () => {
  test.beforeEach(({ context }) => loginAs(context, 'player')); // shim = Alice, owns account #1

  test('sees their own account history', async ({ page }) => {
    await page.goto('/accounts/1');
    await expect(page.locator('.privacy-gate')).toHaveCount(0);
    // account-detail renders several tables (txns, counterparties, …) when
    // history is visible — assert at least one is present.
    await expect(page.locator('table.data-table').first()).toBeVisible();
  });

  test('cannot see another account’s history', async ({ page }) => {
    await page.goto('/accounts/2'); // Bob's account
    await expect(page.locator('.privacy-gate')).toBeVisible();
  });
});
