# Phase 1 Data Model: Branding i motyw UI (Projekt Uśmiech)

**Feature**: `003-brand-ui-theme` | **Date**: 2026-08-27

Feature nie dotyka bazy danych ani backendu. Model danych obejmuje jedną encję po stronie
klienta oraz strukturę tokenów, która jest źródłem prawdy dla testów.

---

## Encja: Preferencja motywu (na urządzeniu)

Zapamiętany wybór użytkownika między motywem jasnym a ciemnym.

| Atrybut | Wartość |
| --- | --- |
| Nośnik | `localStorage` przeglądarki (per urządzenie, per pochodzenie) |
| Klucz | `pu.theme` |
| Dopuszczalne wartości | `"light"`, `"dark"` |
| Brak wpisu | stan prawidłowy — oznacza „podążaj za systemem operacyjnym" |
| Zasięg | wspólna dla wszystkich osób korzystających z urządzenia |
| Powiązanie z kontem | **brak** — nie jest polem profilu ani częścią sesji (FR-010) |
| Cykl życia | przetrwa wylogowanie (FR-011); ginie z wyczyszczeniem danych witryny |
| Dane osobowe | **nie** — nie podlega RODO ani logowaniu audytowemu (Principle III) |

### Stany i przejścia

```text
                    zapis "light"
      ┌──────────────────────────────────────┐
      │                                      ▼
┌─────┴──────┐   zapis "dark"          ┌───────────┐
│  SYSTEM    │ ──────────────────────► │   LIGHT   │
│ (brak      │                         │ color-    │
│  wpisu)    │ ◄────────────────────── │ scheme:   │
│ color-     │   wyczyszczenie danych  │ light     │
│ scheme:    │        witryny          └───────────┘
│ light dark │                               ▲
└─────┬──────┘                               │ przełączenie
      │                                      ▼
      │        zapis "dark"           ┌───────────┐
      └──────────────────────────────►│   DARK    │
                                      │ color-    │
                                      │ scheme:   │
                                      │ dark      │
                                      └───────────┘
```

### Reguły walidacji

| Reguła | Zachowanie | Wymaganie |
| --- | --- | --- |
| Wartość spoza `{light, dark}` | traktowana jak brak wpisu → stan SYSTEM | FR-014, przypadek brzegowy „uszkodzona preferencja" |
| Odczyt rzuca wyjątkiem (tryb prywatny) | stan SYSTEM, aplikacja działa dalej, brak komunikatu błędu | FR-014 |
| Zapis rzuca wyjątkiem | przełączenie działa w obrębie sesji, brak komunikatu błędu | FR-014, US2 scenariusz 6 |
| Zmiana ustawienia systemu operacyjnego przy stanie LIGHT/DARK | ignorowana — jawny wybór wygrywa | FR-013, US2 scenariusz 5 |
| Zmiana ustawienia systemu operacyjnego przy stanie SYSTEM | stosowana natychmiast | FR-012 |
| Zdarzenie `storage` z innej karty | stan stosowany w tej karcie | przypadek brzegowy „dwie karty" |
| Wylogowanie | wpis nietknięty | FR-011 |

---

## Struktura: Tokeny marki (źródło prawdy dla testów)

Nie jest to encja trwała, lecz struktura danych, nad którą pracuje audyt kontrastu (FR-018).
Żyje w `frontend/src/styles/brand-tokens.ts` i jest lustrzana wobec
`design/brand/_pu-tokens.scss` — rozjazd między nimi wykrywa test parzystości.

```text
BrandTokens = Record<RoleName, { light: HexColor; dark: HexColor }>
ContrastPair = { foreground: RoleName; background: RoleName; minRatio: 4.5 | 3.0 }
```

### Role semantyczne

Każda rola MUSI mieć wartość w obu motywach (FR-007). Brak odpowiednika = porażka testu.

| Grupa | Role |
| --- | --- |
| Powierzchnie | `bg`, `surface`, `surface-raised`, `border` |
| Tekst | `text`, `text-muted`, `text-disabled` |
| Akcent | `accent` (płaszczyzna), `accent-text` (tekst), `on-accent`, `accent-hover` |
| Kontrapunkt | `euc`, `euc-text`, `on-euc` |
| Funkcyjne | `success`, `warning`, `error`, `info` + `on-*` dla każdego |
| Fokus | `focus-ring` |
| Schemat uzębienia | `tooth-healthy-fill`, `tooth-healthy-stroke`, `tooth-diseased-fill`, `tooth-selected-stroke` |

### Reguły walidacji tokenów

| Reguła | Wymaganie |
| --- | --- |
| Każda rola ma wartość w `light` i w `dark` | FR-007 |
| Każda para z listy `ContrastPair` osiąga swój próg, osobno w obu motywach | FR-017 |
| Tekst podstawowy: próg 4.5:1; duży tekst i elementy UI: próg 3.0:1 | FR-017 |
| `accent` **nie występuje** jako `foreground` nad jasnym tłem | FR-004 |
| `accent` **wolno** stosować jako `foreground` nad ciemnym tłem | FR-004 |
| `focus-ring` osiąga ≥3:1 wobec każdego tła, na którym może leżeć | FR-020 |
| Wartości w `.ts` są identyczne z wartościami w `_pu-tokens.scss` | FR-002 (jedno źródło) |

---

## Co NIE powstaje

Zapisane wprost, bo to jest granica utrzymująca feature poza bramką przeglądu bezpieczeństwa
(zob. plan.md, Constitution Check):

- brak tabeli, kolumny i migracji w bazie danych,
- brak endpointu API i zmiany w kontraktach backendu,
- brak pola w modelu konta, profilu i w sesji,
- brak wpisu w logu audytowym dla zmiany motywu.
