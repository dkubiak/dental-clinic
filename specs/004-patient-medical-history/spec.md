# Feature Specification: Historia medyczna pacjenta

**Feature Branch**: `004-patient-medical-history`

**Created**: 2026-08-29

**Status**: Draft

## Clarifications

### Session 2026-08-29

- Q: Czy pole "status" w `ChronicConditionEntry` (aktywna/przebyta — stan kliniczny) to to samo
  pole, co status "aktualny/nieaktualny" wymagany przez FR-010 (model korekty append-only), czy to
  dwa niezależne pola? → A: Dwa niezależne pola na każdym typie wpisu — domenowe (dla chorób:
  aktywna/przebyta) osobno od technicznego "aktualny/nieaktualny" (korekta, wspólne dla alergii,
  leków i chorób).
- Q: Czy wpisy "nieaktualne/superseded" mają być domyślnie widoczne na liście, czy domyślnie
  ukryte za rozwijalną opcją "pokaż historię zmian"? → A: Domyślnie widoczne są tylko aktualne
  wpisy (`recordStatus = aktualny`); nieaktualne dostępne pod jawnym rozwinięciem "historia zmian"
  per sekcja (alergie/leki/choroby osobno).
- Q: Czy rozwinięcie "historia zmian" ma być dostępne dla ASSISTANT (odczyt wg FR-004), czy tylko
  dla DOCTOR? → A: ASSISTANT widzi historię zmian tak samo jak DOCTOR — ten sam zakres odczytu
  (bez edycji) obejmuje zarówno bieżące wpisy, jak i historię korekt.
- Q: SC-004 mówiło o rozpoznaniu krytycznej alergii "w ciągu pierwszych kilku sekund" — nieskwan-
  tyfikowany przymiotnik. Jaki konkretny, testowalny próg go zastąpi? → A: Bez progu czasowego —
  kryterium przeformułowane jako widoczność bez przewijania i bez dodatkowej interakcji
  (kliknięcia/rozwinięcia), czyli sygnał wizualny musi być w pierwszym ekranie kartoteki od razu
  po jej otwarciu.

**Input**: User description: "Historia medyczna pacjenta: rozszerzenie kartoteki pacjenta (zbudowanej w feature 002-patient-records) o sekcję danych klinicznych obejmującą trzy elementy: (1) alergie — lista z substancją/czynnikiem, typem reakcji i wagą (krytyczna / umiarkowana), która ma zasilać już istniejący komponent status-indicator z feature 003-brand-ui-theme (odblokowuje zablokowane zadanie T049 w specs/003-brand-ui-theme/tasks.md); (2) przyjmowane leki — lista z nazwą, dawką i datą rozpoczęcia, istotna przy planowaniu zabiegów (interakcje lekowe, znieczulenie); (3) choroby przewlekłe/przebyte — lista z nazwą, statusem (aktywna/przebyta) i datą rozpoznania, dająca lekarzowi pełniejszy obraz stanu zdrowia pacjenta. Dane szczególnej kategorii wg RODO Art. 9 — pełny audit log, RBAC ograniczające dostęp (RECEPTION bez wglądu w treść kliniczną), wymóg udokumentowanego przeglądu bezpieczeństwa przed merge, moduł pozostaje w klasyfikacji high-risk 'patient records' (Principle V)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Lekarz przegląda i odnotowuje alergie pacjenta (Priority: P1)

Lekarz otwiera kartotekę pacjenta przed zabiegiem i musi natychmiast wiedzieć, czy pacjent ma
odnotowane alergie oraz jak poważne są to reakcje, zanim podejmie decyzję o materiałach czy
znieczuleniu. Lekarz może też dodać nową alergię wykrytą podczas wizyty lub skorygować istniejący
wpis.

**Why this priority**: To najwyższe ryzyko kliniczne ze wszystkich trzech elementów — pominięta
lub niewidoczna alergia (np. na lateks czy określony lek znieczulający) może prowadzić do
bezpośredniego zagrożenia zdrowia pacjenta podczas zabiegu. Odblokowuje też zadanie T049 z
`specs/003-brand-ui-theme/tasks.md`, które czeka na realne pole alergii do zasilenia komponentu
`status-indicator`.

**Independent Test**: Można w pełni przetestować logując się jako DOCTOR, otwierając kartotekę
pacjenta z co najmniej jedną alergią o wadze "krytyczna", i weryfikując, że wpis jest widoczny na
liście oraz wizualnie wyróżniony (via `status-indicator`) bez konieczności implementacji leków czy
chorób przewlekłych.

**Acceptance Scenarios**:

1. **Given** kartoteka pacjenta bez odnotowanych alergii, **When** lekarz otwiera zakładkę historii
   medycznej, **Then** system pokazuje czytelny stan "brak odnotowanych alergii" zamiast pustej
   lub błędnej sekcji.
2. **Given** kartoteka pacjenta z alergią o wadze "krytyczna" (np. na penicylinę, reakcja:
   anafilaksja), **When** lekarz otwiera kartotekę, **Then** wpis jest wizualnie wyróżniony jako
   krytyczny (poprzez `status-indicator`) w sposób widoczny bez przewijania.
3. **Given** lekarz przegląda kartotekę pacjenta, **When** dodaje nowy wpis alergii (substancja,
   typ reakcji, waga), **Then** wpis zostaje zapisany, natychmiast widoczny na liście, a operacja
   jest odnotowana w logu audytowym (kto/co/kiedy).

---

### User Story 2 - Lekarz przegląda przyjmowane leki pacjenta przed zabiegiem (Priority: P2)

Lekarz planujący zabieg musi znać leki aktualnie przyjmowane przez pacjenta, aby ocenić ryzyko
interakcji lekowych (np. z lekami przeciwbólowymi czy znieczuleniem) i odpowiednio dobrać
protokół leczenia.

**Why this priority**: Bezpośrednio wpływa na bezpieczeństwo planowania zabiegu, ale jest mniej
czasowo krytyczne niż alergie (nie wymaga natychmiastowego wizualnego alertu w każdym widoku —
wystarczy, że jest dostępne w kartotece).

**Independent Test**: Można przetestować niezależnie logując się jako DOCTOR, dodając wpis leku
(nazwa, dawka, data rozpoczęcia) do kartoteki pacjenta i weryfikując, że jest widoczny na liście
oraz odnotowany w logu audytowym — bez zależności od stanu sekcji alergii czy chorób przewlekłych.

**Acceptance Scenarios**:

1. **Given** kartoteka pacjenta bez odnotowanych leków, **When** lekarz otwiera zakładkę historii
   medycznej, **Then** system pokazuje czytelny stan "brak odnotowanych leków".
2. **Given** lekarz przegląda kartotekę pacjenta, **When** dodaje wpis leku (nazwa, dawka, data
   rozpoczęcia), **Then** wpis zostaje zapisany, widoczny na liście wraz z datą rozpoczęcia, a
   operacja jest odnotowana w logu audytowym.

---

### User Story 3 - Lekarz przegląda choroby przewlekłe/przebyte pacjenta (Priority: P3)

Lekarz chce mieć pełny obraz stanu zdrowia pacjenta — w tym choroby przewlekłe (np. cukrzyca,
choroby serca) i istotne choroby przebyte — jako kontekst kliniczny przy planowaniu leczenia.

**Why this priority**: Wartościowy kontekst kliniczny, ale najmniej czasowo krytyczny z trzech
elementów — nie wpływa bezpośrednio na natychmiastowe decyzje w trakcie zabiegu tak jak alergie
czy interakcje lekowe.

**Independent Test**: Można przetestować niezależnie logując się jako DOCTOR, dodając wpis choroby
(nazwa, status aktywna/przebyta, data rozpoznania) do kartoteki pacjenta i weryfikując, że jest
widoczny na liście oraz odnotowany w logu audytowym.

**Acceptance Scenarios**:

1. **Given** kartoteka pacjenta bez odnotowanych chorób przewlekłych/przebytych, **When** lekarz
   otwiera zakładkę historii medycznej, **Then** system pokazuje czytelny stan "brak odnotowanych
   chorób".
2. **Given** lekarz przegląda kartotekę pacjenta, **When** dodaje wpis choroby (nazwa, status, data
   rozpoznania), **Then** wpis zostaje zapisany, widoczny na liście ze statusem, a operacja jest
   odnotowana w logu audytowym.

---

### Edge Cases

- Co się dzieje, gdy użytkownik z rolą RECEPTION próbuje otworzyć zakładkę historii medycznej
  pacjenta? System musi odmówić dostępu do treści klinicznej, pokazując co najwyżej sam fakt
  istnienia krytycznego alertu (np. z komponentu `status-indicator`), bez ujawniania substancji,
  leków czy chorób.
- Co się dzieje, gdy ASSISTANT próbuje edytować (dodać/zmienić/usunąć) wpis w historii medycznej?
  System musi odmówić — ASSISTANT ma dostęp tylko do odczytu (patrz FR-004, Assumptions).
- Jak system zachowuje się dla pacjentów utworzonych przed wdrożeniem tej funkcji (brak istniejącej
  historii medycznej)? Wszystkie trzy sekcje muszą pokazywać stan pusty, nie błąd.
- Co się dzieje przy żądaniu eksportu lub usunięcia danych pacjenta (prawa podmiotu danych, RODO)?
  Dane z historii medycznej muszą być uwzględnione w istniejącym mechanizmie eksportu/usunięcia z
  `002-patient-records` na tych samych zasadach co pozostałe dane pacjenta.
- Co się dzieje, gdy lekarz chce skorygować błędnie wprowadzony wpis (np. źle podaną wagę alergii)?
  Zgodnie z FR-010, lekarz dodaje nowy wpis; poprzedni zostaje oznaczony jako nieaktualny i znika
  z domyślnego widoku sekcji, ale pozostaje dostępny pod rozwinięciem "historia zmian" — nie jest
  usuwany ani nadpisywany.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST umożliwić roli DOCTOR dodawanie, przeglądanie i korygowanie (w modelu
  append-only, patrz FR-010) wpisów alergii pacjenta, każdy ze substancją/czynnikiem, typem
  reakcji i wagą (krytyczna / umiarkowana).
- **FR-002**: System MUST umożliwić roli DOCTOR dodawanie, przeglądanie i korygowanie (w modelu
  append-only, patrz FR-010) wpisów przyjmowanych leków pacjenta, każdy z nazwą, dawką i datą
  rozpoczęcia.
- **FR-003**: System MUST umożliwić roli DOCTOR dodawanie, przeglądanie i korygowanie (w modelu
  append-only, patrz FR-010) wpisów chorób przewlekłych/przebytych pacjenta, każdy z nazwą,
  statusem (aktywna / przebyta) i datą rozpoznania.
- **FR-004**: System MUST umożliwić roli ASSISTANT odczyt (bez edycji) wszystkich trzech sekcji
  historii medycznej, w tym rozwinięcia "historia zmian" (wpisy `recordStatus = nieaktualny`) — ten
  sam zakres odczytu co DOCTOR, bez prawa edycji.
- **FR-005**: System MUST uniemożliwić roli RECEPTION dostęp do treści klinicznej historii
  medycznej; RECEPTION może co najwyżej widzieć sam fakt istnienia krytycznego alertu alergii, bez
  szczegółów (substancja, typ reakcji, leki, choroby).
- **FR-006**: System MUST wizualnie wyróżniać wpisy alergii o wadze "krytyczna" przy pomocy
  istniejącego komponentu `status-indicator` (feature 003-brand-ui-theme), spójnie z drugim
  sygnałem wizualnym już zastosowanym na schemacie uzębienia.
- **FR-007**: System MUST rejestrować każdy odczyt i zapis danych historii medycznej w
  niemodyfikowalnym, append-only logu audytowym (kto/co/kiedy/stan przed-po), zgodnie z zasadą
  Full Auditability.
- **FR-008**: Wpisy w logu audytowym dotyczące historii medycznej MUST NOT być edytowalne ani
  usuwalne przez żadną rolę w normalnym przepływie aplikacji, spójnie z istniejącym mechanizmem
  audytu.
- **FR-009**: Dane historii medycznej MUST być uwzględnione w istniejących mechanizmach realizacji
  praw podmiotu danych (eksport / usunięcie na żądanie pacjenta) na tych samych zasadach co
  pozostałe dane pacjenta z `002-patient-records`.
- **FR-010**: System MUST implementować korektę błędnie wprowadzonych wpisów (alergia/lek/choroba)
  w modelu append-only: DOCTOR nie edytuje ani nie usuwa istniejącego wpisu bezpośrednio, tylko
  dodaje nowy wpis, a poprzedni zostaje oznaczony jako nieaktualny/superseded poprzez wspólną,
  techniczną flagę `recordStatus` — niezależną od jakiegokolwiek pola statusu klinicznego (patrz
  Key Entities, Clarifications Session 2026-08-29). Pełna historia wpisów (nie tylko log audytowy)
  MUST pozostać dostępna w kartotece, ale domyślnie ukryta: każda sekcja (alergie/leki/choroby)
  pokazuje domyślnie tylko wpisy z `recordStatus = aktualny`, a wpisy nieaktualne są dostępne pod
  jawnym rozwinięciem "historia zmian" per sekcja, tak by domyślny widok pozostał nieprzeładowany
  (spójne z SC-004).
- **FR-011**: System MUST przyjmować nazwy substancji/alergenów, leków i chorób jako wolny tekst
  wpisywany przez lekarza, bez walidacji względem zewnętrznego słownika/kodowania (np. ICD-10) w
  zakresie tej funkcji.
- **FR-012**: System MUST pokazywać czytelny stan pusty ("brak odnotowanych...") dla każdej z
  trzech sekcji, gdy pacjent nie ma żadnych wpisów, zamiast błędu lub pustego ekranu — dotyczy w
  szczególności pacjentów istniejących sprzed wdrożenia tej funkcji.

### Key Entities *(include if feature involves data)*

Każdy z trzech typów wpisów poniżej niesie, niezależnie od swoich pól domenowych, wspólną **flagę
korekty** `recordStatus` (aktualny / nieaktualny-superseded), używaną wyłącznie przez mechanizm
korekty append-only z FR-010 — nie miesza się ona z ewentualnym polem statusu klinicznego (patrz
`ChronicConditionEntry` poniżej, Clarifications Session 2026-08-29).

- **AllergyEntry**: pojedynczy wpis alergii pacjenta — substancja/czynnik, typ reakcji, waga
  (krytyczna / umiarkowana), `recordStatus` (aktualny / nieaktualny-superseded), powiązany z
  konkretnym pacjentem i znacznikiem czasu utworzenia.
- **MedicationEntry**: pojedynczy wpis przyjmowanego leku — nazwa, dawka, data rozpoczęcia,
  `recordStatus` (aktualny / nieaktualny-superseded), powiązany z konkretnym pacjentem.
- **ChronicConditionEntry**: pojedynczy wpis choroby przewlekłej/przebytej — nazwa, kliniczny
  status choroby (aktywna / przebyta — stan zdrowia pacjenta, niezależny od korekty), data
  rozpoznania, `recordStatus` (aktualny / nieaktualny-superseded — techniczna flaga korekty wpisu,
  odrębna od statusu klinicznego), powiązany z konkretnym pacjentem.
- **audit_log_entry** (istniejąca tabela, feature 001 — bez zmian schematu): ta funkcja dodaje
  wyłącznie 3 nowe wartości enuma `audit_event_type` (`MEDICAL_HISTORY_ENTRY_ADDED`,
  `MEDICAL_HISTORY_ENTRY_VIEWED`, `MEDICAL_HISTORY_HISTORY_VIEWED`); żaden nowy byt/tabela audytu
  nie powstaje — patrz data-model.md.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Lekarz widzi pełny obraz historii medycznej pacjenta (alergie, leki, choroby
  przewlekłe/przebyte) na jednym ekranie kartoteki, bez konieczności przechodzenia do osobnych
  widoków.
- **SC-002**: 100% odczytów i zapisów danych historii medycznej pojawia się w logu audytowym.
- **SC-003**: 100% prób dostępu roli RECEPTION do treści klinicznej historii medycznej kończy się
  odmową dostępu, przy zachowaniu widoczności samego faktu istnienia krytycznego alertu.
- **SC-004**: Sygnał krytycznej alergii jest widoczny na pierwszym ekranie kartoteki od razu po jej
  otwarciu — bez przewijania strony i bez żadnej dodatkowej interakcji (kliknięcia, rozwinięcia
  sekcji).
- **SC-005**: Pacjenci bez odnotowanej historii medycznej (w tym wszyscy pacjenci istniejący przed
  wdrożeniem tej funkcji) wyświetlają czytelny stan pusty w 100% przypadków, bez błędów.

## Assumptions

- Edycja (dodawanie/zmiana) wpisów historii medycznej jest zarezerwowana dla roli DOCTOR; ASSISTANT
  ma dostęp tylko do odczytu — przyjęte jako bezpieczny domyślny podział zgodny z zasadą
  minimalizacji RODO, do potwierdzenia w `/speckit-clarify` jeśli wymagany szerszy zakres dla
  ASSISTANT.
- Rola ADMINISTRATOR nie ma rutynowego wglądu klinicznego w historię medyczną; dostęp do tych
  danych w kontekście compliance (eksport/usunięcie na żądanie pacjenta) odbywa się przez istniejący
  mechanizm praw podmiotu danych z `002-patient-records`, nie przez bezpośredni podgląd kliniczny.
- Ta funkcja pozostaje w module "patient records", już sklasyfikowanym jako high-risk
  (Principle V, Risk-Tiered High Availability) — nie zmienia klasyfikacji ryzyka modułu, ale
  `/speckit-plan` musi to udokumentować.
- Dla pacjentów istniejących (utworzonych przed wdrożeniem tej funkcji) brak historii medycznej
  jest stanem początkowym — nie wymaga migracji/backfillu danych.
- Zakres nie obejmuje integracji z zewnętrznymi systemami e-recepty ani automatycznego sprawdzania
  interakcji lekowych — to wyłącznie odnotowanie i wyświetlenie danych dla oceny klinicznej przez
  lekarza.
- Ta zmiana dotyka danych pacjenta, więc zgodnie z bramkami workflow w konstytucji wymaga
  udokumentowanego przeglądu bezpieczeństwa/zgodności w PR przed merge i nie może mieć włączonego
  auto-merge (Development Workflow & Quality Gates).
