import { expect, test } from '@playwright/test';
import { readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { currentTotpCode, loadSeedAccounts } from './support/seed-accounts';

function fileSizeOrZero(filePath: string): number {
  try {
    return statSync(filePath).size;
  } catch {
    return 0;
  }
}

/**
 * T039 — covers spec.md US1 Acceptance Scenarios 1-7. Requires the backend running locally with
 * `-Dspring.profiles.active=e2e-seed` (E2eSeedRunner/E2eStubAwsConfig — see quickstart.md
 * Prerequisites) so the three role accounts exist with MFA already enrolled.
 *
 * <p>Scenarios 1-3 ("system grants access to <role>'s functions") are verified at the level this
 * feature actually builds — landing on the role-appropriate home screen after login — since the
 * appointments/medical-records/account-management screens themselves belong to later features
 * (plan.md: this feature is the RBAC/auth foundation only). Full endpoint-level 404-not-403
 * enforcement is covered server-side by RbacEnforcementIntegrationTest (T036).
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

test.describe('US1 — staff login with role-scoped access', () => {
  test('Scenario 1: reception logs in and lands on the reception home screen', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.reception);
    await page.waitForURL('**/reception');
    // The role name legitimately appears twice on this screen (toolbar title + welcome text) —
    // target the toolbar specifically to avoid a Playwright strict-mode ambiguity.
    await expect(page.locator('mat-toolbar')).toHaveText('Panel — Recepcja');
  });

  test('Scenario 2: doctor logs in and lands on the doctor home screen', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await page.waitForURL('**/doctor');
    await expect(page.locator('mat-toolbar')).toHaveText('Panel — Lekarz');
  });

  test('Scenario 3: administrator logs in and lands on the admin home screen', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.admin);
    await page.waitForURL('**/admin');
    await expect(page.locator('mat-toolbar')).toHaveText('Panel — Administrator');
  });

  test('SC-001: login-to-role-home-screen completes within 10 seconds', async ({ page }) => {
    const account = seedAccounts.reception;
    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(account.email);
    await page.getByLabel('Hasło').fill(account.password);

    // Timer starts at submission of correct credentials, per spec.md SC-001 ("w mniej niż 10
    // sekund od podania poprawnych danych"), and ends once the role-appropriate home screen is
    // visible — covering both the MFA challenge round trip and the post-MFA redirect.
    const start = Date.now();
    await page.getByRole('button', { name: 'Zaloguj się' }).click();

    await page.waitForURL('**/login/mfa');
    await page.getByLabel('6-cyfrowy kod').fill(currentTotpCode(account.totpSecret));
    await page.getByRole('button', { name: 'Potwierdź' }).click();

    await page.waitForURL('**/reception');
    await expect(page.locator('mat-toolbar')).toHaveText('Panel — Recepcja');
    const elapsedMs = Date.now() - start;

    expect(elapsedMs).toBeLessThan(10_000);
  });

  test('Scenario 4: wrong password is denied without revealing which field was wrong', async ({
    page,
  }) => {
    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(seedAccounts.reception.email);
    await page.getByLabel('Hasło').fill('definitely-the-wrong-password');
    await page.getByRole('button', { name: 'Zaloguj się' }).click();

    await expect(page.getByRole('alert')).toHaveText('Nieprawidłowy adres e-mail lub hasło.');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('Scenario 5: reception cannot reach an admin-only route (client-side UX guard)', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.reception);
    await page.waitForURL('**/reception');

    await page.goto('/admin');

    // role.guard.ts redirects — this is UX only; the actual security boundary is server-side
    // (404-not-403, verified by RbacEnforcementIntegrationTest, not re-proven here).
    await expect(page).toHaveURL(/\/login$/);
  });

  test('Scenario 6: incomplete MFA grants no access', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(seedAccounts.doctor.email);
    await page.getByLabel('Hasło').fill(seedAccounts.doctor.password);
    await page.getByRole('button', { name: 'Zaloguj się' }).click();

    await page.waitForURL('**/login/mfa');
    // Never submits a TOTP code — navigating straight to a role-home route must not succeed.
    await page.goto('/doctor');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('Scenario 7: self-service password reset end to end', async ({ page }) => {
    const emailsPath = path.resolve(__dirname, '.generated/sent-emails.jsonl');
    const resetAccount = seedAccounts.passwordResetTest;

    // sent-emails.jsonl is a single file shared by the whole backend instance — this test may run
    // concurrently across multiple Playwright projects (playwright.config.ts). Recording the
    // byte offset before triggering the request and only inspecting what was appended after it
    // isolates this run's own email from any other project's concurrent request, avoiding a race
    // on "read the last line".
    const offsetBefore = fileSizeOrZero(emailsPath);

    await page.goto('/password-reset/request');
    await page.getByLabel('Służbowy adres e-mail').fill(resetAccount.email);
    await page.getByRole('button', { name: 'Wyślij link resetujący' }).click();
    await expect(page.getByText(/wysłaliśmy na niego link/)).toBeVisible();

    // The email is written by the backend slightly after the confirmation text above appears,
    // so poll instead of reading the file exactly once.
    let ownLine: string | undefined;
    await expect
      .poll(
        () => {
          const appended = readFileSync(emailsPath, 'utf-8').slice(offsetBefore);
          ownLine = appended
            .trim()
            .split('\n')
            .find((line) => line.includes(resetAccount.email));
          return ownLine;
        },
        { message: 'expected a captured email for this test run', timeout: 10_000 },
      )
      .toBeDefined();
    const resetEmail = JSON.parse(ownLine!) as { body: string };
    const tokenMatch = resetEmail.body.match(/token=([\w-]+)/);
    expect(tokenMatch).not.toBeNull();
    const token = tokenMatch![1];

    const newPassword = 'brand-new-password-123';
    await page.goto(`/password-reset/confirm?token=${token}`);
    await page.getByLabel('Nowe hasło (min. 12 znaków)').fill(newPassword);
    await page.getByLabel('Powtórz nowe hasło').fill(newPassword);
    await page.getByRole('button', { name: 'Ustaw nowe hasło' }).click();
    await expect(page.getByText('Hasło zostało zmienione.')).toBeVisible();

    // Log in with the new password to prove the reset actually took effect end to end.
    await loginWithMfa(page, { ...resetAccount, password: newPassword });
    await page.waitForURL('**/reception');

    // The link is single-use (FR-016) — reusing it must fail.
    await page.goto(`/password-reset/confirm?token=${token}`);
    await page.getByLabel('Nowe hasło (min. 12 znaków)').fill('yet-another-password-1');
    await page.getByLabel('Powtórz nowe hasło').fill('yet-another-password-1');
    await page.getByRole('button', { name: 'Ustaw nowe hasło' }).click();
    await expect(page.getByRole('alert')).toContainText('wygasł lub został już użyty');
  });
});
