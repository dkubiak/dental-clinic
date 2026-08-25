import { expect, test } from '@playwright/test';
import { currentTotpCode, loadSeedAccounts } from './support/seed-accounts';

/**
 * T044 — covers spec.md US2 Acceptance Scenarios 1–4 (tooth-chart view/edit). Requires the
 * backend (auth-service + patient-service) running locally with
 * `-Dspring.profiles.active=e2e-seed` (see quickstart.md Prerequisites).
 *
 * <p>Scenarios 1–3 (toggle/revert/default-healthy) are exercised with the DOCTOR seed account;
 * ASSISTANT's equal tooth-chart access (rbac-policy.md) is re-proven end to end by the dedicated
 * ASSISTANT test below (T061 added the seed account) as well as server-side by
 * ToothChartApiTest#assistant_canToggleToothStatus. Scenario 4 (RECEPTION denied) is proven here
 * client-side (no tooth-chart tab renders) — the server-side 404 is proven by
 * ToothChartApiTest#reception_isDenied404_onReadChart/onToggleToothStatus.
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
  await page.waitForURL('**/patients');
}

async function createPatient(page: import('@playwright/test').Page, lastName: string) {
  await page.getByTestId('new-patient-action').first().click();
  await page.waitForURL('**/patients/new');

  await page.getByLabel('Imię').fill('Test');
  await page.getByLabel('Nazwisko').fill(lastName);
  await page.getByLabel('Data urodzenia').fill('1990-01-15');
  await page.getByLabel('Ulica').fill('Polna');
  await page.getByLabel('Numer budynku').fill('1');
  await page.getByLabel('Kod pocztowy').fill('00-001');
  await page.getByLabel('Miasto').fill('Warszawa');
  await page.getByTestId('create-submit').click();
  await page.waitForURL(/\/patients\/[0-9a-f-]{36}$/);
}

test.describe('US2 — Wizualne oznaczanie stanu uzębienia', () => {
  test('Scenario 3: a newly created record defaults every tooth to healthy', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-3');

    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await expect(page.locator('[data-testid^="tooth-"]')).toHaveCount(32);
    await expect(page.locator('[data-testid^="tooth-"].sick')).toHaveCount(0);
  });

  test('Scenario 1 & 2: doctor marks a tooth sick, then reverts it to healthy', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-1');

    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await page.locator('[data-testid="tooth-11"]').click();
    await page.getByTestId('toggle-status').click();
    await expect(page.locator('[data-testid="tooth-11"]')).toHaveClass(/sick/);

    await page.getByTestId('toggle-status').click();
    await expect(page.locator('[data-testid="tooth-11"]')).not.toHaveClass(/sick/);
  });

  test('SC-002: a tooth-state toggle is reflected within 15 seconds of opening the record', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-SC002');

    // Timer starts once the record's tooth-chart tab is open (spec.md SC-002: "od otwarcia
    // kartoteki pacjenta") and ends when the toggled visual state is reflected.
    const start = Date.now();
    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await page.locator('[data-testid="tooth-18"]').click();
    await page.getByTestId('toggle-status').click();
    await expect(page.locator('[data-testid="tooth-18"]')).toHaveClass(/sick/);
    const elapsedMs = Date.now() - start;

    expect(elapsedMs).toBeLessThan(15_000);
  });

  test('Scenario 4: reception has no access to the tooth chart', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.reception);
    await createPatient(page, 'Testowy-E2E-Tooth-4');

    await expect(page.getByRole('tab', { name: 'Stan uzębienia' })).toHaveCount(0);
  });

  test('Scenario 1: assistant marks a tooth sick on the same terms as doctor', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-5');
    const url = page.url();

    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(seedAccounts.assistant.email);
    await page.getByLabel('Hasło').fill(seedAccounts.assistant.password);
    await page.getByRole('button', { name: 'Zaloguj się' }).click();
    await page.waitForURL('**/login/mfa');
    await page.getByLabel('6-cyfrowy kod').fill(currentTotpCode(seedAccounts.assistant.totpSecret));
    await page.getByRole('button', { name: 'Potwierdź' }).click();
    await page.waitForURL('**/patients');

    await page.goto(url);
    await expect(page.getByRole('heading', { name: 'Testowy-E2E-Tooth-5' })).toBeVisible();
    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await page.locator('[data-testid="tooth-21"]').click();
    await page.getByTestId('toggle-status').click();
    await expect(page.locator('[data-testid="tooth-21"]')).toHaveClass(/sick/);
  });
});
