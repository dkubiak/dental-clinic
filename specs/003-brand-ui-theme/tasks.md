---

description: "Task list for 003-brand-ui-theme"
---

# Tasks: Branding i motyw UI (Projekt Uśmiech)

**Input**: Design documents from `/specs/003-brand-ui-theme/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: **WYMAGANE.** Principle I konstytucji jest NON-NEGOTIABLE (testy przed implementacją,
Red-Green-Refactor), a FR-018 wprost żąda automatycznego audytu kontrastu. Zadania testowe nie
są tu opcjonalne.

**Organization**: Zadania pogrupowane wg historii użytkownika ze `spec.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: może działać równolegle (inny plik, brak zależności)
- **[Story]**: US1 … US5 — mapowanie na historie ze `spec.md`
- Każde zadanie podaje konkretną ścieżkę pliku

## Path Conventions

Aplikacja webowa. Ten feature dotyka **wyłącznie** `frontend/`, `design/brand/` i jednego pliku
workflow. `backend/`, `patient-service/`, `helm/`, `infra/` pozostają nietknięte — jest to
strukturalne potwierdzenie oceny bramki bezpieczeństwa z `plan.md`.

## ⚠️ Kolejność faz odbiega od kolejności priorytetów — świadomie

Historie w `spec.md` **nie są** tu w pełni niezależne i udawanie inaczej byłoby nieuczciwe.
Wymusza to Principle I w połączeniu z jednym wspólnym systemem tokenów:

```text
US4 (audyt)  ──►  US1 (przekolorowanie)  ──┬──►  US2 (przełącznik)
   ▲                                       ├──►  US3 (stany)
   │                                       └──►  US5 (mobile)
   └── sekwencjonowane pierwsze mimo P2, bo Principle I wymaga,
       by test istniał i czerwienił przed implementacją
```

- **US4 idzie pierwsze mimo priorytetu P2.** Priorytet mówi o ważności, nie o kolejności.
  Audyt jest testem, który musi zaczerwienić się przed wdrożeniem palety.
- **US1 pozostaje MVP** — to ono dostarcza wartość widoczną dla gabinetu.
- **US2, US3 i US5 są względem siebie niezależne** i mogą iść równolegle po zakończeniu US1.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Wygenerowanie palet tonalnych i uzbrojenie projektu w to, czego brakuje do
uruchomienia weryfikacji bez backendu.

- [X] T001 Wygeneruj palety tonalne M3 zasiane kolorami marki do `frontend/src/styles/_pu-palettes.scss` poleceniem `npx ng generate @angular/material:theme-color --isScss --directory=src/styles --primaryColor=#CBAD89 --secondaryColor=#3E7A72 --tertiaryColor=#3E7A72 --neutralColor=#2E2C2D --neutralVariantColor=#5C5654 --errorColor=#A33A32` (research.md R4)
- [X] T002 [P] Dodaj devDependency `http-server` oraz skrypty `serve:dist` i `e2e:theme` w `frontend/package.json`, tak aby specyfikacje motywu dało się uruchomić przeciwko zbudowanej aplikacji bez backendu (research.md R6)
- [X] T003 [P] Uzupełnij `design/brand/_pu-tokens.scss` o siedem brakujących ról: `border-strong`, `focus-ring`, `focus-ring-on-accent`, `tooth-healthy-fill`, `tooth-healthy-stroke`, `tooth-diseased-fill`, `tooth-diseased-stroke`, `tooth-selected-stroke` — wartości wg `contracts/design-tokens.md` §1

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Narzędzie liczące kontrast, na którym opiera się cały audyt. Bez niego żadna
historia nie ma jak się zweryfikować.

**⚠️ CRITICAL**: Żadna historia nie może ruszyć przed ukończeniem tej fazy.

- [X] T004 Napisz **czerwony** test narzędzia kontrastu w `frontend/src/styles/contrast.spec.ts` — znane pary kontrolne: `#000000`/`#FFFFFF` = 21.00, `#FFFFFF`/`#FFFFFF` = 1.00, `#7A5A2E`/`#FAF7F2` = 5.90, `#CBAD89`/`#FAF7F2` = 1.99 (tolerancja ±0.01)
- [X] T005 Zaimplementuj `frontend/src/styles/contrast.ts` — luminancja względna sRGB i współczynnik kontrastu wg WCAG 2.1; T004 zielenieje

**Checkpoint**: Narzędzie pomiarowe działa i samo jest przetestowane. Można budować audyt.

---

## Phase 3: User Story 4 — Czytelność potwierdzona automatycznie (Priority: P2) 🔒 sekwencjonowane pierwsze

**Goal**: Powstaje strażnik, który uniemożliwi wprowadzenie nieczytelnego zestawienia kolorów —
w obu motywach, przy każdym uruchomieniu testów.

**Independent Test**: Zepsuj celowo jedną wartość w `brand-tokens.ts` i sprawdź, że zestaw
testów zgłasza porażkę wskazującą motyw, parę ról i uzyskany współczynnik.

### Tests for User Story 4 ⚠️

> **Napisz je NAJPIERW. Wszystkie trzy MUSZĄ czerwienić przed przejściem dalej.**

- [X] T006 [P] [US4] Napisz **czerwony** `frontend/src/styles/contrast-audit.spec.ts` — dla każdej pary z `contrast-pairs.ts` sprawdza `minRatio` osobno w motywie jasnym i ciemnym; komunikat porażki podaje motyw, obie role, próg i wartość uzyskaną (asercje A2, A3 z `contracts/theme-preference.md` §4). Czerwony, bo `brand-tokens.ts` jeszcze nie istnieje
- [X] T007 [P] [US4] Napisz **czerwony** `frontend/src/styles/token-parity.spec.ts` — parsuje `design/brand/_pu-tokens.scss`, porównuje wartości z `brand-tokens.ts` i sprawdza, że każda rola ma wartość w obu motywach (asercje A1, A5)
- [X] T008 [P] [US4] Napisz **czerwony** `frontend/src/styles/no-literal-colors.spec.ts` — skanuje dwanaście plików komponentów w `frontend/src/app/` na obecność `#rgb`/`#rrggbb`, `rgb()`, `hsl()`, nazw kolorów CSS oraz wzorca `var(--mat-sys-*, #fallback)` (asercja A6). Czerwony, bo siedem komponentów zawiera dziś takie wartości
- [X] T009 [P] [US4] Napisz **czerwony** `frontend/src/styles/theme-emission.spec.ts` — parsuje `frontend/src/styles/_pu-theme.scss` oraz `frontend/src/index.html` i sprawdza, że: (a) każda wartość w mapie `$overrides` jest zapisana jako `light-dark(#jasny, #ciemny)` — płaska wartość koloru kończy się porażką; (b) obecne są trzy nadpisania komponentowe `--mat-button-text-label-text-color`, `--mat-button-outlined-label-text-color`, `--mat-button-protected-label-text-color`; (c) wartości jasny/ciemny w `$overrides` zgadzają się z odpowiadającymi rolami w `frontend/src/styles/brand-tokens.ts`; (d) `index.html` deklaruje `<meta name="color-scheme" content="light dark">`. Pozostaje czerwony do Phase 4, tak samo jak T008 (research.md R2 i R3 — to jest strażnik, który R2 obiecywał, a T007 nie dostarczał)
- [X] T010 [US4] Dodaj do `frontend/src/styles/contrast-audit.spec.ts` strażnika A4 — para z rolą `accent` jako tekstem nad jasnym tłem MUSI zostać odrzucona, nawet gdyby ktoś dopisał ją do `contrast-pairs.ts`

### Implementation for User Story 4

- [X] T011 [US4] Utwórz `frontend/src/styles/brand-tokens.ts` — mapa `Record<RoleName, {light, dark}>` ze wszystkimi rolami z `contracts/design-tokens.md` §1; T007 zielenieje
- [X] T012 [US4] Utwórz `frontend/src/styles/contrast-pairs.ts` — lista par podlegających audytowi z progami 4.5 i 3.0 wg `contracts/design-tokens.md` §2, z jawnym pominięciem par wyłączonych (`border` wobec tła, wypełnienia zęba wobec tła); T006 zielenieje
- [X] T013 [US4] Udowodnij, że audyt naprawdę czerwieni (quickstart.md §1): w `frontend/src/styles/brand-tokens.ts` podmień `accent-text.light` na `#CBAD89`, uruchom `npm test`, potwierdź komunikat z wartością 1.99, cofnij zmianę i potwierdź zieleń. Powtórz dla `frontend/src/styles/token-parity.spec.ts`, usuwając wartość `dark` dowolnej roli

**Checkpoint**: T006, T007 i T010 zielone. **T008 i T009 pozostają czerwone celowo** — T008
zzielenieje, gdy US1 usunie twarde kolory z komponentów, a T009 gdy US1 utworzy `_pu-theme.scss`.
To jest poprawny stan TDD, nie usterka.

---

## Phase 4: User Story 1 — Personel widzi aplikację w kolorach gabinetu (Priority: P1) 🎯 MVP

**Goal**: Wszystkie trzynaście ekranów przestaje być fioletowe i zaczyna wyglądać jak Projekt
Uśmiech — w obu motywach.

**Independent Test**: Przejdź przez wszystkie ekrany w motywie jasnym i ciemnym; żaden nie
prezentuje koloru spoza systemu, a `no-literal-colors.spec.ts` jest zielony.

**Depends on**: Phase 3 (potrzebuje `brand-tokens.ts` i działającego audytu).

### Tests for User Story 1 ⚠️

> **Napisz przed implementacją. MUSI czerwienić.**

- [X] T014 [P] [US1] Napisz **czerwony** test obecności znaku marki — rozszerz `frontend/src/app/features/auth/login/login.component.spec.ts` oraz `frontend/src/app/core/shell/app-shell.component.spec.ts` o asercję, że znak marki jest obecny w drzewie DOM z tekstem alternatywnym i renderuje się poprawnie w obu motywach (FR-023). Oba pliki już istnieją — rozszerzasz je, nie tworzysz

### Implementation for User Story 1

- [X] T015 [US1] Utwórz `frontend/src/styles/_pu-theme.scss` — mapa `$overrides` odwzorowująca role marki na zmienne `--mat-sys-*`, **każda wartość jako `light-dark(#jasny, #ciemny)`** wg `contracts/design-tokens.md` §3 (research.md R2 — zwykła wartość zabiłaby zmienność motywu)
- [X] T016 [US1] Dodaj do `frontend/src/styles/_pu-theme.scss` trzy obowiązkowe nadpisania komponentowe: `--mat-button-text-label-text-color`, `--mat-button-outlined-label-text-color`, `--mat-button-protected-label-text-color` na `light-dark(#7A5A2E, #E3C9A6)` (research.md R3 — bez nich złoto stałoby się etykietą przycisku tekstowego przy 1.99:1)
- [X] T017 [US1] Przepisz `frontend/src/styles.scss` — `mat.theme()` z paletami z `_pu-palettes.scss` i nadpisaniami z `_pu-theme.scss`; usuń `mat.$violet-palette` i komentarz odraczający brandowanie
- [X] T018 [US1] Dodaj `<meta name="color-scheme" content="light dark">` do `<head>` w `frontend/src/index.html`, aby przeglądarka znała obsługiwane schematy przed załadowaniem CSS
- [X] T019 [P] [US1] Przekoloruj `frontend/src/app/core/shell/app-shell.component.ts` na role systemu; usuń wszelkie literalne wartości
- [X] T020 [P] [US1] Przekoloruj `frontend/src/app/features/auth/login/login.component.ts`; usuń fallback `var(--mat-sys-error, #b3261e)`
- [X] T021 [P] [US1] Przekoloruj `frontend/src/app/features/auth/login/mfa-challenge.component.ts`; usuń fallbacki `#b3261e` i `#f2f2f2`
- [X] T022 [P] [US1] Przekoloruj `frontend/src/app/features/auth/password-reset/password-reset-request.component.ts`
- [X] T023 [P] [US1] Przekoloruj `frontend/src/app/features/auth/password-reset/password-reset-confirm.component.ts`; usuń fallback `#b3261e`
- [X] T024 [P] [US1] Przekoloruj `frontend/src/app/features/home/role-home.component.ts`
- [X] T025 [P] [US1] Przekoloruj `frontend/src/app/features/patients/patient-search/patient-search.component.ts`
- [X] T026 [P] [US1] Przekoloruj `frontend/src/app/features/patients/patient-create/patient-create.component.ts`; usuń fallback `#b3261e`
- [X] T027 [P] [US1] Przekoloruj `frontend/src/app/features/patients/patient-detail/patient-detail.component.ts`; usuń fallback `#b3261e`
- [X] T028 [P] [US1] Przekoloruj `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.ts` — zastąp `fill: #ffffff`, `stroke: #666`, `fill: #f3b0ae` i `stroke: #1a73e8` rolami `tooth-*`; zaznaczenie przechodzi z obcego marce błękitu na złoto (research.md R9)
- [X] T029 [P] [US1] Przekoloruj `frontend/src/app/features/admin/accounts/accounts.component.ts`; usuń fallback `#b3261e`
- [X] T030 [P] [US1] Przekoloruj `frontend/src/app/features/admin/audit-log/audit-log.component.ts`
- [X] T031 [US1] Osadź znak marki na ekranie logowania w `frontend/src/app/features/auth/login/login.component.ts` i w pasku `frontend/src/app/core/shell/app-shell.component.ts`, w wariancie czytelnym na jasnym i ciemnym tle (FR-023)
- [X] T032 [US1] Uruchom `npm test` w `frontend/` i potwierdź, że `frontend/src/styles/no-literal-colors.spec.ts` **zzieleniał**, a `frontend/src/styles/contrast-audit.spec.ts` nadal jest zielony

**Checkpoint**: MVP osiągnięty. Aplikacja wygląda jak Projekt Uśmiech, cały zestaw testów
jednostkowych jest zielony. Nadaje się do pokazania właścicielowi.

---

## Phase 5: User Story 2 — Przełącznik motywu dostępny zawsze (Priority: P2)

**Goal**: Użytkownik przełącza motyw z każdego ekranu, także przed zalogowaniem, a urządzenie
pamięta wybór.

**Independent Test**: Przełącz motyw na ekranie logowania, odśwież stronę, sprawdź, że wybór
został odtworzony; powtórz w trybie prywatnym i sprawdź, że nie pojawia się błąd.

**Depends on**: Phase 4. Niezależne od US3 i US5.

### Tests for User Story 2 ⚠️

- [X] T033 [P] [US2] Napisz **czerwony** `frontend/src/app/core/theme/theme.service.spec.ts` — zapis i odczyt klucza `pu.theme`, wartość nieznana traktowana jak brak wpisu, wyjątek przy odczycie i zapisie nie propaguje, `resolved` śledzi `prefers-color-scheme` tylko w stanie `system`, jawny wybór wygrywa ze zmianą systemu, zdarzenie `storage` z innej karty stosuje zmianę, klucz `pu.theme` pozostaje nietknięty po symulacji wylogowania (FR-011, US2 scenariusz 7)
- [X] T034 [P] [US2] Napisz **czerwony** `frontend/src/app/core/theme/theme-toggle.component.spec.ts` — przełącznik ma `data-testid="theme-toggle"`, dostępną nazwę komunikującą stan i skutek, jest osiągalny klawiaturą
- [X] T035 [P] [US2] Napisz **czerwony** `frontend/e2e/us2-theme-toggle.spec.ts` — przełącznik obecny na ekranie logowania przed uwierzytelnieniem, przełączenie bez przeładowania i bez utraty wpisanych danych, trwałość po odświeżeniu, brak błysku przy zapamiętanym motywie ciemnym, propagacja między dwiema kartami

### Implementation for User Story 2

- [X] T036 [US2] Zaimplementuj `frontend/src/app/core/theme/theme.service.ts` — sygnały `choice` i `resolved`, metody `set()` i `toggle()`, ustawianie `color-scheme` na `document.documentElement` wg `contracts/theme-preference.md` §3
- [X] T037 [US2] Otocz każdy dostęp do `localStorage` w `frontend/src/app/core/theme/theme.service.ts` blokiem `try/catch` — w trybie prywatnym rzuca sam **odczyt**, nie tylko zapis; nieznana wartość równa się brakowi wpisu (FR-014)
- [X] T038 [US2] Dodaj w `frontend/src/app/core/theme/theme.service.ts` nasłuch zdarzenia `storage` na kluczu `pu.theme`, stosujący zmianę w pozostałych kartach urządzenia (research.md R10)
- [X] T039 [US2] Zaimplementuj `frontend/src/app/core/theme/theme-toggle.component.ts` — kontrolka przełączająca z `data-testid="theme-toggle"`, dostępną nazwą i widocznym fokusem korzystającym z roli `focus-ring`
- [X] T040 [US2] Osadź przełącznik w pasku `frontend/src/app/core/shell/app-shell.component.ts`
- [X] T041 [US2] Osadź przełącznik na czterech ekranach przed zalogowaniem: `frontend/src/app/features/auth/login/login.component.ts`, `frontend/src/app/features/auth/login/mfa-challenge.component.ts`, `frontend/src/app/features/auth/password-reset/password-reset-request.component.ts`, `frontend/src/app/features/auth/password-reset/password-reset-confirm.component.ts` (FR-008: przełącznik działa także bez sesji)
- [X] T042 [US2] Dodaj synchroniczny skrypt inline w `<head>` pliku `frontend/src/index.html`, ustawiający `color-scheme` przed pierwszym malowaniem, odporny na wyjątek z `localStorage` (research.md R8, FR-027)
- [X] T043 [US2] Dodaj zadanie `frontend-e2e-theme` w `.github/workflows/ci.yml` — build, statyczny serwer, `npx playwright test e2e/us2-theme-*.spec.ts`; **nie ruszaj** wyłączonego zadania `frontend-e2e` (research.md R6)
- [X] T044 [US2] Zweryfikuj brak utraty stanu przy przełączeniu — otwórz ekran z `frontend/src/app/features/patients/patient-create/patient-create.component.ts`, wypełnij pola, przełącz motyw, potwierdź, że dane i pozycja przewijania zostały (FR-009)

**Checkpoint**: Przełącznik działa na każdym ekranie, jest bramkowany w CI.

---

## Phase 6: User Story 3 — Stan i komunikat czytelny od pierwszego spojrzenia (Priority: P2)

**Goal**: Każdy typ komunikatu i każdy stan są rozpoznawalne bez postrzegania koloru, w obu
motywach.

**Independent Test**: Wyrenderuj po jednym komunikacie każdego typu w obu motywach i odczytaj je
w symulacji deuteranopii oraz w skali szarości.

**Depends on**: Phase 4. Niezależne od US2 i US5.

### Tests for User Story 3 ⚠️

- [X] T045 [P] [US3] Napisz **czerwony** `frontend/src/app/shared/status/status-indicator.component.spec.ts` — każdy z czterech typów (sukces, ostrzeżenie, błąd, informacja) renderuje kolor **oraz** etykietę tekstową lub ikonę; brak drugiego sygnału kończy się porażką (FR-019)
- [X] T046 [P] [US3] Napisz **czerwony** test drugiego sygnału na schemacie uzębienia w `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.spec.ts` — stan chory MUSI być odróżnialny od zdrowego atrybutem innym niż wypełnienie

### Implementation for User Story 3

- [X] T047 [US3] Zaimplementuj `frontend/src/app/shared/status/status-indicator.component.ts` — wspólny komponent stanu kodujący typ kolorem i ikoną plus etykietą, korzystający z ról funkcyjnych systemu
- [X] T048 [US3] Dodaj drugi sygnał niezależny od koloru do `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.ts` — wypełnienia zdrowy/chory dają wobec siebie **1.23:1** (jasny) i **1.09:1** (ciemny), więc sam kolor nie przenosi tu informacji klinicznej; to zadanie funkcjonalne, nie kosmetyczne (contracts/design-tokens.md §2)
- [ ] T049 [US3] Zastosuj `status-indicator` do ostrzeżenia o alergii w `frontend/src/app/features/patients/patient-detail/patient-detail.component.ts`, nadając mu wagę wizualnie silniejszą niż komunikaty niekrytyczne (US3 scenariusz 4)
- [X] T050 [US3] Zweryfikuj w `frontend/src/app/shared/status/status-indicator.component.ts` rozróżnialność roli `warning` od roli `accent` w obu motywach — kolor ostrzeżenia jest celowo bardziej pomarańczowy niż złoto (FR-006)
- [X] T051 [US3] Wykonaj sprawdzenie w symulacji deuteranopii i protanopii dla czterech typów komunikatu renderowanych przez `frontend/src/app/shared/status/status-indicator.component.ts`, w obu motywach (US3 scenariusz 3)

**Checkpoint**: Znaczenie nigdy nie zależy wyłącznie od koloru.

---

## Phase 7: User Story 5 — Czytelność przy fotelu, na tablecie (Priority: P2)

**Goal**: Wszystkie ekrany działają od 320 px w obu motywach, a przełącznik jest tam osiągalny.

**Independent Test**: Otwórz kluczowe ekrany kliniczne przy 320 px w obu motywach i sprawdź brak
poziomego przewijania oraz osiągalność przełącznika.

**Depends on**: Phase 4 (i Phase 5 dla sprawdzeń przełącznika). Niezależne od US3.

### Tests for User Story 5 ⚠️

- [ ] T052 [P] [US5] Napisz **czerwony** `frontend/e2e/us5-theme-contrast.spec.ts` — realny kontrast na wyrenderowanych ekranach w obu motywach, brak poziomego przewijania przy 320 px, osiągalność przełącznika bez zagnieżdżenia głębszego niż jeden poziom

### Implementation for User Story 5

- [ ] T053 [US5] Zapewnij osiągalność przełącznika przy 320 px w `frontend/src/app/core/shell/app-shell.component.ts` — bez poziomego przewijania i bez chowania go w zagnieżdżonym menu (FR-016)
- [ ] T054 [US5] Usuń poziome przewijanie wykryte przez T052 na dotkniętych ekranach; szeroka treść dostaje własny kontener z `overflow-x: auto` — kandydaci to `frontend/src/app/features/admin/audit-log/audit-log.component.ts`, `frontend/src/app/features/admin/accounts/accounts.component.ts` i `frontend/src/app/features/patients/patient-search/patient-search.component.ts`
- [ ] T055 [US5] Zapewnij rozpoznawalność stanu aktywnego/wciśniętego przy obsłudze dotykiem w `frontend/src/styles/_pu-theme.scss` (tokeny stanów Material) oraz w `frontend/src/app/core/theme/theme-toggle.component.ts`, gdy element jest częściowo zasłonięty palcem (FR-025)
- [ ] T056 [US5] Zweryfikuj użyteczność przy powiększeniu tekstu do 200% w obu motywach; dopisz sprawdzenie do `frontend/e2e/us5-theme-contrast.spec.ts` (FR-021)
- [ ] T057 [US5] Uruchom `frontend/e2e/us2-theme-toggle.spec.ts` i `frontend/e2e/us5-theme-contrast.spec.ts` na wszystkich czterech projektach z `frontend/playwright.config.ts` (Pixel 7, iPhone 14, iPad Mini landscape, Desktop Chrome) i potwierdź zieleń

**Checkpoint**: Aplikacja użyteczna chairside, w obu motywach, na każdym wspieranym urządzeniu.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Wymagania przekrojowe, których nie da się przypisać do jednej historii.

- [ ] T058 [P] Dodaj reguły wydruku w `frontend/src/styles.scss` — wydruk MUSI NIE dziedziczyć ciemnego tła z motywu ciemnego, a informacja zakodowana kolorem musi przetrwać skalę szarości (FR-028)
- [ ] T059 [P] Dodaj i zweryfikuj reguły `@media (forced-colors: active)` w `frontend/src/styles.scss` — aplikacja pozostaje użyteczna, a przełącznik z `frontend/src/app/core/theme/theme-toggle.component.ts` nie wprowadza w błąd (FR-021)
- [ ] T060 Zweryfikuj, że kontrolki przeglądarki (pola formularza, autouzupełnianie, paski przewijania, natywne okna dialogowe) przyjmują motyw aplikacji, a nie systemu operacyjnego; dopisz sprawdzenie do `frontend/e2e/us5-theme-contrast.spec.ts` (FR-026)
- [ ] T061 [P] Zaktualizuj `design/brand/README.md` — dopisz siedem nowych ról, sekcję o pułapce `--mat-sys-primary` i odnośnik do wdrożonych tokenów
- [ ] T062 [P] Udokumentuj system motywów w `CLAUDE.md` — jak przełączać, gdzie leży źródło prawdy tokenów, dlaczego audyt jest w Vitest a nie w Playwrighcie. Przy okazji popraw dwie nieaktualne informacje w tym pliku: twierdzenie o braku kodu aplikacji oraz wersję konstytucji (jest 1.5.0, nie 1.3.1)
- [ ] T063 Sprawdź budżet bundla po zmianach — `initial` musi zmieścić się w ostrzeżeniu 500 kB z `frontend/angular.json`
- [ ] T064 Wykonaj pełną walidację wg `specs/003-brand-ui-theme/quickstart.md` — sekcje 1–3, w tym siedem sprawdzeń ręcznych i potwierdzenie przez `git diff --stat`, że `backend/`, `patient-service/`, `helm/` i `infra/` są bez zmian

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: bez zależności
- **Phase 2 (Foundational)**: po Phase 1 — BLOKUJE wszystkie historie
- **Phase 3 (US4)**: po Phase 2 — BLOKUJE US1, bo Principle I wymaga czerwonego testu przed implementacją
- **Phase 4 (US1)**: po Phase 3 — BLOKUJE US2, US3, US5, bo dostarcza system tokenów
- **Phase 5 (US2)**, **Phase 6 (US3)**, **Phase 7 (US5)**: po Phase 4, wzajemnie niezależne
- **Phase 8 (Polish)**: po wszystkich wybranych historiach

### Uwaga o niezależności historii

Szablon zakłada, że historie są wzajemnie niezależne. Tutaj **nie są** i jest to
udokumentowana konsekwencja, nie przeoczenie: wszystkie pięć historii dzieli jeden system
tokenów, a Principle I wymusza, by audyt istniał przed nim. Niezależnie testowalne pozostają
US2, US3 i US5 — po ukończeniu US1 można je realizować i weryfikować w dowolnej kolejności,
także równolegle.

### Within Each User Story

- Testy MUSZĄ być napisane i czerwone przed implementacją (Principle I)
- Tokeny przed motywem, motyw przed komponentami
- Usługa przed komponentem przełącznika, komponent przed osadzeniem
- Każda faza kończy się zielonym pełnym zestawem testów **i commitem** (wymóg Development
  Workflow & Quality Gates — faza nie może zostać porzucona z czerwonym zestawem ani
  z niezacommitowanymi zmianami)

### Parallel Opportunities

- **Phase 1**: T002 i T003 równolegle
- **Phase 3**: T006, T007, T008 i T009 równolegle (cztery różne pliki testowe)
- **Phase 4**: T019–T030 równolegle — **dwanaście komponentów, dwanaście osobnych plików**,
  to największa okazja do zrównoleglenia w całym feature'cie
- **Phase 5**: T033, T034, T035 równolegle
- **Phase 6**: T045 i T046 równolegle
- **Phase 8**: T058, T059, T061, T062 równolegle
- Po Phase 4: US2, US3 i US5 mogą iść równolegle przy większym zespole

---

## Parallel Example: User Story 1

```bash
# Dwanaście komponentów, dwanaście plików — pełne zrównoleglenie:
Task: "Przekoloruj app-shell.component.ts"
Task: "Przekoloruj login.component.ts"
Task: "Przekoloruj mfa-challenge.component.ts"
Task: "Przekoloruj password-reset-request.component.ts"
Task: "Przekoloruj password-reset-confirm.component.ts"
Task: "Przekoloruj role-home.component.ts"
Task: "Przekoloruj patient-search.component.ts"
Task: "Przekoloruj patient-create.component.ts"
Task: "Przekoloruj patient-detail.component.ts"
Task: "Przekoloruj tooth-chart.component.ts"
Task: "Przekoloruj accounts.component.ts"
Task: "Przekoloruj audit-log.component.ts"
```

Warunek: T015–T018 muszą być gotowe wcześniej — bez `_pu-theme.scss` komponenty nie mają do
czego się odwołać.

---

## Implementation Strategy

### MVP (Phases 1–4)

1. Phase 1: Setup — palety, skrypty, brakujące role
2. Phase 2: Foundational — narzędzie kontrastu
3. Phase 3: US4 — audyt czerwieni się
4. Phase 4: US1 — przekolorowanie; audyt zielenieje
5. **STOP i ZWERYFIKUJ**: aplikacja wygląda jak Projekt Uśmiech w obu motywach, zestaw testów
   zielony
6. Nadaje się do pokazania właścicielowi

Na tym etapie motyw ciemny **istnieje i jest przetestowany**, ale użytkownik nie ma jeszcze jak
go włączyć — to przychodzi z US2. Jest to sensowny punkt zatrzymania: MVP dostarcza pełną
wartość brandingową bez przełącznika.

### Incremental Delivery

1. MVP (Phases 1–4) → demo
2. + US2 (przełącznik) → demo — od tego momentu motyw ciemny jest dostępny dla personelu
3. + US3 (stany) → demo
4. + US5 (mobile) → demo
5. + Polish

### Parallel Team Strategy

Fazy 1–4 są z natury sekwencyjne (jeden system tokenów, jeden łańcuch TDD) — z wyjątkiem
dwunastu zadań komponentowych w Phase 4, które wchłoną dowolną liczbę osób. Dopiero po Phase 4
rozjazd na trzy niezależne strumienie: US2, US3, US5.

---

## Notes

- `[P]` = inny plik, brak zależności od niezakończonego zadania
- Potwierdź, że każdy test czerwieni, zanim go zazielenisz — T013 jest osobnym zadaniem właśnie
  po to, żeby to udowodnić, a nie założyć
- Commit po każdej fazie; faza nie może zostać opuszczona z czerwonym zestawem testów
- Trzy wyzwalacze unieważniające ocenę bramki bezpieczeństwa (`plan.md`): zależność
  `ThemeService` od `AuthService`, preferencja w profilu lub cookie, wpis w logu audytowym.
  Jeżeli którykolwiek zajdzie — zatrzymaj się, zaktualizuj plan i wyłącz auto-merge
- **T049 zablokowane (wykryte podczas implementacji Phase 6)**: zadanie zakłada, że kartoteka
  pacjenta ma pole "alergia" (spec.md US3 scenariusz 4: "Given kartoteka pacjenta z odnotowaną
  alergią"). Takie pole nie istnieje nigdzie w kodzie ani w module 002 (`PatientDetail` w
  `patients.models.ts` ma tylko dane podstawowe + adres) — spec tego feature'u zakłada dane
  pacjenta, których 002 nigdy nie zbudował. Dodanie realnego pola "alergia" byłoby zmianą modelu
  danych pacjenta (RODO Art. 9, Principle II/III), wymagającą własnej specyfikacji/planu i
  odblokowującą wymóg udokumentowanego przeglądu bezpieczeństwa, którego plan.md tego feature'u
  wprost unika (Constitution Check, "brak wymogu udokumentowanego przeglądu"). T045/T047
  (status-indicator) i T048 (drugi sygnał na schemacie uzębienia) są zrobione i niezależne od
  T049. T050/T051 zweryfikowane symulacją ślepoty barw na maketach z prawdziwymi tokenami —
  patrz komentarz w `status-indicator.component.ts`.
