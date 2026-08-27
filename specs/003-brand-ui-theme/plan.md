# Implementation Plan: Branding i motyw UI (Projekt Uśmiech)

**Branch**: `003-brand-ui-theme` | **Date**: 2026-08-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-brand-ui-theme/spec.md`

## Summary

Zastąpienie domyślnej fioletowej palety Angular Material systemem kolorystycznym marki
(`design/brand/_pu-tokens.scss`) we wszystkich trzynastu ekranach aplikacji personelu, wraz
z przełącznikiem motywu jasny/ciemny dostępnym zawsze — również przed zalogowaniem.

Podejście techniczne wynika z jednego ustalenia zweryfikowanego w źródle
`@angular/material@21.2.14`: `mat.theme()` z domyślnym `theme-type: color-scheme` emituje każdy
token koloru jako `light-dark($jasny, $ciemny)`, więc **przełączanie motywu sprowadza się do
ustawienia właściwości CSS `color-scheme` na `<html>`**. Trzy dopuszczalne wartości tej
właściwości (`light dark` / `light` / `dark`) pokrywają się jeden do jednego z trzema stanami
preferencji wymaganymi przez specyfikację, a ponieważ `color-scheme` jest właściwością natywną,
przeglądarka stosuje ją też do kontrolek poza kontrolą aplikacji — co realizuje FR-026 bez
osobnego mechanizmu.

Dwie rzeczy determinują kolejność prac. Po pierwsze, audyt kontrastu (FR-018) musi trafić do
**Vitest**, a nie do Playwrighta, bo zadanie `frontend-e2e` jest w CI wyłączone (`if: false`) —
umieszczony tam audyt nigdy by się nie uruchomił. Po drugie, Principle I wymaga, by ten audyt
powstał i czerwienił się **przed** wdrożeniem palety.

## Technical Context

**Language/Version**: TypeScript 5.9, Angular 21.0, Sass (SCSS)

**Primary Dependencies**: `@angular/material` 21.2 (Material 3, `mat.theme()`), `@angular/cdk` 21

**Storage**: `localStorage` przeglądarki (klucz `pu.theme`) — bez bazy danych, bez backendu

**Testing**: Vitest 4 + jsdom (jednostkowe, bramkowane w CI), Playwright 1.57 (przeglądarkowe,
projekty mobile-first: Pixel 7, iPhone 14, iPad Mini landscape, Desktop Chrome)

**Target Platform**: przeglądarki evergreen wspierające CSS `light-dark()` (Baseline 2024:
Chrome 123+, Safari 17.5+, Firefox 120+)

**Project Type**: aplikacja webowa — ten feature dotyka **wyłącznie** katalogu `frontend/`
oraz jednego pliku workflow CI

**Performance Goals**: przełączenie motywu odczuwalnie natychmiastowe, bez przeładowania strony;
zero błysku motywu przy starcie; brak wzrostu bundla ponad istniejący budżet (ostrzeżenie 500 kB,
błąd 1 MB dla `initial`)

**Constraints**: WCAG 2.1 AA w obu motywach; użyteczność od 320 px; brak zmian w backendzie,
modelu konta i sesji; brak zmian układu ekranów

**Scale/Scope**: 12 komponentów ze stylami inline, 1 arkusz globalny, ~13 ekranów, 2 motywy,
1 nowa usługa, 1 nowy komponent przełącznika, 1 nowe zadanie CI

## Constitution Check

*GATE: Musi przejść przed Phase 0. Ponowna weryfikacja po Phase 1.*

Konstytucja w wersji **1.5.0**.

| Zasada | Ocena | Uzasadnienie |
| --- | --- | --- |
| **I. Test-First (NON-NEGOTIABLE)** | ✅ PASS | Audyt kontrastu, test parzystości ról, skan literalnych kolorów i testy `ThemeService` powstają i czerwienią się przed wdrożeniem palety. Kolejność wymuszona przez plan zadań. |
| **II. Ochrona danych pacjenta / RODO** | ✅ PASS (nie dotyczy) | Feature nie odczytuje, nie zapisuje ani nie przesyła danych pacjenta. Preferencja motywu nie jest daną osobową (zob. data-model.md). |
| **III. Pełna audytowalność** | ✅ PASS (nie dotyczy) | Brak operacji CRUD na danych pacjenta lub klinicznych. Zmiana motywu **nie może** trafiać do logu audytowego — nie jest zdarzeniem klinicznym i zaśmiecałaby dziennik. |
| **IV. Mobile-First** | ✅ PASS | FR-016 i FR-024 są w zakresie; projekty Playwrighta są już skonfigurowane mobile-first, a przełącznik musi być osiągalny od 320 px. |
| **V. Warstwowa dostępność wg ryzyka** | ✅ PASS (nie dotyczy) | Feature jest przekrojową warstwą prezentacji, nie modułem. Nie wprowadza ani nie modyfikuje modułu wysokiego ryzyka, więc nie zmienia granic domen awarii i nie wymaga dokumentowania podejścia do dostępności. |
| **VI. Infrastruktura i dostarczanie jako kod (NON-NEGOTIABLE)** | ✅ PASS | Nowa bramka (`frontend-e2e-theme`) powstaje jako plik workflow w `.github/workflows/ci.yml`. Zero kroków ręcznych. |

### Ograniczenia stosu technologicznego

Bez odstępstw. Angular pozostaje frameworkiem frontendu, feature nie dotyka backendu Java,
PostgreSQL, Terraform, Helm ani Argo CD. Nie dochodzi żadna zależność produkcyjna — całość
opiera się na już zainstalowanym `@angular/material`.

### Bramki jakości — analiza szczegółowa

Rozstrzygnięcie, które decyduje o trybie merge'a, wymaga precyzji, bo feature **dotyka pliku
`login.component.ts`**, a więc pozornie ociera się o uwierzytelnianie.

**Ocena: feature NIE uruchamia wymogu udokumentowanego przeglądu bezpieczeństwa.** Konstytucja
wymaga go dla zmian dotykających „danych pacjenta, uwierzytelniania, autoryzacji lub logowania
audytowego". Zmiana w `login.component.ts` ogranicza się do warstwy prezentacji — kolorów,
znaku marki i osadzenia przełącznika. Nie dotyka przepływu logowania, walidacji poświadczeń,
obsługi MFA, tokenów ani sesji. Prezentacja ekranu uwierzytelniania to nie logika
uwierzytelniania.

Konsekwencja: zielone CI wystarcza do merge'a, auto-merge jest dopuszczalny.

**Trzy wyzwalacze, które unieważniają tę ocenę.** Jeżeli którykolwiek zajdzie w trakcie
implementacji, wymóg przeglądu wraca, auto-merge musi zostać wyłączony, a plan zaktualizowany:

1. `ThemeService` zaczyna zależeć od `AuthService`, sesji lub modelu konta.
2. Preferencja motywu trafia do profilu użytkownika, cookie wysyłanego do backendu lub do
   ładunku tokenu.
3. Zmiana motywu zaczyna generować wpis w logu audytowym.

Granica jest zapisana wprost jako niezmiennik kontraktu usługi
(`contracts/theme-preference.md`, sekcja 3) i jako sekcja „Co NIE powstaje" w `data-model.md`,
żeby dało się ją sprawdzić w przeglądzie kodu, a nie tylko w intencji.

### Wynik bramki: PASS — brak naruszeń wymagających uzasadnienia

## Project Structure

### Documentation (this feature)

```text
specs/003-brand-ui-theme/
├── plan.md              # Ten plik
├── research.md          # Phase 0 — 10 ustaleń zweryfikowanych na źródle Material 21.2.14
├── data-model.md        # Phase 1 — preferencja motywu, struktura tokenów
├── quickstart.md        # Phase 1 — jak uruchomić i zweryfikować
├── contracts/
│   ├── design-tokens.md      # Role semantyczne, wartości, mapowanie na --mat-sys-*
│   └── theme-preference.md   # Kontrakty DOM, magazynu, usługi, audytu, integracji
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — tworzone przez /speckit-tasks, NIE przez /speckit-plan
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── index.html                          # ZMIANA: meta color-scheme + skrypt inline (R8)
│   ├── styles.scss                         # ZMIANA: mat.theme() z paletą marki + $overrides
│   ├── styles/
│   │   ├── _pu-theme.scss                  # NOWY: mapowanie ról marki na --mat-sys-*
│   │   ├── _pu-palettes.scss               # NOWY: palety tonalne z schematu theme-color
│   │   └── brand-tokens.ts                 # NOWY: źródło prawdy dla audytu (FR-018)
│   └── app/
│       ├── core/
│       │   ├── theme/
│       │   │   ├── theme.service.ts        # NOWY: choice/resolved/set/toggle
│       │   │   ├── theme.service.spec.ts   # NOWY: magazyn, degradacja, dwie karty
│       │   │   ├── theme-toggle.component.ts       # NOWY: przełącznik (FR-008, FR-015)
│       │   │   └── theme-toggle.component.spec.ts  # NOWY
│       │   └── shell/
│       │       └── app-shell.component.ts  # ZMIANA: osadzenie przełącznika, kolory
│       └── features/
│           ├── auth/                       # ZMIANA ×4: login, mfa-challenge, 2× password-reset
│           ├── home/role-home.component.ts # ZMIANA
│           ├── patients/                   # ZMIANA ×4: search, create, detail, tooth-chart
│           └── admin/                      # ZMIANA ×2: accounts, audit-log
├── src/styles/
│   ├── contrast-audit.spec.ts              # NOWY: progi WCAG, obie palety (FR-017, FR-018)
│   ├── token-parity.spec.ts                # NOWY: .ts ↔ .scss, komplet ról (FR-002, FR-007)
│   └── no-literal-colors.spec.ts           # NOWY: statyczny skan komponentów (FR-001)
└── e2e/
    ├── us2-theme-toggle.spec.ts            # NOWY: przełącznik, trwałość, brak błysku
    └── us2-theme-contrast.spec.ts          # NOWY: realny kontrast, 320 px, oba motywy

.github/workflows/ci.yml                    # ZMIANA: nowe zadanie frontend-e2e-theme (R6)

design/brand/_pu-tokens.scss                # ZMIANA: role schematu uzębienia, focus-ring (R9)
```

**Structure Decision**: Zachowujemy istniejący układ aplikacji webowej. Feature jest w całości
frontendowy — `backend/`, `patient-service/`, `helm/` i `infra/` pozostają nietknięte, co jest
zarazem strukturalnym potwierdzeniem oceny bramki bezpieczeństwa powyżej.

Dwie decyzje strukturalne warte odnotowania. Po pierwsze, `ThemeService` i przełącznik trafiają
do `src/app/core/`, a nie do `features/`, bo są przekrojowe i muszą działać przed
zalogowaniem — umieszczenie ich w feature'cie za strażnikiem trasy złamałoby FR-008. Po drugie,
testy audytu leżą obok tokenów w `src/styles/`, a nie w katalogu komponentów, bo dotyczą samego
systemu kolorystycznego, nie żadnego pojedynczego widoku.

### Kolejność wdrożenia wymuszona przez Principle I

```text
1. Testy (czerwone)   → contrast-audit, token-parity, no-literal-colors, theme.service
2. Tokeny             → brand-tokens.ts + uzupełnienie _pu-tokens.scss  → testy 1 zielenieją
3. Motyw Material     → _pu-palettes, _pu-theme, styles.scss            → skan kolorów zielenieje
4. Usługa i przełącznik → theme.service, theme-toggle, index.html
5. Ekrany             → 12 komponentów, usunięcie literalnych kolorów
6. Bramka CI          → frontend-e2e-theme + specyfikacje Playwrighta
```

Każda faza kończy się przejściem pełnego zestawu testów i commitem — zgodnie z wymogiem
Development Workflow & Quality Gates konstytucji.

## Constitution Re-Check (po Phase 1)

Ponowna weryfikacja po wytworzeniu `research.md`, `data-model.md`, `contracts/` i `quickstart.md`.
Projekt nie wprowadził żadnego nowego naruszenia — wynik nadal **PASS** — ale wyostrzył trzy
rzeczy, które przed fazą projektową były tylko deklaracjami.

| Zasada | Co zmieniła faza projektowa |
| --- | --- |
| **I. Test-First** | Z deklaracji „napiszemy testy najpierw" zrobił się konkretny mechanizm: cztery zestawy w Vitest nad wspólnym źródłem prawdy (`brand-tokens.ts`), plus wymóg z `quickstart.md` §1, by **udowodnić, że audyt czerwieni się** na celowo zepsutej wartości. Test, który nigdy nie zawiódł, nie jest bramką. |
| **III. Pełna audytowalność** | Doprecyzowane w formie zakazu, nie tylko braku: zmiana motywu **nie może** trafiać do logu audytowego (`data-model.md`, „Co NIE powstaje"). Zaśmiecanie dziennika klinicznego zdarzeniami prezentacyjnymi osłabiłoby jego wartość dowodową. |
| **VI. Dostarczanie jako kod** | Bramka przeglądarkowa przestała być teoretyczna: R6 pokazał, że da się ją uruchomić bez backendu, bo przełącznik jest przed logowaniem. Bez tego ustalenia FR-026 i FR-027 byłyby niebramkowane. |

### Bramka bezpieczeństwa — potwierdzona strukturalnie

Ocena z Constitution Check („brak wymogu udokumentowanego przeglądu") była przed fazą projektową
oparta na intencji. Teraz jest weryfikowalna mechanicznie: sekcja „Co NIE powstaje"
w `data-model.md`, niezmienniki kontraktu usługi w `contracts/theme-preference.md` §3 oraz
ostatni punkt definicji ukończenia w `quickstart.md` (`backend/`, `patient-service/`, `helm/`,
`infra/` bez zmian). Trzy wyzwalacze unieważniające ocenę pozostają w mocy.

### Nowe ustalenie o randze projektowej, nie naruszenie

Faza projektowa wykryła, że **rozróżnienie stanu zęba nie może opierać się na kolorze**:
wypełnienia zdrowy/chory dają wobec siebie 1.23:1 (jasny) i 1.09:1 (ciemny). Nie jest to usterka
palety — dwa wypełnienia o wysokim wzajemnym kontraście zerwałyby spójność schematu. Oznacza to,
że drugi sygnał wymagany przez FR-019 jest w schemacie uzębienia **nośnikiem znaczenia
klinicznego**, a nie udogodnieniem dostępności. `/speckit-tasks` musi potraktować go jako zadanie
funkcjonalne o tej samej randze co samo przekolorowanie, nie jako polish.

## Complexity Tracking

> Constitution Check nie wykazał naruszeń wymagających uzasadnienia. Sekcja pozostaje pusta.

Odnotowane odstępstwo bez rangi naruszenia: plan **dodaje** zadanie CI zamiast odblokować
istniejące `frontend-e2e`. Nie jest to obejście Principle VI — nowa bramka jest w pełni
zdefiniowana w kodzie. Włączenie istniejącego zadania wymagałoby wyprowadzenia Postgresa
i LocalStacka, co leży poza zakresem tego feature'u i natychmiast czerwieniłoby CI. Powód
zapisano w `research.md` (R6), żeby przyszły `/speckit-converge` nie potraktował tego jako
przeoczenia.
