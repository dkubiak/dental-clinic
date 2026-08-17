import { expect, test } from '@playwright/test';
import { readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { currentTotpCode, loadSeedAccounts } from './support/seed-accounts';

/**
 * T073 — covers spec.md US3 Acceptance Scenarios 1-2. Requires the backend running locally with
 * `-Dspring.profiles.active=e2e-seed` (see quickstart.md Prerequisites).
 *
 * <p>Scenario 3 ("administrator direct access to clinical data is always denied") is not
 * re-provable here: the fixture that demonstrates it (TestOnlyAdminController's
 * `/test-support/clinical-data-only`) lives only on the backend's test classpath, not in a real
 * running instance — it is proven server-side by AdministratorNoClinicalAccessTest (T071), the
 * same split us1-login-rbac.spec.ts's javadoc already documents for endpoint-level RBAC checks.
 */

const seedAccounts = loadSeedAccounts();
const emailsPath = path.resolve(__dirname, '.generated/sent-emails.jsonl');

function fileSizeOrZero(filePath: string): number {
  try {
    return statSync(filePath).size;
  } catch {
    return 0;
  }
}

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

test.describe('US3 — admin account lifecycle', () => {
  test('Scenario 1: admin-created account sets its password, enrolls MFA, and logs in with role-appropriate access', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.admin);
    await page.waitForURL('**/admin');

    const uniqueEmail = `lifecycle-e2e-${Date.now()}@dentalclinic.example`;
    const offsetBefore = fileSizeOrZero(emailsPath);

    await page.goto('/admin/accounts');
    await page.getByLabel('Adres e-mail').fill(uniqueEmail);
    // Role select already defaults to RECEPTION.
    await page.getByRole('button', { name: 'Utwórz konto' }).click();
    await expect(page.locator('tr', { hasText: uniqueEmail })).toBeVisible();

    const appended = readFileSync(emailsPath, 'utf-8').slice(offsetBefore);
    const ownLine = appended
      .trim()
      .split('\n')
      .find((line) => line.includes(uniqueEmail));
    expect(ownLine, 'expected a captured set-password email for the new account').toBeDefined();
    const tokenMatch = JSON.parse(ownLine!).body.match(/token=([\w-]+)/);
    expect(tokenMatch).not.toBeNull();
    const token = tokenMatch![1];

    const newPassword = 'new-staff-first-password-1';
    await page.goto(`/password-reset/confirm?token=${token}`);
    await page.getByLabel('Nowe hasło (min. 12 znaków)').fill(newPassword);
    await page.getByLabel('Powtórz nowe hasło').fill(newPassword);
    await page.getByRole('button', { name: 'Ustaw nowe hasło' }).click();
    await expect(page.getByText('Hasło zostało zmienione.')).toBeVisible();

    // First login: password step, then enroll MFA using the freshly-issued secret.
    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(uniqueEmail);
    await page.getByLabel('Hasło').fill(newPassword);
    await page.getByRole('button', { name: 'Zaloguj się' }).click();
    await page.waitForURL('**/login/mfa');

    const secret = (await page.locator('code.secret').textContent())!.trim();
    await page.getByLabel('6-cyfrowy kod').fill(currentTotpCode(secret));
    await page.getByRole('button', { name: 'Potwierdź' }).click();

    // RECEPTION role -> lands on the reception home screen (role-appropriate access).
    await page.waitForURL('**/reception');
    await expect(page.locator('mat-toolbar')).toHaveText('Panel — Recepcja');
  });

  test('Scenario 2: a deactivated account cannot log in, and the denial is audit-logged', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.admin);
    await page.waitForURL('**/admin');

    const uniqueEmail = `deactivate-e2e-${Date.now()}@dentalclinic.example`;
    await page.goto('/admin/accounts');
    await page.getByLabel('Adres e-mail').fill(uniqueEmail);
    await page.getByRole('button', { name: 'Utwórz konto' }).click();
    const row = page.locator('tr', { hasText: uniqueEmail });
    await expect(row).toBeVisible();

    await row.getByRole('button', { name: 'Dezaktywuj' }).click();
    await expect(row).toContainText('DEACTIVATED');

    // The account never completed enrollment, so it fails on password step already — either way,
    // it must never reach a role-home screen.
    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(uniqueEmail);
    await page.getByLabel('Hasło').fill('whatever-password-guess-1');
    await page.getByRole('button', { name: 'Zaloguj się' }).click();
    await expect(page.getByRole('alert')).toHaveText(
      'To konto zostało dezaktywowane. Skontaktuj się z administratorem.',
    );
    await expect(page).toHaveURL(/\/login$/);

    await page.goto('/admin/audit-log');
    await page.getByLabel('Typ zdarzenia').click();
    await page.getByRole('option', { name: 'LOGIN_DENIED_DEACTIVATED', exact: true }).click();
    await expect(page.locator('table tbody tr').first()).toBeVisible();
  });
});
