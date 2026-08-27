# Specification Quality Checklist: Branding i motyw UI (Projekt Uśmiech)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-27
**Feature**: [spec.md](../spec.md)

## Content Quality

- [X] No implementation details (languages, frameworks, APIs)
- [X] Focused on user value and business needs
- [X] Written for non-technical stakeholders
- [X] All mandatory sections completed

## Requirement Completeness

- [X] No [NEEDS CLARIFICATION] markers remain
- [X] Requirements are testable and unambiguous
- [X] Success criteria are measurable
- [X] Success criteria are technology-agnostic (no implementation details)
- [X] All acceptance scenarios are defined
- [X] Edge cases are identified
- [X] Scope is clearly bounded
- [X] Dependencies and assumptions identified

## Feature Readiness

- [X] All functional requirements have clear acceptance criteria
- [X] User scenarios cover primary flows
- [X] Feature meets measurable outcomes defined in Success Criteria
- [X] No implementation details leak into specification

## Notes

### Iteracja 1 (2026-08-27) — 3 pozycje niezaliczone

Wszystkie trzy wynikały z jednego nierozstrzygniętego pytania — zakresu motywu ciemnego.
Opis feature'u mówił o "ciemnym motywie dla materiałów pacjenta", podczas gdy repozytorium
zawiera wyłącznie aplikację dla personelu: nie istniała żadna powierzchnia skierowana do
pacjenta ani przełącznik motywu.

Niezaliczone: *No [NEEDS CLARIFICATION] markers remain*, *All acceptance scenarios are
defined*, *All functional requirements have clear acceptance criteria*.

### Iteracja 2 (2026-08-27) — wszystkie pozycje zaliczone

Przyjęto rozstrzygnięcie "tylko tokeny": wariant ciemny jako definicja objęta testami
kontrastu, aplikacja personelu zawsze jasna. 5 historii, 21 wymagań, 8 kryteriów sukcesu.

### Iteracja 3 (2026-08-27) — zmiana zakresu, wszystkie pozycje nadal zaliczone

Rozstrzygnięcie z iteracji 2 zostało **wycofane** na wniosek właściciela produktu: front ma
zawsze udostępniać przełącznik jasny/ciemny. Preferencja żyje **na urządzeniu** (nie w koncie),
co utrzymuje backend poza zakresem feature'u.

To nie jest kosmetyczna zmiana US5 — motyw ciemny przestaje być definicją, a staje się
pełnoprawnym motywem aplikacji personelu, co **podwaja powierzchnię weryfikacji**: każdy ekran,
każdy komunikat, każdy stan fokusu i każdy audyt kontrastu muszą przejść w obu motywach.
Zmiany objęły całą specyfikację, nie jedną historię:

- **Nowa US2 "Przełącznik motywu dostępny zawsze"** (P2, 8 scenariuszy), w tym przypadki
  brzegowe wpisane wprost w kryteria akceptacji: brak przeładowania i brak utraty stanu
  formularza przy przełączeniu, przetrwanie wylogowania, pierwszeństwo jawnego wyboru nad
  ustawieniem systemu operacyjnego, praca w trybie prywatnym bez błędu, dostępność klawiaturowa.
- **Dawna US4 (mobile-first) przenumerowana na US5**; pozostałe historie rozszerzone o klauzulę
  "w obu motywach" tam, gdzie weryfikacja realnie się podwaja (US1 scenariusz 1 i 4, US3
  scenariusz 6, US4 scenariusze 1, 2, 5, 6, US5 scenariusze 2 i 4).
- **Wymagania: 21 → 28.** Nowy blok *Przełącznik motywu* (FR-008 … FR-016). FR-007 podniesione
  do rangi wymagania systemowego (komplet ról w obu motywach) i wpięte w audyt przez FR-018.
  FR-004 doprecyzowane: zakaz złota jako tekstu dotyczy motywu jasnego, w ciemnym ta sama para
  jest poprawna. FR-026 rozszerzone o komponenty przeglądarki. FR-027 o błysk zapamiętanego
  motywu. FR-028 o wydruk niedziedziczący ciemnego tła.
- **Kryteria sukcesu: 8 → 10.** Dodane SC-005 (osiągalność przełącznika i brak utraty stanu)
  oraz SC-006 (odtworzenie preferencji, degradacja bez błędu).
- **Nowa sekcja Key Entities** — preferencja motywu na urządzeniu. Wcześniej feature nie
  dotykał żadnych danych; teraz dotyka, więc encja została opisana wraz ze stanami i jawną
  adnotacją, że nie zawiera danych osobowych ani medycznych.
- **Edge Cases przepisane** — dopisane: błysk motywu innego niż zapamiętany, uszkodzona wartość
  preferencji, dwie karty na jednym urządzeniu, współdzielony tablet i zmiana po poprzedniku,
  wydruk z motywu ciemnego.
- **Poza zakresem** — dopisana synchronizacja preferencji między urządzeniami i powiązanie
  z kontem (świadomie odrzucone) oraz automatyczne przełączanie wg pory dnia.

**Stan**: 5 historii użytkownika (P1 ×1, P2 ×4), 28 wymagań funkcjonalnych (numeracja ciągła
FR-001 … FR-028), 10 kryteriów sukcesu (SC-001 … SC-010), 1 encja, 0 markerów
[NEEDS CLARIFICATION]. Specyfikacja gotowa do `/speckit-plan`.

### Uwagi dla fazy planowania

- **Principle I (test-first)**: FR-018 wymaga automatycznego audytu kontrastu obejmującego oba
  motywy oraz kompletność ról. Ten test musi powstać i czerwienić się przed wdrożeniem palety —
  jest to naturalny pierwszy krok planu.
- **Principle IV (mobile-first)**: FR-016 i FR-024 oraz US5 wiążą feature z wymogiem
  konstytucyjnym; przełącznik musi być osiągalny od 320 px.
- **Gates**: preferencja motywu celowo żyje na urządzeniu (FR-010, FR-011), więc feature nie
  dotyka modelu konta, sesji, danych pacjenta ani logu audytowego i może być scalony na zielonym
  CI bez udokumentowanego przeglądu bezpieczeństwa. **Plan musi to potwierdzić** — jeżeli
  wdrożenie przełącznika wymusiłoby zmianę w profilu użytkownika, sesji lub uwierzytelnianiu,
  wymóg przeglądu wraca i auto-merge jest wykluczony.
- **Principle V**: feature nie wprowadza ani nie modyfikuje modułu wysokiego ryzyka, więc nie
  wymaga dokumentowania podejścia do dostępności w planie.
- **Ryzyko do zaadresowania w planie**: FR-027 (brak błysku zapamiętanego motywu) i FR-026
  (komponenty przeglądarki zgodne z motywem aplikacji) to dwa miejsca, w których "przełącznik
  motywu" bywa wdrażany pozornie i psuje się dopiero u użytkownika. Warto, by plan wskazał dla
  nich konkretny mechanizm, a nie potraktował ich jako detal implementacyjny.
