import { expect, test } from '@playwright/test';
import { currentTotpCode, loadSeedAccounts } from './support/seed-accounts';

/**
 * Feature 005 (005-tooth-chart-diagnoses) — covers spec.md User Story 1 Acceptance Scenarios 1-5
 * (quickstart.md Scenario 1). Replaces the old binary tooth-state e2e suite this feature's
 * migration (V4__tooth_chart_diagnoses.sql) drops outright — requires the backend
 * (auth-service + patient-service) running locally with `-Dspring.profiles.active=e2e-seed`
 * (see quickstart.md Prerequisites).
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

const teethLocator = (page: import('@playwright/test').Page) =>
  page.locator('[data-testid^="tooth-"]:not([data-testid^="tooth-chart"])');

test.describe('US1 — Lekarz odnotowuje rozpoznanie na konkretnej powierzchni zęba', () => {
  test('Scenario 1: a fresh chart renders both arches with an empty-state message', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-1');

    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await expect(teethLocator(page)).toHaveCount(32);
    await expect(page.getByTestId('tooth-chart-empty')).toBeVisible();
  });

  test('Scenario 2-5: doctor records a surface-scoped finding and it persists after reload', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-2');
    const url = page.url();

    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await page.locator('[data-testid="tooth-36"]').click();

    await expect(page.locator('[data-testid="finding-list-empty"]')).toBeVisible();

    await page.getByTestId('catalog-search-input').fill('próchnica zębiny');
    await page.getByTestId('catalog-entry-K02.1').click();

    // Scenario 3 — blocked without a surface.
    await expect(page.getByTestId('save-finding')).toBeDisabled();

    // Scenario 4 — pick a surface, then save.
    await page.getByTestId('surface-zone-OCCLUSAL_INCISAL').click();
    await expect(page.getByTestId('save-finding')).toBeEnabled();
    await page.getByTestId('save-finding').click();
    await expect(page.getByTestId('save-success')).toBeVisible();

    // Scenario 5 — reload and verify persistence.
    await page.goto(url);
    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await page.locator('[data-testid="tooth-36"]').click();
    await expect(page.getByTestId('finding-item')).toContainText('Próchnica zębiny');
  });

  test('Scenario 6: an incisor offers an incisal surface, not an occlusal one', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-6');

    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await page.locator('[data-testid="tooth-11"]').click();

    const occlusalIncisalZone = page.getByTestId('surface-zone-OCCLUSAL_INCISAL');
    await expect(occlusalIncisalZone).toHaveAttribute('aria-label', /sieczna/);
  });

  test('Scenario 7: a WHOLE_TOOTH-scope entry hides the surface picker', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-7');

    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await page.locator('[data-testid="tooth-36"]').click();
    await page.getByTestId('catalog-search-input').fill('zapalenie miazgi nieodwracalne');
    await page.getByTestId('catalog-entry-K04.0i').click();

    await expect(page.getByTestId('surface-picker')).toHaveCount(0);
    await expect(page.getByTestId('save-finding')).toBeEnabled();
  });

  test('reception has no access to the tooth chart tab', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.reception);
    await createPatient(page, 'Testowy-E2E-Tooth-Reception');

    await expect(page.getByRole('tab', { name: 'Stan uzębienia' })).toHaveCount(0);
  });

  test('assistant has the same write access as doctor (FR-057)', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    await createPatient(page, 'Testowy-E2E-Tooth-Assistant');
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
    await page.getByRole('tab', { name: 'Stan uzębienia' }).click();
    await page.locator('[data-testid="tooth-21"]').click();
    await page.getByTestId('catalog-search-input').fill('próchnica zębiny');
    await page.getByTestId('catalog-entry-K02.1').click();
    await page.getByTestId('surface-zone-MESIAL').click();
    await page.getByTestId('save-finding').click();
    await expect(page.getByTestId('save-success')).toBeVisible();
  });
});
