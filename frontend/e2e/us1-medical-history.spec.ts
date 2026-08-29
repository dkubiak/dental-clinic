import { expect, test } from '@playwright/test';
import { currentTotpCode, loadSeedAccounts } from './support/seed-accounts';

/**
 * Covers spec.md (004-patient-medical-history) US1/US2/US3 acceptance scenarios end to end.
 * Requires the backend (auth-service + patient-service) running locally with
 * `-Dspring.profiles.active=e2e-seed` (see quickstart.md Prerequisites) — same requirement as
 * `us2-tooth-chart.spec.ts`, which this file mirrors structurally.
 *
 * Not wired into any CI job (plan.md Technical Context, "Known coverage limitation") — this
 * feature is only reachable post-login, so it can't ride the pre-auth-only `frontend-e2e-theme`
 * job, and `frontend-e2e` itself stays disabled (`if: false`) pending Postgres/LocalStack
 * provisioning in CI. Run locally: `npm run e2e -- e2e/us1-medical-history.spec.ts`.
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
  // `new-patient-action` exists twice (desktop nav link + mobile FAB, app-shell.component.ts) —
  // exactly one is ever visible per viewport (CSS media query), the other is `display:none`.
  // `.first()` (DOM order) would always grab the desktop one, which hangs/times out on the
  // mobile-chromium/mobile-webkit projects. `:visible` picks whichever one the current viewport
  // actually renders.
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

async function selectMatOption(
  page: import('@playwright/test').Page,
  labelText: string,
  optionText: string,
) {
  await page.getByLabel(labelText).click({ force: true });
  await page.getByRole('option', { name: optionText, exact: true }).click();
}

test.describe('US1 — Alergie', () => {
  test('Scenario 1: empty state shows "brak odnotowanych alergii"', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-MedHist-1');

    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await expect(page.getByTestId('allergies-empty')).toBeVisible();
  });

  test('Scenario 2: a CRITICAL allergy is visually flagged and the header badge is visible without scrolling (SC-004)', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-MedHist-2');
    const patientUrl = page.url();

    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await page.getByLabel('Substancja/czynnik').fill('Penicylina');
    await page.getByLabel('Typ reakcji').fill('Anafilaksja');
    await selectMatOption(page, 'Waga', 'krytyczna');
    await page.getByRole('button', { name: 'Dodaj alergię' }).click();

    await expect(page.locator('.status-error', { hasText: 'Penicylina' })).toBeVisible();

    // Reload the record fresh — the badge must be visible on first screen, immediately on open,
    // without any extra click or scroll (SC-004), not just right after submitting the form.
    await page.goto(patientUrl);
    await expect(page.getByTestId('critical-allergy-alert')).toBeInViewport();
  });

  test('Scenario 3: reception sees only the fact-only critical-allergy badge, no clinical detail', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-MedHist-3');
    const patientUrl = page.url();

    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await page.getByLabel('Substancja/czynnik').fill('Lateks');
    await page.getByLabel('Typ reakcji').fill('Wysypka anafilaktyczna');
    await selectMatOption(page, 'Waga', 'krytyczna');
    await page.getByRole('button', { name: 'Dodaj alergię' }).click();
    await expect(page.locator('.status-error')).toBeVisible();

    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(seedAccounts.reception.email);
    await page.getByLabel('Hasło').fill(seedAccounts.reception.password);
    await page.getByRole('button', { name: 'Zaloguj się' }).click();
    await page.waitForURL('**/login/mfa');
    await page.getByLabel('6-cyfrowy kod').fill(currentTotpCode(seedAccounts.reception.totpSecret));
    await page.getByRole('button', { name: 'Potwierdź' }).click();
    await page.waitForURL('**/patients');

    await page.goto(patientUrl);
    await expect(page.getByTestId('critical-allergy-alert')).toBeInViewport();
    await expect(page.getByRole('tab', { name: 'Historia medyczna' })).toHaveCount(0);
    await expect(page.locator('body')).not.toContainText('Lateks');
  });

  test('Scenario 1 (US1): assistant has the same read access as doctor, but no add-entry form', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-MedHist-4');
    const patientUrl = page.url();

    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await page.getByLabel('Substancja/czynnik').fill('Pyłki');
    await page.getByLabel('Typ reakcji').fill('Katar');
    await selectMatOption(page, 'Waga', 'umiarkowana');
    await page.getByRole('button', { name: 'Dodaj alergię' }).click();
    await expect(page.locator('body')).toContainText('Pyłki');

    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(seedAccounts.assistant.email);
    await page.getByLabel('Hasło').fill(seedAccounts.assistant.password);
    await page.getByRole('button', { name: 'Zaloguj się' }).click();
    await page.waitForURL('**/login/mfa');
    await page.getByLabel('6-cyfrowy kod').fill(currentTotpCode(seedAccounts.assistant.totpSecret));
    await page.getByRole('button', { name: 'Potwierdź' }).click();
    await page.waitForURL('**/patients');

    await page.goto(patientUrl);
    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await expect(page.locator('body')).toContainText('Pyłki');
    await expect(page.getByTestId('add-allergy-form')).toHaveCount(0);
  });

  test('FR-010: correcting an entry hides the old one from the default view but keeps it in "Historia zmian"', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-MedHist-5');
    const patientUrl = page.url();

    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await page.getByLabel('Substancja/czynnik').fill('Penicylina');
    await page.getByLabel('Typ reakcji').fill('Anafilaksja');
    await selectMatOption(page, 'Waga', 'krytyczna');
    await page.getByRole('button', { name: 'Dodaj alergię' }).click();
    await expect(page.locator('.status-error')).toBeVisible();

    // Correct the just-added CRITICAL entry down to MODERATE.
    await page
      .locator('li', { hasText: 'Penicylina' })
      .getByRole('button', { name: 'Koryguj' })
      .click();
    await expect(page.getByLabel('Substancja/czynnik')).toHaveValue('Penicylina');
    await selectMatOption(page, 'Waga', 'umiarkowana');
    await page.getByRole('button', { name: 'Zapisz korektę' }).click();

    // Default view: only the corrected (MODERATE) entry, critical badge gone.
    await expect(page.locator('.status-error')).toHaveCount(0);
    await page.goto(patientUrl);
    await expect(page.getByTestId('critical-allergy-alert')).toHaveCount(0);

    // "Historia zmian": both entries, the old one marked superseded.
    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await page.getByTestId('toggle-allergy-history').click();
    await expect(page.locator('li.superseded', { hasText: 'Penicylina' })).toBeVisible();
    await expect(page.locator('li.superseded')).toContainText('nieaktualny');
  });
});

test.describe('US2 — Leki', () => {
  test('Scenario 1: empty state, then adding a medication shows it with its start date', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-MedHist-Leki-1');

    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await expect(page.getByTestId('medications-empty')).toBeVisible();

    await page.getByLabel('Nazwa leku').fill('Ibuprofen');
    await page.getByLabel('Dawka').fill('400mg 2x/dzień');
    await page.getByLabel('Data rozpoczęcia').fill('2026-01-01');
    await page.getByRole('button', { name: 'Dodaj lek' }).click();

    await expect(page.locator('body')).toContainText('Ibuprofen');
    await expect(page.locator('body')).toContainText('2026-01-01');
  });
});

test.describe('US3 — Choroby przewlekłe/przebyte', () => {
  test('Scenario 1 & FR-010: add a chronic condition, then correct its clinical status independently of the correction flag', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-MedHist-Choroby-1');

    await page.getByRole('tab', { name: 'Historia medyczna' }).click();
    await expect(page.getByTestId('chronic-conditions-empty')).toBeVisible();

    await page.getByLabel('Nazwa choroby').fill('Cukrzyca typu 2');
    await selectMatOption(page, 'Status', 'aktywna');
    await page.getByLabel('Data rozpoznania').fill('2020-03-15');
    await page.getByRole('button', { name: 'Dodaj chorobę' }).click();
    await expect(page.locator('body')).toContainText('ACTIVE');

    await page
      .locator('li', { hasText: 'Cukrzyca typu 2' })
      .getByRole('button', { name: 'Koryguj' })
      .click();
    await selectMatOption(page, 'Status', 'przebyta');
    await page.getByRole('button', { name: 'Zapisz korektę' }).click();

    await expect(page.locator('body')).toContainText('PAST');
    await expect(page.locator('[data-testid^="chronic-condition-"]')).toHaveCount(1);
  });
});
