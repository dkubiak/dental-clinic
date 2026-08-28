import { expect, test } from '@playwright/test';

/**
 * US2 — przełącznik motywu dostępny zawsze, także przed zalogowaniem (FR-008 … FR-013, FR-027).
 * Backend NIE jest potrzebny: ekran logowania renderuje się w pełni statycznie, a preferencja
 * motywu żyje wyłącznie w localStorage (quickstart.md §2). Uruchamiane w CI przez zadanie
 * `frontend-e2e-theme` przeciwko zbudowanej aplikacji serwowanej statycznie (research.md R6).
 */

test.beforeEach(async ({ page }) => {
  await page.goto('/login');
});

test('przełącznik motywu jest obecny na ekranie logowania, przed uwierzytelnieniem (FR-008)', async ({ page }) => {
  await expect(page.getByTestId('theme-toggle')).toBeVisible();
});

test('przełączenie motywu nie przeładowuje strony i nie usuwa wpisanych danych (FR-009)', async ({ page }) => {
  await page.getByLabel('Adres e-mail').fill('personel@dentalclinic.example');

  await page.getByTestId('theme-toggle').click();

  await expect(page.getByLabel('Adres e-mail')).toHaveValue('personel@dentalclinic.example');
  await expect(page.locator('html')).toHaveJSProperty('style.colorScheme', 'dark');
});

test('wybór motywu jest trwały po odświeżeniu strony (FR-010, FR-013)', async ({ page }) => {
  await page.getByTestId('theme-toggle').click();
  await expect(page.locator('html')).toHaveJSProperty('style.colorScheme', 'dark');

  await page.reload();

  await expect(page.locator('html')).toHaveJSProperty('style.colorScheme', 'dark');
});

test('brak błysku motywu jasnego przy zapamiętanym motywie ciemnym (FR-027)', async ({ page }) => {
  await page.evaluate(() => localStorage.setItem('pu.theme', 'dark'));

  // Skrypt inline w <head> musi ustawić color-scheme PRZED pierwszym malowaniem — sprawdzamy to
  // zaraz po nawigacji, nie czekając na bootstrap Angulara (research.md R8).
  const response = await page.goto('/login', { waitUntil: 'commit' });
  expect(response?.ok()).toBe(true);

  const colorScheme = await page.evaluate(() => document.documentElement.style.colorScheme);
  expect(colorScheme).toBe('dark');
});

test('zmiana motywu propaguje się do drugiej karty tego samego pochodzenia', async ({ context, page }) => {
  const secondPage = await context.newPage();
  await secondPage.goto('/login');

  await page.getByTestId('theme-toggle').click();
  await expect(page.locator('html')).toHaveJSProperty('style.colorScheme', 'dark');

  await expect(secondPage.locator('html')).toHaveJSProperty('style.colorScheme', 'dark');

  await secondPage.close();
});
