import { expect, test } from '@playwright/test';
import { currentTotpCode, loadSeedAccounts } from './support/seed-accounts';

/**
 * T033 — covers spec.md US1 Acceptance Scenarios 1–6 (patient creation). Requires the backend
 * (auth-service + patient-service) running locally with `-Dspring.profiles.active=e2e-seed` (see
 * quickstart.md Prerequisites) so the seeded role accounts exist with MFA already enrolled.
 *
 * <p>Scenario 6 ("any role other than rejestrator/lekarz/administrator is denied") is only
 * partially covered here: no ASSISTANT seed account exists yet (that's T061, Phase 6 Polish) — the
 * server-side denial itself is already proven by PatientCreateApiTest#assistant_isDenied404
 * (backend contract test); this suite re-proves it end-to-end once T061 adds the seed account.
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

test.describe('US1 — Założenie kartoteki nowego pacjenta', () => {
  test('Scenario 1: reception creates a patient record with a valid PESEL', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.reception);

    await page.getByTestId('new-patient-action').first().click();
    await page.waitForURL('**/patients/new');

    await page.getByLabel('Imię').fill('Jan');
    await page.getByLabel('Nazwisko').fill('Testowy-E2E-1');
    await page.getByLabel('Data urodzenia').fill('1990-01-15');
    await page.getByLabel('PESEL (opcjonalnie)').fill('90011500013');
    await page.getByLabel('Ulica').fill('Polna');
    await page.getByLabel('Numer budynku').fill('12A');
    await page.getByLabel('Kod pocztowy').fill('00-001');
    await page.getByLabel('Miasto').fill('Warszawa');
    await page.getByTestId('create-submit').click();

    // Redirects to the new patient's detail view (US1: "system tworzy nową kartotekę i
    // wyświetla potwierdzenie" — the detail view showing the freshly-saved data is that
    // confirmation).
    await page.waitForURL(/\/patients\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { name: /Testowy-E2E-1/ })).toBeVisible();
  });

  test('Scenario 2: doctor creates a patient record on the same terms as reception', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.doctor);

    await page.getByTestId('new-patient-action').first().click();
    await page.waitForURL('**/patients/new');

    await page.getByLabel('Imię').fill('Anna');
    await page.getByLabel('Nazwisko').fill('Testowy-E2E-2');
    await page.getByLabel('Data urodzenia').fill('1985-05-20');
    await page.getByLabel('Ulica').fill('Leśna');
    await page.getByLabel('Numer budynku').fill('3');
    await page.getByLabel('Kod pocztowy').fill('00-002');
    await page.getByLabel('Miasto').fill('Kraków');
    await page.getByTestId('create-submit').click();

    await page.waitForURL(/\/patients\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { name: /Testowy-E2E-2/ })).toBeVisible();
  });

  test('Scenario 3: an invalid PESEL checksum is rejected client-side, blocking submission', async ({
    page,
  }) => {
    await loginWithMfa(page, seedAccounts.reception);
    await page.goto('/patients/new');

    await page.getByLabel('Imię').fill('Piotr');
    await page.getByLabel('Nazwisko').fill('Testowy-E2E-3');
    await page.getByLabel('Data urodzenia').fill('1980-03-03');
    await page.getByLabel('PESEL (opcjonalnie)').fill('90011500021'); // altered last digit
    await page.getByLabel('Ulica').fill('Krótka');
    await page.getByLabel('Numer budynku').fill('1');
    await page.getByLabel('Kod pocztowy').fill('00-003');
    await page.getByLabel('Miasto').fill('Poznań');

    await expect(page.getByText('Nieprawidłowy format lub suma kontrolna')).toBeVisible();
    await expect(page.getByTestId('create-submit')).toBeDisabled();
  });

  test('Scenario 4: a duplicate PESEL is rejected by the server', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.doctor);
    const pesel = '90011500143'; // deliberately reused across both submissions below

    await page.goto('/patients/new');
    await page.getByLabel('Imię').fill('Ewa');
    await page.getByLabel('Nazwisko').fill('Testowy-E2E-4a');
    await page.getByLabel('Data urodzenia').fill('1972-01-18');
    await page.getByLabel('PESEL (opcjonalnie)').fill(pesel);
    await page.getByLabel('Ulica').fill('Rynek');
    await page.getByLabel('Numer budynku').fill('5');
    await page.getByLabel('Kod pocztowy').fill('00-005');
    await page.getByLabel('Miasto').fill('Wrocław');
    await page.getByTestId('create-submit').click();
    await page.waitForURL(/\/patients\/[0-9a-f-]{36}$/);

    await page.goto('/patients/new');
    await page.getByLabel('Imię').fill('Marek');
    await page.getByLabel('Nazwisko').fill('Testowy-E2E-4b');
    await page.getByLabel('Data urodzenia').fill('1975-06-06');
    await page.getByLabel('PESEL (opcjonalnie)').fill(pesel);
    await page.getByLabel('Ulica').fill('Rynek');
    await page.getByLabel('Numer budynku').fill('6');
    await page.getByLabel('Kod pocztowy').fill('00-006');
    await page.getByLabel('Miasto').fill('Wrocław');
    await page.getByTestId('create-submit').click();

    await expect(page.getByRole('alert')).toContainText('PESEL już istnieje');
    await expect(page).toHaveURL(/\/patients\/new$/);
  });

  test('Scenario 5: a record can be created without a PESEL', async ({ page }) => {
    await loginWithMfa(page, seedAccounts.reception);
    await page.goto('/patients/new');

    await page.getByLabel('Imię').fill('Zofia');
    await page.getByLabel('Nazwisko').fill('Testowy-E2E-5');
    await page.getByLabel('Data urodzenia').fill('1995-09-09');
    await page.getByLabel('Ulica').fill('Ogrodowa');
    await page.getByLabel('Numer budynku').fill('7');
    await page.getByLabel('Kod pocztowy').fill('00-007');
    await page.getByLabel('Miasto').fill('Łódź');
    await page.getByTestId('create-submit').click();

    await page.waitForURL(/\/patients\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { name: /Testowy-E2E-5/ })).toBeVisible();
  });

  test('Scenario 6 (client-side half): ADMINISTRATOR cannot reach the create-patient route', async ({
    page,
  }) => {
    // The ASSISTANT case (also denied by FR-001/rbac-policy.md) is proven server-side by
    // PatientCreateApiTest#assistant_isDenied404 — no ASSISTANT seed account exists yet for an
    // end-to-end login here (T061, Phase 6 Polish, adds it). ADMINISTRATOR has zero patient-facing
    // shell access at all (data-model.md), so it's used here to prove the route-guard half of the
    // denial client-side, complementing the backend's 404-not-403 enforcement.
    await page.goto('/login');
    await page.getByLabel('Adres e-mail').fill(seedAccounts.admin.email);
    await page.getByLabel('Hasło').fill(seedAccounts.admin.password);
    await page.getByRole('button', { name: 'Zaloguj się' }).click();
    await page.waitForURL('**/login/mfa');
    await page.getByLabel('6-cyfrowy kod').fill(currentTotpCode(seedAccounts.admin.totpSecret));
    await page.getByRole('button', { name: 'Potwierdź' }).click();
    await page.waitForURL('**/admin');

    await page.goto('/patients/new');
    await expect(page).toHaveURL(/\/login$/);
  });
});
