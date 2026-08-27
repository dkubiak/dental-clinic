# Quickstart: Branding i motyw UI (Projekt Uśmiech)

**Feature**: `003-brand-ui-theme` | **Date**: 2026-08-27

Jak uruchomić i zweryfikować ten feature. Szczegóły wartości są w
[`contracts/design-tokens.md`](./contracts/design-tokens.md), a kontrakty zachowania
w [`contracts/theme-preference.md`](./contracts/theme-preference.md) — ten dokument ich nie
powiela.

## Wymagania wstępne

- Node.js 22 (ta sama wersja co w CI)
- `cd frontend && npm ci`

**Backend nie jest potrzebny.** To jest różnica wobec quickstartów feature'ów 001 i 002.
Cała weryfikacja tego feature'u — łącznie z przeglądarkową — działa na samym froncie, bo
przełącznik motywu z FR-008 jest dostępny na ekranie logowania, przed uwierzytelnieniem.

---

## 1. Weryfikacja jednostkowa (bramkowana w CI)

```bash
cd frontend
npm test
```

Cztery zestawy, wszystkie w Vitest, wszystkie uruchamiane przez zadanie `frontend-unit`:

| Plik | Co sprawdza | Wymagania |
| --- | --- | --- |
| `src/styles/contrast-audit.spec.ts` | progi WCAG dla każdej pary, osobno w obu motywach | FR-017, FR-018 |
| `src/styles/token-parity.spec.ts` | zgodność `brand-tokens.ts` z `_pu-tokens.scss`, komplet ról | FR-002, FR-007 |
| `src/styles/no-literal-colors.spec.ts` | brak literalnych kolorów w komponentach | FR-001 |
| `src/app/core/theme/theme.service.spec.ts` | zapis, odczyt, degradacja, propagacja między kartami | FR-010 … FR-014 |

### Oczekiwany wynik porażki

Audyt ma być diagnostyczny, nie tylko czerwony. Komunikat MUSI wskazywać motyw, obie role,
próg i wartość uzyskaną — inaczej nie spełnia FR-018:

```text
FAIL  src/styles/contrast-audit.spec.ts > kontrast > motyw jasny
  × accent-text na bg osiąga próg 4.5:1
    oczekiwano: >= 4.5
    otrzymano:  1.99
    accent-text #CBAD89 na bg #FAF7F2 (motyw jasny)
```

### Sprawdzenie, że audyt naprawdę działa

Test, który nigdy nie czerwienił, nie jest bramką. Przed uznaniem fazy za skończoną:

```bash
# 1. Podmień w brand-tokens.ts accent-text.light na '#CBAD89'
npm test          # MUSI zgłosić porażkę z komunikatem jak wyżej
# 2. Cofnij zmianę
npm test          # MUSI przejść
```

To samo dla parzystości ról — usuń wartość `dark` dowolnej roli i sprawdź, że test czerwieni.

---

## 2. Weryfikacja w przeglądarce

```bash
cd frontend
npm run build
npx http-server dist/dental-clinic-frontend/browser -p 4200 &
E2E_BASE_URL=http://localhost:4200 npx playwright test e2e/us2-theme-*.spec.ts
```

Domyślnie uruchamia się to na czterech projektach skonfigurowanych w `playwright.config.ts`
(Pixel 7, iPhone 14, iPad Mini landscape, Desktop Chrome), przy czym projekty mobilne są
pierwsze — zgodnie z Principle IV.

| Plik | Co sprawdza | Wymagania |
| --- | --- | --- |
| `e2e/us2-theme-toggle.spec.ts` | przełącznik na ekranie logowania, trwałość po odświeżeniu, brak błysku, dwie karty | FR-008 … FR-013, FR-027 |
| `e2e/us2-theme-contrast.spec.ts` | realny kontrast na wyrenderowanym ekranie, 320 px, oba motywy | FR-024, FR-026 |

### Test braku błysku (FR-027)

Najłatwiejszy do wdrożenia pozornie i dlatego wart osobnej uwagi. Sprawdzenie: ustaw
`localStorage` na `dark`, przeładuj z wyłączonym cache i zweryfikuj, że `color-scheme` na
`<html>` jest `dark` **już przy pierwszym malowaniu**, a nie dopiero po bootstrapie Angulara.
Jeżeli test przechodzi po usunięciu skryptu inline z `index.html`, to znaczy, że nie sprawdza
tego, co miał.

---

## 3. Weryfikacja ręczna

Rzeczy, których automat nie złapie, a które decydują o tym, czy feature jest zrobiony.

1. **Przełącz motyw z wypełnionym formularzem** — otwórz zakładanie kartoteki, wpisz dane,
   przełącz motyw. Dane muszą zostać (FR-009). To jest najczęstsza regresja przy wdrożeniach
   opartych o przeładowanie strony.
2. **Wyloguj się po wyborze motywu** — wybór musi przetrwać (FR-011).
3. **Tryb prywatny** — otwórz aplikację w oknie prywatnym, przełącz motyw. Ma działać
   w obrębie sesji, bez komunikatu błędu (FR-014).
4. **Dwie karty** — otwórz aplikację w dwóch kartach, zmień motyw w jednej. Druga ma nadążyć.
5. **Zmiana motywu systemu operacyjnego** — przy jawnym wyborze aplikacja ma go zignorować
   (FR-013); po wyczyszczeniu danych witryny ma znów podążać za systemem (FR-012).
6. **Schemat uzębienia** — zdrowy i chory ząb muszą być rozróżnialne po wyłączeniu koloru.
   Praktyczny sprawdzian: zrzut ekranu przepuszczony przez skalę szarości. Wypełnienia dają
   wobec siebie 1.23:1, więc jeśli drugi sygnał nie działa, stany zleją się w jedno (FR-019).
7. **Wydruk z motywu ciemnego** — otwórz podgląd wydruku przy włączonym motywie ciemnym.
   Tło nie może wyjść ciemne (FR-028).

---

## 4. Bramka CI

Po wdrożeniu w `.github/workflows/ci.yml` istnieją dwa istotne dla tego feature'u zadania:

| Zadanie | Status | Zakres |
| --- | --- | --- |
| `frontend-unit` | działa (istniejące) | lint + cztery zestawy Vitest z sekcji 1 |
| `frontend-e2e-theme` | **nowe** | build + statyczny serwer + specyfikacje motywu |

Zadanie `frontend-e2e` pozostaje wyłączone (`if: false`) i **nie jest ruszane** — czeka na
wyprowadzenie Postgresa i LocalStacka, co leży poza zakresem tego feature'u. Uzasadnienie
rozdziału: [`research.md`](./research.md) R5 i R6.

---

## 5. Definicja ukończenia

- [ ] `npm run lint` i `npm test` przechodzą lokalnie i w CI
- [ ] Audyt kontrastu udowodnił, że czerwieni się na celowo zepsutej wartości
- [ ] Specyfikacje Playwrighta przechodzą na wszystkich czterech projektach
- [ ] Siedem sprawdzeń ręcznych z sekcji 3 wykonanych
- [ ] Żaden z dwunastu komponentów nie zawiera literalnej wartości koloru
- [ ] `backend/`, `patient-service/`, `helm/`, `infra/` bez zmian — potwierdza ocenę bramki
      bezpieczeństwa z [`plan.md`](./plan.md)
