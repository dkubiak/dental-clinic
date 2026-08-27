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

## Status wdrożenia

Ten katalog zawiera wyłącznie propozycję i gotowe tokeny. `frontend/src/styles.scss`
nie został zmieniony — podpięcie palety pod `mat.theme()` to zmiana funkcjonalna,
która zgodnie z `CLAUDE.md` powinna przejść przez pipeline `/speckit-*`
(por. notatka przy T049 w `specs/002-patient-records/tasks.md`, gdzie brandowanie
było świadomie odroczone).
