# Phase 0 Research: Branding i motyw UI (Projekt Uśmiech)

**Feature**: `003-brand-ui-theme` | **Date**: 2026-08-27

Wszystkie ustalenia poniżej zweryfikowano na źródle `@angular/material@21.2.14` (rozpakowany
pakiet z npm), a nie na dokumentacji ani z pamięci. Numery linii odnoszą się do plików w tym
pakiecie.

---

## R1. Mechanizm przełączania motywu w Angular Material 21

**Decision**: Motyw przełączamy **wyłącznie właściwością CSS `color-scheme`** na elemencie
`<html>`. Nie podmieniamy klas, nie ładujemy drugiego arkusza, nie re-emitujemy zmiennych.

**Rationale**: `mat.theme($config, $overrides)` (`core/tokens/_system.scss:57`) domyślnie
przyjmuje `theme-type: color-scheme`, gdy konfiguracja go nie podaje (linie 63-66). Dla tego
typu `_generate-sys-colors()` (linie 248-256) emituje **każdy** token koloru jako
`light-dark($lightValue, $darkValue)`:

```scss
@if ($type == color-scheme) {
  $light-dark-sys-colors: ();
  @each $name, $light-value in $light-sys-colors {
    $dark-value: map.get($dark-sys-colors, $name);
    $light-dark-sys-colors:
        map.set($light-dark-sys-colors, $name, light-dark($light-value, $dark-value));
  }
  @return $light-dark-sys-colors;
}
```

Funkcja CSS `light-dark()` rozstrzyga się według właściwości `color-scheme` elementu. Daje to
dokładnie trzy stany, które **jeden do jednego** pokrywają się z wymaganiami specyfikacji:

| `color-scheme` na `<html>` | Efekt | Wymaganie |
| --- | --- | --- |
| `light dark` | podąża za systemem operacyjnym | FR-012 (brak zapisanej preferencji) |
| `light` | wymuszony motyw jasny | FR-013 (jawny wybór) |
| `dark` | wymuszony motyw ciemny | FR-013 (jawny wybór) |

Dodatkowa korzyść, która wprost realizuje FR-026: `color-scheme` jest natywną właściwością CSS,
więc przeglądarka stosuje ją także do kontrolek, nad którymi aplikacja nie ma pełnej kontroli —
pól formularza, autouzupełniania, pasków przewijania i natywnych okien dialogowych. Rozwiązanie
oparte na klasie CSS **nie** dałoby tego efektu i FR-026 wymagałby osobnej obsługi.

**Alternatives considered**:
- *Dwa wywołania `mat.theme()` pod klasami `.theme-light` / `.theme-dark`* — odrzucone: podwaja
  rozmiar emitowanego CSS, nie rozwiązuje FR-026 i wymaga ręcznej synchronizacji obu bloków.
- *`theme-type: light` plus nadpisania w `@media (prefers-color-scheme: dark)`* — odrzucone:
  media query nie da się nadpisać jawnym wyborem użytkownika bez duplikacji reguł, co łamie
  FR-013.

**Ryzyko**: `light-dark()` to Baseline 2024 (Chrome 123+, Safari 17.5+, Firefox 120+). Dla
przeglądarek personelu gabinetu (evergreen) jest to akceptowalne; zapisujemy to jako
udokumentowane ograniczenie, nie budujemy fallbacku.

---

## R2. Nadpisania muszą same być `light-dark()` — pułapka

**Decision**: Każda wartość przekazana w `$overrides` do `mat.theme()` MUSI być zapisana jako
`light-dark(#jasny, #ciemny)`.

**Rationale**: `system-level-colors()` (`core/tokens/_system.scss:231`) podstawia nadpisanie
**dosłownie**, zastępując całą wygenerowaną parę:

```scss
--#{$prefix}-#{$name}: #{map.get($overrides, $name) or $value};
```

Przekazanie zwykłego koloru (np. `primary: #CBAD89`) usuwa `light-dark()` i czyni token
**niezmiennym względem motywu** — token wyglądałby identycznie w obu motywach, cicho łamiąc
FR-007 i FR-017. Jest to najbardziej prawdopodobny błąd wdrożeniowy w tym feature i dlatego
zostaje objęty testem (zob. R5, test parzystości ról).

**Alternatives considered**: brak — to nie jest wybór projektowy, tylko właściwość API, którą
trzeba uszanować.

---

## R3. Konflikt: złoto marki a rola `--mat-sys-primary`

**Decision**: `--mat-sys-primary` zostaje **złotem marki** (rola płaszczyzny), a etykiety
przycisków tekstowych, obrysowanych i „protected" nadpisujemy osobno na wariant tekstowy
(brąz / złoto jasne).

**Rationale**: To jest miejsce, w którym paleta marki i system Material realnie się zderzają.
Material używa tego samego tokenu `primary` w dwóch przeciwnych rolach
(`button/_m3-button.scss`):

| Token komponentu | Wartość źródłowa | Rola |
| --- | --- | --- |
| `button-filled-label-text-color` (l. 62) | `on-primary` | etykieta **na** wypełnieniu |
| `button-text-label-text-color` (l. 108) | `primary` | etykieta **jako tekst** |
| `button-outlined-label-text-color` (l. 74) | `primary` | etykieta **jako tekst** |
| `button-protected-label-text-color` (l. 96) | `primary` | etykieta **jako tekst** |

Ustawienie `--mat-sys-primary` na złoto `#CBAD89` daje poprawny przycisk wypełniony
(złote tło + `on-primary` = `#1F1D1E` → **7.88:1**), ale jednocześnie ustawia etykietę przycisku
tekstowego na złoto na papierze → **1.99:1**, czyli dokładnie ta pułapka, którą FR-004 nazywa po
imieniu. Dotyczy to komponentów faktycznie używanych w aplikacji: `app-shell.component.ts`
używa `mat-button` (tekstowy) i `mat-stroked-button` (obrysowany) w pasku nawigacji.

Rozwiązanie — nadpisać trzy tokeny komponentowe obok nadpisań systemowych:

```scss
--mat-button-text-label-text-color:      light-dark(#7A5A2E, #E3C9A6);
--mat-button-outlined-label-text-color:  light-dark(#7A5A2E, #E3C9A6);
--mat-button-protected-label-text-color: light-dark(#7A5A2E, #E3C9A6);
```

Nazwa zmiennej potwierdzona w skompilowanym CSS pakietu (`fesm2022/datepicker.mjs` ustawia
`--mat-button-text-label-text-color` wewnątrz `.mat-calendar-period-button`).

**Alternatives considered**:
- *`--mat-sys-primary` = brąz `#7A5A2E`, złoto tylko przez `primary-container`* — odrzucone:
  główna akcja przestałaby być złota, co jest wprost sprzeczne z FR-022 i z zamysłem palety
  („złoto = ranga"). Przycisk główny musi być złoty.
- *Rezygnacja z przycisków tekstowych/obrysowanych na rzecz samych wypełnionych* — odrzucone:
  łamie FR-022 (co najwyżej jedna akcja akcentowa na ekran) i zmieniałoby układ ekranów, co
  specyfikacja stawia poza zakresem.

---

## R4. Skąd wziąć pełne palety tonalne M3

**Decision**: Wygenerować palety schematem `ng generate @angular/material:theme-color`,
zasianym kolorami marki, a następnie **przypiąć role widoczne dla użytkownika** dokładnymi
wartościami marki przez `$overrides`.

**Rationale**: M3 wymaga sześciu palet po kilkanaście stopni tonalnych
(primary, secondary, tertiary, neutral, neutral-variant, error). Schemat `m3Theme`
(alias `theme-color`, `schematics/collection.json`) przyjmuje dokładnie te ziarna
(`primaryColor`, `secondaryColor`, `tertiaryColor`, `neutralColor`, `neutralVariantColor`,
`errorColor`, `directory`, `isScss`) i generuje je algorytmicznie.

Sam schemat jednak **nie wystarcza**: algorytm tonalny M3 przelicza ziarno i zwraca kolory
pochodne, a nie zasiane. `primary` w motywie jasnym to ton 40 palety, nie `#CBAD89`. Gdybyśmy
poprzestali na schemacie, realnie renderowane kolory rozjechałyby się z zatwierdzoną paletą,
a zweryfikowane wartości kontrastu z `design/brand/README.md` przestałyby obowiązywać — czyli
naruszenie FR-001 i unieważnienie SC-002.

Stąd podział pracy: **schemat daje strukturę i harmonijne tony pochodne** (stany hover, warstwy
kontenerów, obwódki, których paleta marki nie definiuje), a **`$overrides` przypina role, które
użytkownik faktycznie widzi** do dokładnych wartości z `_pu-tokens.scss`.

**Alternatives considered**:
- *Ręczne napisanie wszystkich palet tonalnych* — odrzucone: ~78 wartości pisanych ręcznie,
  bez wartości dodanej, wysokie ryzyko literówki.
- *Same `$overrides` bez schematu* — odrzucone: role, których nie nadpiszemy, pozostałyby
  w domyślnym fiolecie Angulara, wprost łamiąc FR-001.

---

## R5. Gdzie ma żyć audyt kontrastu (FR-018)

**Decision**: Audyt kontrastu i test parzystości ról działają w **Vitest**, nad wspólnym
źródłem prawdy w TypeScript. Do Playwrighta trafiają wyłącznie sprawdzenia wymagające
prawdziwej przeglądarki.

**Rationale**: To jest ustalenie o największym wpływie na to, czy FR-018 będzie realnie
egzekwowane. Zadanie `frontend-e2e` w `.github/workflows/ci.yml:87` jest **wyłączone**
(`if: false`, l. 96) — czeka na backend z Postgresem i LocalStackiem. Audyt kontrastu
umieszczony w Playwrighcie **nigdy nie uruchomiłby się w CI**, a wymaganie byłoby spełnione
tylko pozornie. Zadanie `frontend-unit` (l. 62) działa i uruchamia `npm test` → Vitest.

Kontrast to czysta funkcja wartości kolorów, więc nie potrzebuje przeglądarki. Problem w tym,
że po kompilacji wartości żyją w CSS jako `light-dark(a, b)` i jsdom ich nie rozwiąże. Dlatego:

- **Źródło prawdy**: `frontend/src/styles/brand-tokens.ts` — mapa `{ rola: { light, dark } }`.
- **Test parzystości**: parsuje `design/brand/_pu-tokens.scss` i porównuje z mapą TS; rozjazd
  wartości albo rola bez odpowiednika w drugim motywie kończą się porażką (FR-007, FR-018).
- **Audyt kontrastu**: liczy WCAG 2.1 na mapie TS dla zadeklarowanych par tekst/tło, osobno dla
  motywu jasnego i ciemnego (FR-017), z komunikatem podającym motyw, parę i uzyskany współczynnik.

**Podział sprawdzeń**:

| Sprawdzenie | Narzędzie | Uzasadnienie |
| --- | --- | --- |
| Progi kontrastu, obie palety | Vitest | czysta funkcja, bramkowane w CI już dziś |
| Parzystość ról jasny/ciemny | Vitest | czysta funkcja |
| Brak koloru spoza systemu (FR-001) | Vitest | statyczny skan źródeł komponentów |
| `ThemeService`: zapis, odczyt, degradacja | Vitest + jsdom | logika, `localStorage` dostępny |
| Atrybut `color-scheme` na `<html>` | Vitest + jsdom | manipulacja DOM |
| Brak błysku motywu (FR-027) | Playwright | wymaga prawdziwego ładowania strony |
| Przełącznik na ekranie logowania | Playwright | wymaga renderu przeglądarki |
| Realny kontrast na wyrenderowanym ekranie | Playwright | wymaga silnika CSS |
| Szerokość 320 px, brak przewijania | Playwright | wymaga layoutu |

**Alternatives considered**: *Wszystko w Playwrighcie* — odrzucone z powodu `if: false`.
*Wszystko w Vitest* — odrzucone: FR-026 i FR-027 są z definicji zjawiskami przeglądarkowymi,
jsdom ich nie odtworzy.

---

## R6. Odblokowanie bramki przeglądarkowej w CI bez backendu

**Decision**: Dodać do `.github/workflows/ci.yml` **nowe** zadanie `frontend-e2e-theme`,
uruchamiające wyłącznie specyfikacje motywu przeciwko zbudowanej aplikacji serwowanej
statycznie. Zadanie `frontend-e2e` pozostaje wyłączone i nietknięte.

**Rationale**: FR-008 wymaga przełącznika **także przed zalogowaniem**. To oznacza, że ekran
logowania, przełącznik, zapamiętywanie preferencji i brak błysku motywu dają się przetestować
**bez żadnego backendu** — wystarczy `ng build` i statyczny serwer. Nie ma powodu, by
sprawdzenia przeglądarkowe tego feature'u czekały na wyprowadzenie Postgresa i LocalStacka
w CI. Osobne zadanie realizuje Principle VI (bramka zdefiniowana w kodzie) bez ruszania
cudzego, zablokowanego zadania.

**Alternatives considered**:
- *Włączyć istniejące `frontend-e2e`* — odrzucone: uruchomiłoby specyfikacje US1–US3
  wymagające backendu i zadanie natychmiast by czerwieniło.
- *Zostawić sprawdzenia przeglądarkowe poza CI* — odrzucone: FR-026 i FR-027 byłyby
  niebramkowane, czyli w praktyce niesprawdzane.

---

## R7. Przechowywanie preferencji motywu

**Decision**: `localStorage`, klucz `pu.theme`, wartości `light` | `dark`. Brak wpisu =
podążanie za systemem operacyjnym. Każdy odczyt i zapis w `try/catch`.

**Rationale**: Specyfikacja rozstrzyga przechowywanie na urządzeniu (Clarifications, FR-010),
a `localStorage` jest jedynym trwałym magazynem po stronie klienta niezwiązanym z sesją —
co jest istotne, bo FR-011 wymaga, by wybór przetrwał wylogowanie. `sessionStorage` odpada
z definicji, cookie niepotrzebnie trafiałoby do każdego żądania HTTP i zbliżałoby feature do
obszaru sesji, którego świadomie unikamy (zob. bramki w plan.md).

`try/catch` nie jest ostrożnościowy — w trybie prywatnym i przy zablokowanych danych witryny
sam **dostęp** do `localStorage` potrafi rzucić wyjątkiem, nie tylko zapis. FR-014 wymaga, by
aplikacja działała wtedy dalej bez komunikatu błędu.

Nieznana lub uszkodzona wartość jest traktowana jak brak wpisu (powrót do podążania za
systemem) — realizuje przypadek brzegowy „uszkodzona preferencja".

**Alternatives considered**: *Cookie* — odrzucone (ruch sieciowy, bliskość obszaru sesji).
*IndexedDB* — odrzucone (asynchroniczne, co koliduje z R8 i skrypt startowy nie mógłby go
odczytać synchronicznie).

---

## R8. Zapobieganie błyskowi motywu (FR-027)

**Decision**: Synchroniczny skrypt inline w `<head>` pliku `index.html`, ustawiający
`color-scheme` na `<html>` **przed** pierwszym malowaniem. Angular później tylko przejmuje już
ustawioną wartość.

**Rationale**: Bootstrap Angulara następuje po pobraniu i wykonaniu bundla. Gdyby motyw
ustawiał dopiero `ThemeService`, użytkownik z zapamiętanym motywem ciemnym zobaczyłby przy
każdym wejściu błysk jasnego tła — dokładnie to, czego zabrania FR-027. Jedyne miejsce
działające przed malowaniem to skrypt synchroniczny w `<head>`.

Skrypt musi być mikroskopijny, bez zależności i odporny na wyjątek z `localStorage`:

```html
<script>
  try {
    var t = localStorage.getItem('pu.theme');
    document.documentElement.style.colorScheme =
      t === 'light' || t === 'dark' ? t : 'light dark';
  } catch (e) {
    document.documentElement.style.colorScheme = 'light dark';
  }
</script>
```

Dodatkowo `index.html` dostaje `<meta name="color-scheme" content="light dark">`, żeby
przeglądarka znała obsługiwane schematy jeszcze przed CSS i nie malowała białego tła pod
ciemnym motywem.

**Alternatives considered**:
- *Ustawianie w `APP_INITIALIZER`* — odrzucone: nadal po pobraniu bundla, błysk pozostaje.
- *Renderowanie po stronie serwera* — odrzucone: projekt nie ma SSR, a dokładanie go dla
  jednego wymagania jest nieproporcjonalne.

---

## R9. Twarde kolory w istniejących komponentach

**Decision**: Usunąć wszystkie literalne wartości kolorów z dwunastu komponentów; schemat
uzębienia dostaje własne role semantyczne w systemie marki.

**Rationale**: Skan źródeł wykazał dwie kategorie naruszeń FR-001:

1. **Fallbacki przy tokenach systemowych** — `var(--mat-sys-error, #b3261e)` w pięciu
   komponentach oraz `var(--mat-sys-surface-container, #f2f2f2)` w `mfa-challenge`. Wartości
   fallbacku to domyślne kolory M3, nie kolory marki; wystarczy usunąć drugi argument, bo po
   wdrożeniu motywu token zawsze jest zdefiniowany.
2. **Twarde kolory w schemacie uzębienia** (`tooth-chart.component.ts:63-72`): `fill: #ffffff`,
   `stroke: #666`, `fill: #f3b0ae` (ząb chory), `stroke: #1a73e8` (zaznaczenie). To najpoważniejszy
   przypadek: trzy z nich są nośnikami znaczenia klinicznego, a `#1a73e8` to obcy marce błękit.

Schemat uzębienia dostaje cztery nowe role w systemie:
`--pu-tooth-healthy-fill`, `--pu-tooth-healthy-stroke`, `--pu-tooth-diseased-fill`,
`--pu-tooth-selected-stroke` — każda jako para `light-dark()`, każda objęta audytem kontrastu.
Zaznaczenie przechodzi z błękitu na złoto marki, co jest zgodne z FR-001 i z rolą „akcent
oznacza to, co ważne".

FR-019 (podwójne kodowanie) wymaga przy tym, by rozróżnienie zdrowy/chory nie opierało się
wyłącznie na wypełnieniu — potrzebny jest drugi sygnał (wzór, obrys lub etykieta). Konkretną
formę wybiera implementacja; audyt sprawdza samo istnienie drugiego sygnału.

**Alternatives considered**: *Zostawić fallbacki jako zabezpieczenie* — odrzucone: fallback
z definicji jest kolorem spoza systemu, a po wdrożeniu motywu nigdy się nie uaktywni; to
martwy kod, który psuje statyczny skan FR-001.

---

## R10. Ryzyko rozjazdu dwóch kart (przypadek brzegowy)

**Decision**: `ThemeService` nasłuchuje zdarzenia `storage` i stosuje zmianę w pozostałych
kartach tego samego urządzenia.

**Rationale**: Przypadek brzegowy ze specyfikacji („dwie karty na jednym urządzeniu") ma
gotowe, jednolinijkowe rozwiązanie: przeglądarka emituje `storage` we wszystkich innych kartach
tego samego pochodzenia. Bez tego karty rozjeżdżają się trwale aż do przeładowania. Zdarzenie
nie jest emitowane w karcie, która dokonała zapisu, więc nie powstaje pętla.

**Alternatives considered**: *`BroadcastChannel`* — odrzucone: dokłada API, a `storage` i tak
jest potrzebne; korzyści żadnej, bo źródłem prawdy jest `localStorage`.
