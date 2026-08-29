import { expect, test } from '@playwright/test';
import { currentTotpCode, loadSeedAccounts } from './support/seed-accounts';

/**
 * T053 — covers spec.md US3 Acceptance Scenarios 1–2 (visit-history placeholder). Requires the
 * backend (auth-service + patient-service) running locally with
 * `-Dspring.profiles.active=e2e-seed` (see quickstart.md Prerequisites).
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
  // `new-patient-action` exists twice (desktop nav link + mobile FAB, app-shell.component.ts,
  // since feature 003's responsive redesign) — exactly one is visible per viewport (CSS media
  // query), the other `display:none`. `.first()` (DOM order) always grabbed the desktop one,
  // hanging on mobile-chromium/mobile-webkit/tablet-chromium; `:visible` picks whichever one the
  // current viewport actually renders.
  await page.locator('[data-testid="new-patient-action"]:visible').click();
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

test.describe('US3 — Podgląd historii wizyt pacjenta z poziomu kartoteki', () => {
  test('Scenario 1 & 2: reception sees an empty-state placeholder with no add-entry control', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.reception);
    await createPatient(page, 'Testowy-E2E-VisitHistory-1');

    await page.getByRole('tab', { name: 'Historia wizyt' }).click();
    await expect(page.getByText(/histori[ai] wizyt/i)).toBeVisible();

    const tabPanel = page.getByRole('tabpanel');
    await expect(tabPanel.getByRole('button')).toHaveCount(0);
    await expect(tabPanel.getByRole('link')).toHaveCount(0);
  });

  test('Scenario 1: doctor sees the same empty-state placeholder', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-VisitHistory-2');

    await page.getByRole('tab', { name: 'Historia wizyt' }).click();
    await expect(page.getByText(/histori[ai] wizyt/i)).toBeVisible();
  });
});
