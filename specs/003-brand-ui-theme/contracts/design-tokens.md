# Contract: Tokeny systemu kolorystycznego

**Feature**: `003-brand-ui-theme`

Wszystkie współczynniki kontrastu w tym dokumencie zostały wyliczone (WCAG 2.1, sRGB), a nie
oszacowane. Są to wartości, które ma odtworzyć audyt z `contrast-audit.spec.ts` — jeśli
implementacja da inny wynik, to implementacja jest błędna, nie tabela.

---

## 1. Role semantyczne

Każda rola MUSI mieć wartość w obu motywach (FR-007). Zapis w SCSS zawsze jako
`light-dark(#jasny, #ciemny)` — zob. research.md R2.

### Powierzchnie

| Rola | Jasny | Ciemny | Zastosowanie |
| --- | --- | --- | --- |
| `bg` | `#FAF7F2` | `#1A1819` | tło strony |
| `surface` | `#FFFFFF` | `#2E2C2D` | karta, panel, pole |
| `surface-raised` | `#F2EDE6` | `#363233` | warstwa uniesiona, hover wiersza |
| `border` | `#E6DFD5` | `#504C4B` | separator **dekoracyjny** |
| `border-strong` | `#7D746F` | `#8C8480` | obrys **kontrolki** (pole, checkbox) |

Rozdział `border` / `border-strong` jest celowy. WCAG 1.4.11 wymaga 3:1 tylko dla granic
niezbędnych do rozpoznania kontrolki; separator dekoracyjny nie jest nośnikiem informacji.
`border` osiąga 1.24:1 (jasny) i 2.08:1 (ciemny) i **jest wyłączony** z listy par audytu.
Użycie `border` na obrysie pola formularza jest błędem — do tego służy `border-strong`.

### Tekst

| Rola | Jasny | Ciemny | Zastosowanie |
| --- | --- | --- | --- |
| `text` | `#1F1D1E` | `#EAE4DC` | treść główna |
| `text-muted` | `#5C5654` | `#A79E96` | treść drugorzędna, etykiety pól |
| `text-disabled` | `#8C8480` | `#7A726C` | element nieaktywny, placeholder |

### Akcent marki

| Rola | Jasny | Ciemny | Zastosowanie |
| --- | --- | --- | --- |
| `accent` | `#CBAD89` | `#CBAD89` | **wyłącznie płaszczyzna** — tło przycisku, linia, kreska |
| `accent-text` | `#7A5A2E` | `#E3C9A6` | tekst i linki „złote" |
| `accent-hover` | `#B2946E` | `#E3C9A6` | stan hover akcji głównej |
| `on-accent` | `#1F1D1E` | `#1A1819` | treść **na** płaszczyźnie akcentu |

`accent` ma tę samą wartość w obu motywach i jest to decyzja, nie przeoczenie: złoto marki jest
stałą identyfikacji, a czytelność zapewnia `on-accent`, który się zmienia. Rozdział
`accent` / `accent-text` jest bezpośrednią realizacją FR-004.

### Kontrapunkt i kolory funkcyjne

| Rola | Jasny | Ciemny | Rola | Jasny | Ciemny |
| --- | --- | --- | --- | --- | --- |
| `euc` | `#3E7A72` | `#7FB3AA` | `success` | `#2E6B45` | `#6FBF8E` |
| `euc-text` | `#2F5D57` | `#7FB3AA` | `warning` | `#9A5B00` | `#E8A33D` |
| `on-euc` | `#FAF7F2` | `#1A1819` | `error` | `#A33A32` | `#E88178` |
| | | | `info` | `#3B5A7A` | `#8FB2D6` |

`on-success`, `on-warning`, `on-error`, `on-info`: `#FAF7F2` (jasny) / `#1A1819` (ciemny).

### Fokus

| Rola | Jasny | Ciemny | Zastosowanie |
| --- | --- | --- | --- |
| `focus-ring` | `#7A5A2E` | `#E3C9A6` | wskaźnik fokusu na tle i powierzchni |
| `focus-ring-on-accent` | `#4E3A1F` | `#1A1819` | wskaźnik fokusu **na płaszczyźnie akcentu** |

Osobna rola dla fokusu na złocie jest konieczna, nie ozdobna: `focus-ring` na `accent` daje
**2.97:1**, czyli o włos poniżej progu 3:1 z FR-020. `focus-ring-on-accent` daje 5.07:1.

### Schemat uzębienia

| Rola | Jasny | Ciemny | Zastępuje |
| --- | --- | --- | --- |
| `tooth-healthy-fill` | `#FFFFFF` | `#363233` | `fill: #ffffff` |
| `tooth-healthy-stroke` | `#5C5654` | `#A79E96` | `stroke: #666` |
| `tooth-diseased-fill` | `#F7E3E1` | `#5A2B27` | `fill: #f3b0ae` |
| `tooth-diseased-stroke` | `#A33A32` | `#E88178` | — (nowa) |
| `tooth-selected-stroke` | `#7A5A2E` | `#E3C9A6` | `stroke: #1a73e8` |

Zaznaczenie przechodzi z obcego marce błękitu `#1a73e8` na brąz/złoto.

---

## 2. Pary kontrastu podlegające audytowi

Lista, którą konsumuje `contrast-audit.spec.ts`. Kolumny „Jasny" i „Ciemny" to wartości
wyliczone — audyt MUSI je odtworzyć.

### Próg 4.5:1 — tekst podstawowy

| Tekst | Tło | Jasny | Ciemny |
| --- | --- | --- | --- |
| `text` | `bg` | 15.69 | 13.99 |
| `text` | `surface` | 16.61 | 10.98 |
| `text-muted` | `bg` | 6.74 | 6.71 |
| `text-muted` | `surface` | 7.21 | 8.24 |
| `accent-text` | `bg` | 5.90 | 11.08 |
| `accent-text` | `surface` | 6.31 | 8.70 |
| `on-accent` | `accent` | 7.88 | 8.31 |
| `euc-text` | `bg` | 6.96 | 7.51 |
| `success` | `bg` | 5.95 | 8.01 |
| `warning` | `bg` | 5.08 | 8.19 |
| `error` | `bg` | 6.12 | 6.60 |
| `info` | `bg` | 6.71 | 8.00 |

### Próg 3:1 — elementy interfejsu, duży tekst, obrysy

| Element | Tło | Jasny | Ciemny |
| --- | --- | --- | --- |
| `border-strong` | `bg` | 4.27 | 4.82 |
| `border-strong` | `surface` | 4.57 | 3.78 |
| `focus-ring` | `bg` | 5.90 | 11.08 |
| `focus-ring` | `surface` | 6.31 | 8.70 |
| `focus-ring-on-accent` | `accent` | 5.07 | 8.31 |
| `text-disabled` | `bg` | 3.43 | 3.74 |
| `tooth-healthy-stroke` | `tooth-healthy-fill` | 7.21 | 4.80 |
| `tooth-diseased-stroke` | `tooth-diseased-fill` | 5.31 | 4.33 |
| `tooth-selected-stroke` | `tooth-healthy-fill` | 6.31 | 7.93 |

### Pary celowo NIEobjęte audytem

| Para | Powód |
| --- | --- |
| `accent` jako tekst nad `bg`/`surface` (jasny) | 1.99:1 — zakazana przez FR-004; audyt ma **odrzucić** taką parę, gdyby ktoś ją dopisał (asercja A4) |
| `border` wobec `bg` | separator dekoracyjny, nie granica kontrolki (WCAG 1.4.11) |
| `tooth-*-fill` wobec `bg` | wypełnienie nie jest granicą wymagającą kontrastu |
| `tooth-healthy-fill` wobec `tooth-diseased-fill` | zob. niżej |

**Rozróżnienie stanu zęba nie może opierać się na kolorze.** Wypełnienia zdrowy/chory dają
**1.23:1** (jasny) i **1.09:1** (ciemny) względem siebie. To nie jest usterka palety do
naprawienia dobraniem odcieni — dwa wypełnienia o wysokim wzajemnym kontraście musiałyby
zerwać spójność z resztą schematu. FR-019 wymaga drugiego sygnału niezależnego od koloru
(wzór, grubość obrysu albo etykieta) i te liczby pokazują, że jest on nośnikiem znaczenia,
a nie dodatkiem. Audyt sprawdza obecność drugiego sygnału, nie kontrast wypełnień.

---

## 3. Mapowanie na zmienne Angular Material

Nadpisania przekazywane jako `$overrides` do `mat.theme()`. Każda wartość w formie
`light-dark()` — research.md R2.

| Zmienna Material | Wartość | Uwaga |
| --- | --- | --- |
| `--mat-sys-primary` | `light-dark(#CBAD89, #CBAD89)` | rola płaszczyzny |
| `--mat-sys-on-primary` | `light-dark(#1F1D1E, #1A1819)` | etykieta przycisku wypełnionego |
| `--mat-sys-surface` | `light-dark(#FFFFFF, #2E2C2D)` | |
| `--mat-sys-surface-container` | `light-dark(#F2EDE6, #363233)` | |
| `--mat-sys-background` | `light-dark(#FAF7F2, #1A1819)` | |
| `--mat-sys-on-surface` | `light-dark(#1F1D1E, #EAE4DC)` | |
| `--mat-sys-on-surface-variant` | `light-dark(#5C5654, #A79E96)` | |
| `--mat-sys-outline` | `light-dark(#7D746F, #8C8480)` | mapuje na `border-strong` |
| `--mat-sys-outline-variant` | `light-dark(#E6DFD5, #504C4B)` | mapuje na `border` |
| `--mat-sys-error` | `light-dark(#A33A32, #E88178)` | |
| `--mat-sys-on-error` | `light-dark(#FAF7F2, #1A1819)` | |
| `--mat-sys-tertiary` | `light-dark(#3E7A72, #7FB3AA)` | eukaliptus |

### Nadpisania komponentowe — obowiązkowe

Bez nich `--mat-sys-primary` ustawiłby złoto jako **kolor etykiety** przycisków tekstowych
i obrysowanych, dając 1.99:1 na jasnym tle. Powód i weryfikacja: research.md R3.

| Zmienna | Wartość |
| --- | --- |
| `--mat-button-text-label-text-color` | `light-dark(#7A5A2E, #E3C9A6)` |
| `--mat-button-outlined-label-text-color` | `light-dark(#7A5A2E, #E3C9A6)` |
| `--mat-button-protected-label-text-color` | `light-dark(#7A5A2E, #E3C9A6)` |

---

## 4. Zakazane konstrukcje

Wykrywane przez `no-literal-colors.spec.ts` (asercja A6).

| Konstrukcja | Powód |
| --- | --- |
| Literalna wartość koloru w pliku komponentu (`#rgb`, `rgb()`, `hsl()`, nazwa CSS) | FR-001 |
| `var(--mat-sys-*, #fallback)` z literalnym fallbackiem | FR-001 — fallback jest kolorem spoza systemu |
| Zwykła wartość (bez `light-dark()`) w `$overrides` | R2 — zabija zmienność motywu |
| `accent` jako `color` tekstu w motywie jasnym | FR-004 |
| Klasa CSS albo atrybut `data-*` jako nośnik stanu motywu | FR-026 — jedynym nośnikiem jest `color-scheme` |
