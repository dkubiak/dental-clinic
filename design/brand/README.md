# Projekt Uśmiech — system kolorystyczny (v1)

Wizualny przewodnik: <https://claude.ai/code/artifact/d7def842-faea-490c-bd79-1d7f07e74304>

Tokeny do wdrożenia: [`_pu-tokens.scss`](./_pu-tokens.scss)

## Skąd te kolory

Nie są dobrane na oko — trzy wartości bazowe odczytano wprost z pliku logo:

| Wartość   | Pochodzenie                                   |
| --------- | --------------------------------------------- |
| `#F2D5B3` | najjaśniejszy refleks na złotej linii          |
| `#CBAD89` | mediana wszystkich złotych pikseli — kolor marki |
| `#2E2C2D` | dominanta tła — grafit z ciepłym podbiciem     |

Reszta palety to rozwinięcie tych trzech punktów, dzięki czemu strona, materiały
drukowane i aplikacja gabinetu nie rozjadą się kolorystycznie.

## Kierunek

- **Złoto = ranga.** Rzadko i zawsze znaczące: podpis marki, akcja główna,
  potwierdzony status. Poniżej ~10% powierzchni ekranu.
- **Grafit = spokój.** Ciepły grafit zamiast czerni i zamiast bieli szpitalnej.
- **Eukaliptus = higiena.** Chłodny kontrapunkt, ale nie stomatologiczny błękit.

Docelowy rozkład powierzchni: ~58% grafit/papier, ~30% neutrale, ~9% złoto,
~3% eukaliptus.

## Zasada nadrzędna

**Złoto nie jest kolorem tekstu na jasnym tle.** `#CBAD89` na papierze daje
kontrast **1.99:1**, przy progu WCAG AA równym 4.5:1. Rozwiązanie:

- na jasnym tle złoto występuje wyłącznie jako *płaszczyzna* (tło przycisku,
  linia, kreska boczna karty) z napisem `#1F1D1E` — 7.88:1;
- „złoty" tekst i linki na jasnym tle to Bronze 800 `#7A5A2E` — 5.90:1;
- na graficie złoto może być tekstem — Gold 300 `#E3C9A6` daje 11.08:1.

## Dwa konteksty

| Kontekst                        | Tło       | Tekst akcentowy      |
| ------------------------------- | --------- | -------------------- |
| Strona i materiały pacjenta     | `#2E2C2D` | Gold 300 `#E3C9A6`   |
| Aplikacja gabinetu              | `#FAF7F2` | Bronze 800 `#7A5A2E` |

## Dostępność

Wszystkie pary tekst/tło w `_pu-tokens.scss` zweryfikowano pod WCAG 2.1 AA
(ratio ≥ 4.5:1 dla tekstu podstawowego); wyliczone wartości podano w komentarzach
przy każdym tokenie. Kolory funkcyjne mają osobne warianty dla jasnego i ciemnego
motywu, a ostrzeżenie (`#9A5B00`) jest celowo bardziej pomarańczowe niż złoto marki,
żeby nie myliło się z akcentem dekoracyjnym.

## Role dodane podczas wdrożenia (feature 003)

Siedem ról, których nie było w wersji v1 tego dokumentu, dopisanych do `_pu-tokens.scss` w
`specs/003-brand-ui-theme` — audyt kontrastu i schemat uzębienia ich potrzebowały:

| Rola | Jasny | Ciemny | Po co |
| --- | --- | --- | --- |
| `border-strong` | `#7D746F` | `#8C8480` | obrys **kontrolki** (WCAG 1.4.11) — `border` sam nie starcza, bo jest separatorem dekoracyjnym (1.24:1 / 2.08:1, celowo poza audytem) |
| `focus-ring` | `#7A5A2E` | `#E3C9A6` | pierścień fokusu na tle/powierzchni |
| `focus-ring-on-accent` | `#4E3A1F` | `#1A1819` | pierścień fokusu **na płaszczyźnie akcentu** — zwykły `focus-ring` na `accent` daje 2.97:1, poniżej progu 3:1 |
| `tooth-healthy-fill` | `#FFFFFF` | `#363233` | wypełnienie zdrowego zęba |
| `tooth-healthy-stroke` | `#5C5654` | `#A79E96` | obrys zdrowego zęba |
| `tooth-diseased-fill` | `#F7E3E1` | `#5A2B27` | wypełnienie chorego zęba |
| `tooth-diseased-stroke` | `#A33A32` | `#E88178` | obrys chorego zęba |
| `tooth-selected-stroke` | `#7A5A2E` | `#E3C9A6` | zaznaczenie — zastępuje obcy marce błękit `#1a73e8` |

Wypełnienia zdrowy/chory dają wobec siebie zaledwie 1.23:1 (jasny) i 1.09:1 (ciemny) — same nie
niosą znaczenia klinicznego, dlatego stan chory ma na schemacie uzębienia drugi sygnał
niezależny od koloru (wzór kreski `stroke-dasharray`). Pełne wartości i uzasadnienie progów:
[`../../specs/003-brand-ui-theme/contracts/design-tokens.md`](../../specs/003-brand-ui-theme/contracts/design-tokens.md).

## Pułapka `--mat-sys-primary`

Angular Material czyta `--mat-sys-primary` jako źródło TŁA przycisku wypełnionego (poprawne
użycie akcentu jako płaszczyzny) i jednocześnie jako domyślne źródło koloru ETYKIETY przycisku
tekstowego i obrysowanego. Dla tej palety to pułapka: złoto jako tekst na papierze daje
**1.99:1**, poniżej progu WCAG AA 4.5:1. Material nie rozróżnia tych dwóch użyć samodzielnie —
`frontend/src/styles/_pu-theme.scss` dlatego nadpisuje `--mat-button-text-label-text-color`,
`--mat-button-outlined-label-text-color` i `--mat-button-protected-label-text-color` wprost, jako
zwykłe deklaracje CSS osobne od `$overrides` przekazywanych do `mat.theme()`. Pomiń to nadpisanie,
a złoto stanie się nieczytelną etykietą przycisku. Szczegóły: `research.md` R3 i
`contracts/design-tokens.md` §3 w `specs/003-brand-ui-theme`.

## Status wdrożenia

Zaimplementowane. `frontend/src/styles.scss` podpina paletę pod `mat.theme()` z paletami
tonalnymi z `frontend/src/styles/_pu-palettes.scss` i nadpisaniami z
`frontend/src/styles/_pu-theme.scss`. Ten plik (`_pu-tokens.scss`) pozostaje dokumentem
źródłowym — wartości i uzasadnienie kontrastu w komentarzach; `frontend/src/styles/brand-tokens.ts`
jest jego maszynowo czytelnym lustrem, z którym zgodność pilnuje
`frontend/src/styles/token-parity.spec.ts` (bramkowany w CI, zadanie `frontend-unit`).
