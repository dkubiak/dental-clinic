# Feature Specification: Branding i motyw UI (Projekt Uśmiech)

**Feature Branch**: `003-brand-ui-theme`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: "Branding i motyw UI aplikacji Projekt Uśmiech — wdrożenie systemu kolorystycznego marki (design/brand/_pu-tokens.scss) w aplikacji Angular: zastąpienie domyślnej fioletowej palety Angular Material motywem marki (złoto #CBAD89 / grafit #2E2C2D / eukaliptus / ciepłe neutrale), jasny motyw dla aplikacji gabinetu i ciemny dla materiałów pacjenta, kolory funkcyjne dla stanów (sukces/ostrzeżenie/błąd/informacja), zgodność WCAG 2.1 AA, mobile-first zgodnie z Principle IV konstytucji."

## Clarifications

### Session 2026-08-27

- Q: Jaki jest zakres motywu ciemnego w tym feature — aplikacja nie ma dziś żadnej powierzchni skierowanej do pacjenta ani przełącznika motywu? → A: Tylko tokeny. Ciemny wariant zostaje zdefiniowany w systemie i objęty automatycznymi testami kontrastu, ale aplikacja personelu zawsze renderuje się w motywie jasnym. Brak przełącznika, brak preferencji użytkownika, brak podążania za ustawieniem systemu operacyjnego. Wariant grafit+złoto jest gotowy do użycia, gdy powstanie strona lub materiały dla pacjenta (osobna specyfikacja).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Personel widzi aplikację w kolorach gabinetu (Priority: P1)

Pracownik gabinetu (recepcja, lekarz, asystent, administrator) loguje się i pracuje w aplikacji, która wygląda jak Projekt Uśmiech — te same kolory, co logo na drzwiach gabinetu i materiały dla pacjentów — zamiast domyślnej, generycznej palety narzędziowej.

**Why this priority**: To jest cały sens tego feature'u i jedyna część, która sama w sobie dostarcza wartość. Aplikacja jest dziś fioletowa, czyli wizualnie nie ma nic wspólnego z gabinetem; pacjent siedzący obok fotela widzi ekran tabletu i ten rozjazd podważa spójność marki, za którą gabinet zapłacił. Bez tej historii pozostałe nie mają czego uspójniać.

**Independent Test**: Można w pełni przetestować, przechodząc jako zalogowany użytkownik przez wszystkie istniejące ekrany (logowanie, MFA, reset hasła, ekran startowy roli, wyszukiwarka pacjentów, kartoteka, schemat uzębienia, historia wizyt, konta, log audytu) i weryfikując, że każdy widoczny kolor pochodzi z zatwierdzonego systemu marki — żaden ekran nie prezentuje kolorów spoza listy.

**Acceptance Scenarios**:

1. **Given** zalogowany użytkownik dowolnej roli, **When** otwiera dowolny ekran aplikacji, **Then** wszystkie powierzchnie, teksty, obramowania i elementy interaktywne używają wyłącznie kolorów z zatwierdzonego systemu marki.
2. **Given** ekran z akcją główną (np. "Nowy pacjent", "Zapisz"), **When** użytkownik go ogląda, **Then** akcja główna jest wyróżniona kolorem akcentu marki, a pozostałe akcje są wizualnie podrzędne.
3. **Given** dowolny ekran, **When** użytkownik go ogląda, **Then** na ekranie widnieje co najwyżej jedna akcja wyróżniona kolorem akcentu.
4. **Given** ekran logowania (pierwszy kontakt z aplikacją), **When** użytkownik go otwiera, **Then** widoczna jest identyfikacja wizualna gabinetu (znak marki) w kolorach systemu.
5. **Given** zmieniona wartość koloru marki w jednym, centralnym miejscu definicji, **When** aplikacja zostanie ponownie zbudowana, **Then** zmiana jest widoczna na wszystkich ekranach, bez pozostałości poprzedniej wartości.

---

### User Story 2 - Stan i komunikat czytelny od pierwszego spojrzenia (Priority: P2)

Osoba przy recepcji lub przy fotelu rzuca okiem na ekran i natychmiast rozpoznaje, czy patrzy na potwierdzenie, ostrzeżenie o zaległości, krytyczną informację medyczną (np. alergia) czy zwykły komunikat systemowy — również wtedy, gdy nie rozróżnia kolorów.

**Why this priority**: Ostrzeżenie i akcent marki to w tej palecie dwa ciepłe, zbliżone odcienie — jeśli nie zostaną świadomie rozdzielone, personel przestanie odróżniać "ważne" od "ozdobnego". W kontekście medycznym pomylenie ostrzeżenia z dekoracją ma realny koszt. Zależy od US1 (system musi istnieć), ale jest testowalne osobno.

**Independent Test**: Można przetestować niezależnie, wywołując na testowym ekranie po jednym komunikacie każdego typu (sukces, ostrzeżenie, błąd, informacja) i weryfikując, że każdy jest rozróżnialny zarówno kolorem, jak i niezależnym od koloru sygnałem (etykieta tekstowa lub ikona) — w tym w symulacji ślepoty barw.

**Acceptance Scenarios**:

1. **Given** komunikat dowolnego typu (sukces / ostrzeżenie / błąd / informacja), **When** jest wyświetlany, **Then** jego typ jest zakodowany co najmniej dwoma sygnałami: kolorem oraz etykietą tekstową lub ikoną.
2. **Given** komunikat ostrzeżenia obok elementu w kolorze akcentu marki, **When** użytkownik ogląda oba, **Then** są wyraźnie rozróżnialne i ostrzeżenie nie może zostać odczytane jako element dekoracyjny.
3. **Given** ekran wyrenderowany w symulacji deuteranopii i protanopii, **When** użytkownik odczytuje typy komunikatów, **Then** każdy typ pozostaje jednoznacznie rozpoznawalny.
4. **Given** kartoteka pacjenta z odnotowaną alergią, **When** ekran jest wyświetlany, **Then** ostrzeżenie medyczne jest wizualnie silniejsze niż komunikaty niekrytyczne.
5. **Given** schemat uzębienia z zębami oznaczonymi jako zdrowy i chory, **When** operator ogląda schemat, **Then** oba stany są rozróżnialne bez polegania wyłącznie na kolorze.

---

### User Story 3 - Czytelność potwierdzona automatycznie, nie na oko (Priority: P2)

Zespół ma pewność, że żadna zmiana wizualna nie wprowadzi nieczytelnego zestawienia kolorów, bo kontrast jest sprawdzany automatycznie przy każdym uruchomieniu testów, a nie oceniany ręcznie po wdrożeniu.

**Why this priority**: Paleta zawiera kolor (złoto marki), który na jasnym tle ma kontrast 1.99:1 — jest atrakcyjny i kuszący do użycia jako tekst, a jednocześnie nieczytelny. Bez automatycznego strażnika ten błąd wróci przy pierwszej kolejnej zmianie UI. Konstytucja (Principle I) i tak wymaga testu przed implementacją, więc ta historia formalizuje regułę zamiast polegać na dyscyplinie.

**Independent Test**: Można przetestować niezależnie, wprowadzając celowo zestawienie łamiące próg kontrastu i weryfikując, że zestaw testów to wykrywa i zgłasza porażkę wskazującą konkretną parę kolorów.

**Acceptance Scenarios**:

1. **Given** zdefiniowany system kolorystyczny, **When** uruchamiany jest zestaw testów, **Then** każda para tekst/tło przewidziana do użycia jest sprawdzana pod kątem progu kontrastu.
2. **Given** para tekst/tło o kontraście poniżej 4.5:1 dla tekstu podstawowego, **When** uruchamiany jest zestaw testów, **Then** test kończy się porażką i wskazuje, która para i o ile nie spełnia progu.
3. **Given** element interfejsu lub duży tekst o kontraście poniżej 3:1 względem sąsiadującego tła, **When** uruchamiany jest zestaw testów, **Then** test kończy się porażką.
4. **Given** kolor akcentu marki użyty jako kolor tekstu na jasnym tle, **When** uruchamiany jest zestaw testów, **Then** test kończy się porażką (to jest znana pułapka tej palety).
5. **Given** element interaktywny w stanie fokusu klawiaturowego, **When** uruchamiany jest zestaw testów, **Then** wskaźnik fokusu jest weryfikowany pod kątem widoczności i kontrastu w każdym motywie.

---

### User Story 4 - Czytelność przy fotelu, na tablecie (Priority: P2)

Lekarz i asystent pracują chairside na tablecie lub telefonie, często pod ostrym światłem lampy zabiegowej i pod kątem — a mimo to odczytują dane pacjenta bez mrużenia oczu i bez przewijania w bok.

**Why this priority**: Principle IV konstytucji wymaga projektowania mobile-first, a warunki gabinetu (odblask, kąt patrzenia, ekran trzymany w dłoni) są dla kontrastu ostrzejszym testem niż monitor przy biurku. Paleta o niskim kontraście przechodzi test na desktopie i zawodzi przy fotelu.

**Independent Test**: Można przetestować niezależnie, otwierając kluczowe ekrany kliniczne na szerokości od 320 px i weryfikując brak poziomego przewijania, zachowanie hierarchii wizualnej oraz czytelność tekstu drugorzędnego.

**Acceptance Scenarios**:

1. **Given** dowolny ekran aplikacji, **When** jest wyświetlany przy szerokości 320 px, **Then** treść mieści się bez poziomego przewijania, a kolory zachowują ten sam kontrast co na desktopie.
2. **Given** ekran kliniczny (kartoteka, schemat uzębienia), **When** jest wyświetlany na tablecie, **Then** hierarchia wizualna (treść główna vs. drugorzędna) pozostaje czytelna bez powiększania.
3. **Given** element interaktywny, **When** użytkownik wchodzi z nim w interakcję dotykiem, **Then** stan aktywny/wciśnięty jest rozpoznawalny wizualnie mimo zasłonięcia elementu palcem.

---

### User Story 5 - Ciemny wariant marki gotowy do użycia (Priority: P3)

Ciemny wariant systemu — grafit i złoto, dokładnie tak jak logo — jest zdefiniowany, przetestowany i czeka gotowy na moment, w którym powstanie strona lub materiały dla pacjentów. Aplikacja personelu pozostaje jasna.

**Why this priority**: Ciemny wariant jest najbardziej rozpoznawalnym obliczem marki — to w nim wykonane jest logo — więc pominięcie go teraz oznaczałoby zdefiniowanie systemu w połowie i powrót do tej pracy za kilka miesięcy. Jednocześnie nie blokuje pracy gabinetu: aplikacja personelu świadomie zostaje jasna, bo pracuje się w niej po kilka godzin dziennie. Dlatego trafia na koniec kolejki, ale nie wypada z zakresu.

**Independent Test**: Można przetestować niezależnie, uruchamiając automatyczny audyt kontrastu przeciwko wariantowi ciemnemu i weryfikując, że każda rola semantyczna ma w nim zdefiniowaną wartość spełniającą te same progi co w wariancie jasnym — bez uruchamiania aplikacji i bez żadnej zmiany w tym, co widzi personel.

**Acceptance Scenarios**:

1. **Given** zdefiniowany system kolorystyczny, **When** uruchamiany jest zestaw testów, **Then** każda rola semantyczna zdefiniowana w wariancie jasnym ma odpowiednik w wariancie ciemnym — brak roli bez wartości.
2. **Given** wariant ciemny, **When** uruchamiany jest automatyczny audyt kontrastu, **Then** wszystkie pary tekst/tło oraz elementy interfejsu spełniają te same progi WCAG 2.1 AA co w wariancie jasnym.
3. **Given** kolor akcentu marki na tle grafitowym, **When** uruchamiany jest audyt kontrastu, **Then** para przechodzi test jako kolor tekstu — w wariancie ciemnym złoto wolno używać jako tekst, w odróżnieniu od wariantu jasnego.
4. **Given** dowolny ekran aplikacji personelu, **When** użytkownik ma w systemie operacyjnym włączony motyw ciemny, **Then** aplikacja nadal renderuje się w motywie jasnym — wariant ciemny nie jest w tym feature udostępniany użytkownikowi.
5. **Given** wariant ciemny zdefiniowany w systemie, **When** przeglądamy interfejs aplikacji personelu, **Then** żaden ekran go nie używa — wariant istnieje wyłącznie jako gotowa definicja dla przyszłych powierzchni skierowanych do pacjenta.

---

### Edge Cases

- Co się dzieje, gdy użytkownik ma w systemie operacyjnym włączony tryb wysokiego kontrastu lub wymuszone kolory? Aplikacja musi pozostać użyteczna, nawet jeśli kolory marki zostaną nadpisane przez system.
- Jak zachowuje się ekran, gdy użytkownik ma w systemie operacyjnym ustawiony ciemny motyw? Aplikacja pozostaje jasna (FR-021), ale nie może dojść do sytuacji, w której komponenty przeglądarki (pola formularza, paski przewijania, autouzupełnianie) przyjmują ciemny motyw systemu i stają się nieczytelne na jasnym tle aplikacji.
- Co się dzieje, gdy ekran zawiera treść wygenerowaną poza systemem marki (np. osadzony dokument, załącznik, komunikat przeglądarki)? Taka treść nie może rozbijać czytelności otoczenia.
- Jak wygląda ekran wydrukowany lub zapisany do PDF w czerni i bieli (np. skierowanie)? Informacja zakodowana kolorem musi przetrwać utratę koloru.
- Co widzi użytkownik, zanim style zdążą się załadować? Nie może wystąpić błysk nieostylowanej treści ani odwrotnego motywu.
- Jak zachowuje się interfejs przy powiększeniu tekstu do 200%, wymaganym przez WCAG? Kontrast i hierarchia muszą zostać zachowane.
- Co się dzieje ze stanem fokusu klawiaturowego na elemencie w kolorze akcentu marki? Wskaźnik fokusu nie może zlewać się z tłem, na którym leży.

## Requirements *(mandatory)*

### Functional Requirements

#### System kolorystyczny

- **FR-001**: Aplikacja MUSI prezentować wyłącznie kolory należące do zatwierdzonego systemu kolorystycznego marki; żaden ekran nie może używać domyślnej palety narzędziowej ani wartości spoza systemu.
- **FR-002**: System kolorystyczny MUSI być zdefiniowany w jednym, centralnym miejscu, tak aby zmiana wartości koloru propagowała się na wszystkie ekrany bez edytowania poszczególnych widoków.
- **FR-003**: System MUSI definiować kolory poprzez role semantyczne (np. tło, powierzchnia, tekst główny, tekst drugorzędny, obramowanie, akcent, tekst akcentowy, treść na akcencie), a nie poprzez nazwy odcieni, tak aby zmiana odcienia nie wymagała zmian w widokach.
- **FR-004**: System MUSI rozróżniać rolę "akcent jako płaszczyzna" (tło przycisku, linia, kreska) od roli "akcent jako tekst"; złoto marki MUSI być niedostępne jako kolor tekstu na jasnym tle.
- **FR-005**: System MUSI definiować cztery kolory funkcyjne o odrębnych znaczeniach: sukces, ostrzeżenie, błąd, informacja — każdy w wariancie dla jasnego i ciemnego tła.
- **FR-006**: Kolor ostrzeżenia MUSI być wizualnie rozróżnialny od koloru akcentu marki, tak aby ostrzeżenie nie mogło zostać odczytane jako element dekoracyjny.

#### Dostępność

- **FR-007**: Każda para tekst/tło przewidziana do użycia MUSI osiągać kontrast co najmniej 4.5:1 dla tekstu podstawowego oraz co najmniej 3:1 dla dużego tekstu i elementów interfejsu (WCAG 2.1 AA).
- **FR-008**: Zgodność z progami kontrastu MUSI być weryfikowana automatycznym testem uruchamianym w ramach zestawu testów, a nie oceną ręczną; test MUSI wskazywać konkretną parę kolorów, która progu nie spełnia.
- **FR-009**: Żadna informacja MUSI NIE być przekazywana wyłącznie kolorem; każdy stan i typ komunikatu MUSI mieć dodatkowy, niezależny od koloru sygnał (etykieta tekstowa lub ikona).
- **FR-010**: Każdy element interaktywny MUSI mieć widoczny wskaźnik fokusu klawiaturowego spełniający próg kontrastu względem tła, na którym leży.
- **FR-011**: Interfejs MUSI pozostać użyteczny przy powiększeniu tekstu do 200% oraz przy włączonym systemowym trybie wymuszonych kolorów.

#### Spójność i mobile-first

- **FR-012**: Akcja główna na ekranie MUSI być wyróżniona kolorem akcentu; na jednym ekranie MUSI występować co najwyżej jedna taka akcja.
- **FR-013**: Ekran logowania MUSI prezentować identyfikację wizualną gabinetu w kolorach systemu.
- **FR-014**: Wszystkie ekrany MUSZĄ zachowywać zdefiniowany kontrast i hierarchię wizualną przy szerokości ekranu od 320 px, bez poziomego przewijania (Principle IV).
- **FR-015**: Stan interaktywny elementu (hover, aktywny, wciśnięty, wyłączony) MUSI być rozpoznawalny wizualnie również przy obsłudze dotykiem, gdy element jest częściowo zasłonięty palcem.
- **FR-016**: Aplikacja MUSI stosować jeden spójny motyw w obrębie całego interfejsu; MUSI NIE być możliwa sytuacja, w której część ekranu renderuje się w jednym motywie, a część w drugim.
- **FR-017**: Aplikacja MUSI unikać błysku nieostylowanej treści lub odwrotnego motywu podczas ładowania.
- **FR-018**: Informacja zakodowana kolorem MUSI pozostać czytelna po utracie koloru (wydruk lub zapis do dokumentu w skali szarości).

#### Motyw ciemny

- **FR-019**: System MUSI definiować ciemny wariant każdej roli semantycznej zdefiniowanej w wariancie jasnym; żadna rola nie może pozostać bez wartości w wariancie ciemnym.
- **FR-020**: Ciemny wariant MUSI spełniać te same progi kontrastu co wariant jasny (FR-007) i MUSI być objęty tym samym automatycznym audytem (FR-008).
- **FR-021**: Aplikacja personelu MUSI renderować się zawsze w motywie jasnym, niezależnie od ustawień motywu w systemie operacyjnym użytkownika; ciemny wariant MUSI NIE być udostępniany użytkownikowi w tym feature (brak przełącznika, brak zapamiętywanej preferencji).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% ekranów aplikacji prezentuje wyłącznie kolory z zatwierdzonego systemu — audyt wszystkich widoków nie wykrywa ani jednej wartości spoza listy.
- **SC-002**: Automatyczny audyt kontrastu zgłasza 0 naruszeń progów WCAG 2.1 AA dla wszystkich par tekst/tło i wszystkich elementów interfejsu, w obu zdefiniowanych wariantach (jasnym i ciemnym).
- **SC-002a**: 100% ról semantycznych ma zdefiniowaną wartość w obu wariantach — audyt nie wykrywa ani jednej roli bez odpowiednika w wariancie ciemnym.
- **SC-003**: 100% komunikatów i oznaczeń stanu przekazuje swoje znaczenie co najmniej dwoma niezależnymi sygnałami (kolor plus etykieta lub ikona).
- **SC-004**: W teście z udziałem personelu, przeprowadzonym na tablecie w warunkach gabinetu, ponad 90% uczestników poprawnie identyfikuje typ komunikatu (sukces / ostrzeżenie / błąd / informacja) przy pierwszym spojrzeniu.
- **SC-005**: Zmiana jednej wartości koloru w centralnej definicji zmienia wygląd wszystkich ekranów, na których ta rola występuje, bez żadnych pozostałości poprzedniej wartości.
- **SC-006**: Każdy ekran aplikacji jest w pełni użyteczny przy szerokości 320 px oraz przy powiększeniu tekstu do 200% — 0 przypadków poziomego przewijania i 0 przypadków przycięcia treści.
- **SC-007**: Ponad 90% pracowników gabinetu rozpoznaje aplikację jako należącą do Projektu Uśmiech na podstawie samego zrzutu ekranu, bez widocznej nazwy.

## Assumptions

- **Paleta jest zatwierdzona i nie podlega renegocjacji w tym feature.** Wartości kolorystyczne pochodzą z `design/brand/_pu-tokens.scss` (wyprowadzone z próbkowania pliku logo) i zostały już zweryfikowane pod kątem WCAG 2.1 AA. Ten feature wdraża paletę, nie projektuje jej od nowa.
- **Zakres obejmuje wszystkie ekrany istniejące na moment rozpoczęcia prac** — logowanie, wyzwanie MFA, żądanie i potwierdzenie resetu hasła, powłoka aplikacji, ekran startowy roli, wyszukiwarka pacjentów, zakładanie kartoteki, kartoteka pacjenta, schemat uzębienia, historia wizyt, zarządzanie kontami, log audytu. Pozostawienie części aplikacji w starej palecie byłoby gorsze niż stan obecny, bo rozjazd wizualny w obrębie jednego narzędzia czyta się jako usterka.
- **Zmieniamy wyłącznie warstwę wizualną.** Feature nie zmienia układu ekranów, przepływów, treści komunikatów ani zachowania aplikacji — tylko to, jakimi kolorami są renderowane.
- **Schemat uzębienia zostaje przekolorowany w obecnym zakresie stanów** (zdrowy / chory). Wprowadzenie kolorów dla poszczególnych jednostek chorobowych zostało świadomie odroczone w specyfikacji 002 i pozostaje poza zakresem.
- **Znak marki jest dostępny jako zasób graficzny** do osadzenia na ekranie logowania i w powłoce aplikacji.
- **Nie powstaje publiczna strona ani materiały marketingowe.** Repozytorium zawiera wyłącznie aplikację dla personelu; wszelkie powierzchnie skierowane do pacjenta są poza zakresem tego feature'u.
- **Feature nie dotyka danych pacjenta, uwierzytelniania, autoryzacji ani logowania audytowego** — zmienia jedynie prezentację ekranów, które te dane wyświetlają. Zgodnie z Development Workflow & Quality Gates konstytucji nie uruchamia to wymogu udokumentowanego przeglądu bezpieczeństwa/zgodności, a zmiana może zostać scalona na zielonym CI. Jeśli w trakcie planowania okaże się, że wdrożenie wymaga zmian w logice tych obszarów, wymóg przeglądu wraca do gry.
- **Feature nie wprowadza ani nie modyfikuje modułu wysokiego ryzyka** w rozumieniu Principle V — jest przekrojową warstwą prezentacji, nie nowym modułem, więc nie zmienia granic domen awarii.

## Poza zakresem

- Publiczna strona internetowa gabinetu i materiały marketingowe dla pacjentów.
- Zmiany typografii, ikonografii, siatki, odstępów i układu ekranów — ten feature dotyczy koloru.
- Nowe kolory dla jednostek chorobowych na schemacie uzębienia (odroczone w specyfikacji 002).
- Tryb wysokiego kontrastu jako osobny, projektowany motyw aplikacji — wymagamy jedynie, by aplikacja nie psuła się przy systemowym trybie wymuszonych kolorów.
- Personalizacja kolorów przez użytkownika końcowego, w tym jakikolwiek przełącznik motywu jasny/ciemny oraz zapamiętywanie preferencji motywu (rozstrzygnięte w Clarifications — wariant ciemny powstaje wyłącznie jako definicja).
- Podążanie aplikacji personelu za ustawieniem motywu w systemie operacyjnym urządzenia.

## Dependencies

- `design/brand/_pu-tokens.scss` oraz `design/brand/README.md` — zatwierdzony system kolorystyczny wraz z udokumentowanymi wartościami kontrastu.
- Feature 001 (uwierzytelnianie i RBAC) oraz feature 002 (kartoteka pacjentów) — dostarczają ekrany, które ten feature przekolorowuje.
- Zasób graficzny znaku marki.
