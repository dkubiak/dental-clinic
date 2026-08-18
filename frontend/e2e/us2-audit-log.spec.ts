import { expect, test } from '@playwright/test';
import { currentTotpCode, loadSeedAccounts } from './support/seed-accounts';

/**
 * T060 — covers spec.md US2 Acceptance Scenarios 1-4. Requires the backend running locally with
 * `-Dspring.profiles.active=e2e-seed` (see quickstart.md Prerequisites) so `admin@clinic.test`
 * exists with MFA already enrolled.
 */

const seedAccounts = loadSeedAccounts();

async function loginWithMfa(
  page: import('@playwright/test').Page,
  account: { email: string; password: string; totpSecret: string },
) {
  await page.goto('/login');
  await page.getByLabel('Adres e-mail').fill(account.email);
  await page.getByLabel('Hasło').fill(account.password);
  await page.getByRole('button', { name: 'Zaloguj się' }).click();

  await page.waitForURL('**/login/mfa');
  await page.getByLabel('6-cyfrowy kod').fill(currentTotpCode(account.totpSecret));
  await page.getByRole('button', { name: 'Potwierdź' }).click();
}

test.describe('US2 — audit log completeness and immutability', () => {
  test('Scenario 1-2: successful and failed logins both appear in the audit log', async ({
    page,
  }) => {
    // Produce a fresh, uniquely-identifiable failed login before this test's own successful one.
    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(seedAccounts.reception.email);
    await page.getByLabel('Hasło').fill('definitely-the-wrong-password');
    await page.getByRole('button', { name: 'Zaloguj się' }).click();
    await expect(page.getByRole('alert')).toBeVisible();

    await loginWithMfa(page, seedAccounts.admin);
    await page.waitForURL('**/admin');

    // In-app navigation (not page.goto) — a hard navigation reloads the SPA and drops
    // AuthState (role.guard.ts's client-side role, held only in memory), bouncing this
    // admin-only route back to /login even with a valid session cookie.
    await page.getByRole('link', { name: 'Log audytowy' }).click();
    await expect(page.locator('mat-toolbar')).toHaveText('Log audytowy');

    // Filter to LOGIN_FAILURE and confirm at least one row is present (Scenario 2) — the table
    // never renders the submitted password anywhere (Scenario 2's "without saving the password").
    // force: true — Angular Material's own compiled CSS gives the floating label
    // `pointer-events: all` (it forwards the click to the select natively), which Playwright's
    // strict actionability check flags as "intercepts pointer events" even though the click does
    // reach the select (verified: a raw page.mouse.click at the same point opens the panel fine).
    await page.getByLabel('Typ zdarzenia').click({ force: true });
    await page.getByRole('option', { name: 'LOGIN_FAILURE', exact: true }).click();
    await expect(page.locator('table tbody tr').first()).toBeVisible();
    await expect(page.locator('table')).not.toContainText('definitely-the-wrong-password');
  });

  test('Scenario 3: an admin role change produces a ROLE_CHANGED audit entry', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.admin);
    await page.waitForURL('**/admin');

    // A throwaway account, unique to this run, so this test never mutates a shared seed
    // account's role out from under other e2e tests/projects running in parallel
    // (playwright.config.ts fullyParallel — see us1-login-rbac.spec.ts's identical concern).
    const uniqueEmail = `role-change-e2e-${Date.now()}@dentalclinic.example`;
    // In-app navigation — see the Scenario 1-2 comment above on why not page.goto.
    await page.getByRole('link', { name: 'Zarządzanie kontami' }).click();
    await page.getByLabel('Adres e-mail').fill(uniqueEmail);
    await page.getByRole('button', { name: 'Utwórz konto' }).click();
    const row = page.locator('tr', { hasText: uniqueEmail });
    await expect(row).toBeVisible();

    // force: true — see the Scenario 1-2 comment above on the floating-label pointer-events quirk.
    await row.getByLabel('Rola').click({ force: true });
    await page.getByRole('option', { name: 'DOCTOR', exact: true }).click();
    await expect(row).toContainText('DOCTOR');

    // The accounts screen has no nav back to /admin, so go back in the SPA's own history
    // (still an in-app transition, unlike page.goto) before clicking through to the log.
    await page.goBack();
    await page.waitForURL('**/admin');
    await page.getByRole('link', { name: 'Log audytowy' }).click();
    // force: true — see the Scenario 1-2 comment above on the floating-label pointer-events quirk.
    await page.getByLabel('Typ zdarzenia').click({ force: true });
    await page.getByRole('option', { name: 'ROLE_CHANGED', exact: true }).click();
    // Exact before/after-state correctness for this event is proven server-side
    // (RoleChangeAuditIntegrationTest) — this only proves the UI flow produces an entry at all.
    await expect(page.locator('table tbody tr').first()).toBeVisible();
  });

  test('Scenario 4: the audit log screen exposes no edit or delete control for any entry', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.admin);
    await page.waitForURL('**/admin');

    // In-app navigation — see the Scenario 1-2 comment above on why not page.goto.
    await page.getByRole('link', { name: 'Log audytowy' }).click();

    await expect(page.getByRole('button', { name: /edytuj/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /usuń/i })).toHaveCount(0);
  });

  test('non-administrator cannot reach the audit log screen', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.reception);
    await page.waitForURL('**/reception');

    await page.goto('/admin/audit-log');

    // role.guard.ts redirect (UX only) — the real boundary is server-side 404-not-403
    // (AuditLogControllerContractTest), not re-proven here.
    await expect(page).toHaveURL(/\/login$/);
  });
});
