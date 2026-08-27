# Contract: Przełącznik i preferencja motywu

**Feature**: `003-brand-ui-theme`

Feature nie wystawia API HTTP. Kontraktami są tu: powierzchnia DOM, magazyn przeglądarki
i publiczny interfejs usługi Angulara. Wszystkie trzy są obserwowalne z testów.

---

## 1. Kontrakt DOM

Jedyny nośnik stanu motywu w dokumencie. Testy przeglądarkowe asertują wyłącznie na nim.

| Element | Właściwość | Wartości | Znaczenie |
| --- | --- | --- | --- |
| `<html>` | `style.color-scheme` | `light dark` | podąża za systemem operacyjnym (stan SYSTEM) |
| `<html>` | `style.color-scheme` | `light` | wymuszony motyw jasny |
| `<html>` | `style.color-scheme` | `dark` | wymuszony motyw ciemny |

**Niezmienniki**:

- `color-scheme` jest ustawiony **przed pierwszym malowaniem** — gwarantuje to skrypt inline
  w `<head>` (FR-027).
- Motyw NIE jest wyrażony klasą CSS ani atrybutem `data-*`. Jedno źródło stanu w DOM oznacza,
  że nie da się doprowadzić do rozjazdu części interfejsu (FR-026).
- `index.html` deklaruje `<meta name="color-scheme" content="light dark">`.

### Przełącznik

| Cecha | Wymóg | Wymaganie |
| --- | --- | --- |
| Selektor testowy | `[data-testid="theme-toggle"]` | — |
| Obecność | na każdym ekranie, również przed zalogowaniem | FR-008 |
| Rola dostępności | kontrolka przełączająca z dostępną nazwą | FR-015 |
| Nazwa dostępna | komunikuje stan bieżący i skutek uruchomienia | FR-015 |
| Fokus klawiaturowy | osiągalny, widoczny wskaźnik ≥3:1 | FR-015, FR-020 |
| Osiągalność przy 320 px | bez poziomego przewijania, maks. jeden poziom zagnieżdżenia | FR-016 |

---

## 2. Kontrakt magazynu

| Właściwość | Wartość |
| --- | --- |
| Magazyn | `localStorage` |
| Klucz | `pu.theme` |
| Wartości | `"light"` \| `"dark"` |
| Brak klucza | stan SYSTEM (prawidłowy, nie błąd) |
| Wartość nieznana | traktowana jak brak klucza |
| Wyjątek przy odczycie lub zapisie | przechwycony; stan SYSTEM; brak komunikatu dla użytkownika |
| Czyszczenie przy wylogowaniu | **zabronione** (FR-011) |

**Propagacja między kartami**: nasłuch zdarzenia `storage` na kluczu `pu.theme` stosuje nową
wartość w pozostałych kartach tego samego pochodzenia.

---

## 3. Kontrakt usługi

```ts
type ThemeChoice = 'light' | 'dark' | 'system';

interface ThemeService {
  /** Bieżący wybór. 'system' oznacza brak zapisanej preferencji. */
  readonly choice: Signal<ThemeChoice>;

  /** Motyw faktycznie renderowany po rozstrzygnięciu 'system'. */
  readonly resolved: Signal<'light' | 'dark'>;

  /** Ustawia wybór, zapisuje go i stosuje do DOM. Nigdy nie rzuca. */
  set(choice: ThemeChoice): void;

  /** Przełącza jasny ↔ ciemny na podstawie aktualnie renderowanego motywu. */
  toggle(): void;
}
```

**Niezmienniki**:

| Niezmiennik | Wymaganie |
| --- | --- |
| `set()` i `toggle()` nigdy nie rzucają wyjątkiem, także gdy magazyn jest niedostępny | FR-014 |
| `set()` stosuje zmianę do DOM synchronicznie, bez przeładowania strony | FR-009 |
| Zmiana motywu nie modyfikuje stanu routera ani formularzy | FR-009 |
| `resolved` śledzi `prefers-color-scheme` **tylko** gdy `choice === 'system'` | FR-012, FR-013 |
| Usługa nie zależy od `AuthService`, sesji ani modelu konta | granica bramki (plan.md) |

---

## 4. Kontrakt audytu (testowy)

Kontrakt między definicją tokenów a zestawem testów. Naruszenie = porażka testu, nie ostrzeżenie.

```ts
type HexColor = `#${string}`;
type ThemeName = 'light' | 'dark';

type BrandTokens = Record<string, Record<ThemeName, HexColor>>;

interface ContrastPair {
  foreground: string;   // nazwa roli
  background: string;   // nazwa roli
  minRatio: 4.5 | 3.0;  // 4.5 = tekst podstawowy, 3.0 = duży tekst i elementy UI
  themes?: ThemeName[]; // domyślnie obie; ['dark'] dla par dozwolonych tylko w ciemnym
}
```

**Asercje, które MUSZĄ istnieć**:

| # | Asercja | Wymaganie |
| --- | --- | --- |
| A1 | Każda rola ma wartość w obu motywach | FR-007 |
| A2 | Każda para osiąga `minRatio` w każdym motywie ze swojej listy | FR-017 |
| A3 | Komunikat porażki podaje motyw, obie role, oczekiwany i uzyskany współczynnik | FR-018 |
| A4 | Para `accent` jako tekst nad jasnym tłem jest odrzucana | FR-004 |
| A5 | Wartości w `brand-tokens.ts` zgadzają się z `_pu-tokens.scss` | FR-002 |
| A6 | Żaden plik komponentu nie zawiera literalnej wartości koloru | FR-001 |

---

## 5. Kontrakt integracji z Angular Material

| Zobowiązanie | Uzasadnienie |
| --- | --- |
| `mat.theme()` wywoływany z `theme-type: color-scheme` (domyślnie) | R1 — emituje `light-dark()` |
| Każda wartość w `$overrides` zapisana jako `light-dark(#jasny, #ciemny)` | R2 — zwykła wartość zabiłaby zmienność motywu |
| `--mat-sys-primary` = złoto marki (rola płaszczyzny) | R3, FR-022 |
| `--mat-button-text-label-text-color`, `--mat-button-outlined-label-text-color`, `--mat-button-protected-label-text-color` nadpisane na wariant tekstowy | R3, FR-004 |
| Żaden komponent nie używa `var(--mat-sys-*, #fallback)` z literalnym fallbackiem | R9, FR-001 |
