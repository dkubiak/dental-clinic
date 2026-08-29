# Feature Specification: Branding i motyw UI (Projekt Uśmiech)

**Feature Branch**: `003-brand-ui-theme`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: "Branding i motyw UI aplikacji Projekt Uśmiech — wdrożenie systemu kolorystycznego marki (design/brand/_pu-tokens.scss) w aplikacji Angular: zastąpienie domyślnej fioletowej palety Angular Material motywem marki (złoto #CBAD89 / grafit #2E2C2D / eukaliptus / ciepłe neutrale), jasny motyw dla aplikacji gabinetu i ciemny dla materiałów pacjenta, kolory funkcyjne dla stanów (sukces/ostrzeżenie/błąd/informacja), zgodność WCAG 2.1 AA, mobile-first zgodnie z Principle IV konstytucji."

## Clarifications

### Session 2026-08-27

- ~~Q: Jaki jest zakres motywu ciemnego w tym feature — aplikacja nie ma dziś żadnej powierzchni skierowanej do pacjenta ani przełącznika motywu? → A: Tylko tokeny. Ciemny wariant zostaje zdefiniowany w systemie i objęty automatycznymi testami kontrastu, ale aplikacja personelu zawsze renderuje się w motywie jasnym.~~ **Rozstrzygnięcie wycofane** — zob. pytanie poniżej.
- Q: Jaki jest zakres motywu ciemnego w tym feature? → A: Aplikacja MUSI zawsze udostępniać użytkownikowi przełącznik motywu jasny/ciemny. Oba motywy są pełnoprawnymi motywami aplikacji personelu, dostępnymi na każdym ekranie, także przed zalogowaniem. Zastępuje to wcześniejsze rozstrzygnięcie "tylko tokeny".
- Q: Gdzie zapamiętywany jest wybór motywu? → A: Na urządzeniu. Preferencja żyje w przeglądarce danego urządzenia i nie jest przenoszona między urządzeniami ani powiązana z kontem użytkownika. Nie powstaje pole w profilu, endpoint API ani migracja bazy. Na współdzielonym tablecie chairside wszyscy użytkownicy dzielą jedno ustawienie — jest to świadoma decyzja, bo warunki oświetleniowe zależą od miejsca pracy, a nie od osoby.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Personel widzi aplikację w kolorach gabinetu (Priority: P1)

Pracownik gabinetu (recepcja, lekarz, asystent, administrator) loguje się i pracuje w aplikacji, która wygląda jak Projekt Uśmiech — te same kolory, co logo na drzwiach gabinetu i materiały dla pacjentów — zamiast domyślnej, generycznej palety narzędziowej.

**Why this priority**: To jest cały sens tego feature'u i jedyna część, która sama w sobie dostarcza wartość. Aplikacja jest dziś fioletowa, czyli wizualnie nie ma nic wspólnego z gabinetem; pacjent siedzący obok fotela widzi ekran tabletu i ten rozjazd podważa spójność marki, za którą gabinet zapłacił. Bez tej historii pozostałe nie mają czego uspójniać.

**Independent Test**: Można w pełni przetestować, przechodząc jako zalogowany użytkownik przez wszystkie istniejące ekrany (logowanie, MFA, reset hasła, ekran startowy roli, wyszukiwarka pacjentów, kartoteka, schemat uzębienia, historia wizyt, konta, log audytu) — osobno w motywie jasnym i ciemnym — i weryfikując, że każdy widoczny kolor pochodzi z zatwierdzonego systemu marki.

**Acceptance Scenarios**:

1. **Given** zalogowany użytkownik dowolnej roli, **When** otwiera dowolny ekran aplikacji w dowolnym motywie, **Then** wszystkie powierzchnie, teksty, obramowania i elementy interaktywne używają wyłącznie kolorów z zatwierdzonego systemu marki.
2. **Given** ekran z akcją główną (np. "Nowy pacjent", "Zapisz"), **When** użytkownik go ogląda, **Then** akcja główna jest wyróżniona kolorem akcentu marki, a pozostałe akcje są wizualnie podrzędne.
3. **Given** dowolny ekran, **When** użytkownik go ogląda, **Then** na ekranie widnieje co najwyżej jedna akcja wyróżniona kolorem akcentu.
4. **Given** ekran logowania (pierwszy kontakt z aplikacją), **When** użytkownik go otwiera, **Then** widoczna jest identyfikacja wizualna gabinetu (znak marki) w kolorach systemu, poprawnie w obu motywach.
5. **Given** zmieniona wartość koloru marki w jednym, centralnym miejscu definicji, **When** aplikacja zostanie ponownie zbudowana, **Then** zmiana jest widoczna na wszystkich ekranach, bez pozostałości poprzedniej wartości.

---

### User Story 2 - Przełącznik motywu dostępny zawsze (Priority: P2)

Użytkownik w każdej chwili i z każdego ekranu przełącza aplikację między motywem jasnym a ciemnym, a urządzenie pamięta ten wybór przy kolejnym otwarciu.

**Why this priority**: Warunki pracy w gabinecie zmieniają się w ciągu dnia — jasne światło zabiegowe przy fotelu i przyciemniony gabinet wieczorem to dwa różne środowiska dla tego samego tabletu. Wymuszanie jednego motywu oznacza, że przez połowę dnia ktoś pracuje w gorszych warunkach. Motyw ciemny jest przy tym najbardziej rozpoznawalnym obliczem marki, bo w nim wykonane jest logo.

**Independent Test**: Można przetestować niezależnie, otwierając aplikację, przełączając motyw na każdym typie ekranu (przed zalogowaniem i po), odświeżając stronę i weryfikując, że wybór został zapamiętany oraz że przy pierwszym otwarciu na czystym urządzeniu aplikacja podąża za ustawieniem systemu operacyjnego.

**Acceptance Scenarios**:

1. **Given** dowolny ekran aplikacji, w tym ekran logowania przed uwierzytelnieniem, **When** użytkownik go otwiera, **Then** przełącznik motywu jest dostępny i możliwy do uruchomienia.
2. **Given** aplikacja w motywie jasnym, **When** użytkownik uruchamia przełącznik, **Then** cały interfejs natychmiast przechodzi w motyw ciemny, bez przeładowania strony i bez utraty stanu ekranu (wprowadzone dane w formularzu, pozycja przewijania, otwarte panele).
3. **Given** użytkownik, który wybrał motyw ciemny, **When** zamyka aplikację i otwiera ją ponownie na tym samym urządzeniu, **Then** aplikacja startuje w motywie ciemnym.
4. **Given** urządzenie, na którym nikt jeszcze nie dokonał wyboru, **When** użytkownik otwiera aplikację, **Then** motyw początkowy odpowiada ustawieniu jasny/ciemny systemu operacyjnego urządzenia.
5. **Given** użytkownik, który dokonał wyboru motywu, **When** ustawienie motywu w systemie operacyjnym urządzenia się zmieni, **Then** aplikacja pozostaje przy jawnym wyborze użytkownika — wybór jest silniejszy niż ustawienie systemu.
6. **Given** urządzenie, na którym zapisanie preferencji jest niemożliwe (tryb prywatny, zablokowane dane witryny), **When** użytkownik przełącza motyw, **Then** przełączenie działa w obrębie sesji, a aplikacja nie zgłasza błędu ani nie przestaje działać.
7. **Given** zalogowany użytkownik, który wybrał motyw, **When** wylogowuje się, **Then** wybór motywu pozostaje w mocy na tym urządzeniu — nie jest czyszczony razem z sesją.
8. **Given** przełącznik motywu, **When** użytkownik nawiguje klawiaturą, **Then** przełącznik jest osiągalny, ma widoczny fokus i czytelną etykietę informującą o aktualnym stanie oraz skutku uruchomienia.

---

### User Story 3 - Stan i komunikat czytelny od pierwszego spojrzenia (Priority: P2)

Osoba przy recepcji lub przy fotelu rzuca okiem na ekran i natychmiast rozpoznaje, czy patrzy na potwierdzenie, ostrzeżenie o zaległości, krytyczną informację medyczną (np. alergia) czy zwykły komunikat systemowy — również wtedy, gdy nie rozróżnia kolorów, i niezależnie od włączonego motywu.

**Why this priority**: Ostrzeżenie i akcent marki to w tej palecie dwa ciepłe, zbliżone odcienie — jeśli nie zostaną świadomie rozdzielone, personel przestanie odróżniać "ważne" od "ozdobnego". W kontekście medycznym pomylenie ostrzeżenia z dekoracją ma realny koszt. Zależy od US1 (system musi istnieć), ale jest testowalne osobno.

**Independent Test**: Można przetestować niezależnie, wywołując na testowym ekranie po jednym komunikacie każdego typu (sukces, ostrzeżenie, błąd, informacja) w obu motywach i weryfikując, że każdy jest rozróżnialny zarówno kolorem, jak i niezależnym od koloru sygnałem (etykieta tekstowa lub ikona) — w tym w symulacji ślepoty barw.

**Acceptance Scenarios**:

1. **Given** komunikat dowolnego typu (sukces / ostrzeżenie / błąd / informacja), **When** jest wyświetlany, **Then** jego typ jest zakodowany co najmniej dwoma sygnałami: kolorem oraz etykietą tekstową lub ikoną.
2. **Given** komunikat ostrzeżenia obok elementu w kolorze akcentu marki, **When** użytkownik ogląda oba, **Then** są wyraźnie rozróżnialne i ostrzeżenie nie może zostać odczytane jako element dekoracyjny.
3. **Given** ekran wyrenderowany w symulacji deuteranopii i protanopii, **When** użytkownik odczytuje typy komunikatów, **Then** każdy typ pozostaje jednoznacznie rozpoznawalny.
4. **Given** kartoteka pacjenta z odnotowaną alergią, **When** ekran jest wyświetlany, **Then** ostrzeżenie medyczne jest wizualnie silniejsze niż komunikaty niekrytyczne.
5. **Given** schemat uzębienia z zębami oznaczonymi jako zdrowy i chory, **When** operator ogląda schemat, **Then** oba stany są rozróżnialne bez polegania wyłącznie na kolorze.
6. **Given** dowolny komunikat lub oznaczenie stanu, **When** użytkownik przełącza motyw, **Then** znaczenie pozostaje rozpoznawalne w obu motywach — żaden typ nie traci rozróżnialności po przełączeniu.

---

### User Story 4 - Czytelność potwierdzona automatycznie, nie na oko (Priority: P2)

Zespół ma pewność, że żadna zmiana wizualna nie wprowadzi nieczytelnego zestawienia kolorów, bo kontrast jest sprawdzany automatycznie w obu motywach przy każdym uruchomieniu testów, a nie oceniany ręcznie po wdrożeniu.

**Why this priority**: Paleta zawiera kolor (złoto marki), który na jasnym tle ma kontrast 1.99:1 — jest atrakcyjny i kuszący do użycia jako tekst, a jednocześnie nieczytelny. Dopuszczenie dwóch motywów podwaja liczbę zestawień do pilnowania, więc bez automatycznego strażnika ten błąd wróci przy pierwszej kolejnej zmianie UI. Konstytucja (Principle I) i tak wymaga testu przed implementacją, więc ta historia formalizuje regułę zamiast polegać na dyscyplinie.

**Independent Test**: Można przetestować niezależnie, wprowadzając celowo zestawienie łamiące próg kontrastu w jednym z motywów i weryfikując, że zestaw testów to wykrywa i zgłasza porażkę wskazującą konkretny motyw i konkretną parę kolorów.

**Acceptance Scenarios**:

1. **Given** zdefiniowany system kolorystyczny, **When** uruchamiany jest zestaw testów, **Then** każda para tekst/tło przewidziana do użycia jest sprawdzana pod kątem progu kontrastu, osobno w motywie jasnym i ciemnym.
2. **Given** para tekst/tło o kontraście poniżej 4.5:1 dla tekstu podstawowego, **When** uruchamiany jest zestaw testów, **Then** test kończy się porażką i wskazuje, który motyw, która para i o ile nie spełnia progu.
3. **Given** element interfejsu lub duży tekst o kontraście poniżej 3:1 względem sąsiadującego tła, **When** uruchamiany jest zestaw testów, **Then** test kończy się porażką.
4. **Given** kolor akcentu marki użyty jako kolor tekstu na jasnym tle, **When** uruchamiany jest zestaw testów, **Then** test kończy się porażką (to jest znana pułapka tej palety; na tle grafitowym ta sama para jest poprawna).
5. **Given** element interaktywny w stanie fokusu klawiaturowego, **When** uruchamiany jest zestaw testów, **Then** wskaźnik fokusu jest weryfikowany pod kątem widoczności i kontrastu w obu motywach.
6. **Given** rola semantyczna zdefiniowana w jednym motywie, **When** uruchamiany jest zestaw testów, **Then** brak jej odpowiednika w drugim motywie kończy się porażką.

---

### User Story 5 - Czytelność przy fotelu, na tablecie (Priority: P2)

Lekarz i asystent pracują chairside na tablecie lub telefonie, często pod ostrym światłem lampy zabiegowej i pod kątem — a mimo to odczytują dane pacjenta bez mrużenia oczu i bez przewijania w bok, w motywie dobranym do warunków.

**Why this priority**: Principle IV konstytucji wymaga projektowania mobile-first, a warunki gabinetu (odblask, kąt patrzenia, ekran trzymany w dłoni) są dla kontrastu ostrzejszym testem niż monitor przy biurku. Paleta o niskim kontraście przechodzi test na desktopie i zawodzi przy fotelu. To jest też praktyczne uzasadnienie przełącznika z US2.

**Independent Test**: Można przetestować niezależnie, otwierając kluczowe ekrany kliniczne na szerokości od 320 px w obu motywach i weryfikując brak poziomego przewijania, zachowanie hierarchii wizualnej, czytelność tekstu drugorzędnego oraz osiągalność przełącznika motywu.

**Acceptance Scenarios**:

1. **Given** dowolny ekran aplikacji, **When** jest wyświetlany przy szerokości 320 px, **Then** treść mieści się bez poziomego przewijania, a kolory zachowują ten sam kontrast co na desktopie.
2. **Given** ekran kliniczny (kartoteka, schemat uzębienia), **When** jest wyświetlany na tablecie, **Then** hierarchia wizualna (treść główna vs. drugorzędna) pozostaje czytelna bez powiększania, w obu motywach.
3. **Given** element interaktywny, **When** użytkownik wchodzi z nim w interakcję dotykiem, **Then** stan aktywny/wciśnięty jest rozpoznawalny wizualnie mimo zasłonięcia elementu palcem.
4. **Given** aplikacja na szerokości 320 px, **When** użytkownik szuka przełącznika motywu, **Then** przełącznik jest osiągalny bez poziomego przewijania i bez wchodzenia w ustawienia zagnieżdżone głębiej niż jeden poziom.

---

### Edge Cases

- Co się dzieje, gdy użytkownik ma w systemie operacyjnym włączony tryb wysokiego kontrastu lub wymuszone kolory? Aplikacja musi pozostać użyteczna, nawet jeśli kolory marki zostaną nadpisane przez system, a przełącznik motywu nie może wtedy wprowadzać w błąd.
- Co widzi użytkownik, zanim style zdążą się załadować, jeśli na urządzeniu zapamiętano motyw ciemny? Nie może wystąpić błysk motywu jasnego przed przełączeniem na zapamiętany.
- Co się dzieje, gdy zapisana preferencja motywu jest uszkodzona lub zawiera nieznaną wartość? Aplikacja musi wrócić do zachowania domyślnego, a nie wyświetlić pusty lub połamany interfejs.
- Jak zachowuje się aplikacja otwarta jednocześnie w dwóch kartach na tym samym urządzeniu, gdy w jednej z nich użytkownik zmieni motyw? Karty nie mogą trwale rozjechać się wizualnie.
- Co się dzieje z komponentami przeglądarki, na których wygląd aplikacja ma ograniczony wpływ (pola formularza, autouzupełnianie, paski przewijania, natywne okna dialogowe), gdy motyw aplikacji jest inny niż motyw systemu operacyjnego? Nie mogą stać się nieczytelne.
- Co się dzieje na współdzielonym tablecie, gdy kolejna osoba zastaje motyw wybrany przez poprzednika? Musi mieć możliwość natychmiastowej zmiany bez wchodzenia w konfigurację konta.
- Co się dzieje, gdy ekran zawiera treść wygenerowaną poza systemem marki (np. osadzony dokument, załącznik, komunikat przeglądarki)? Taka treść nie może rozbijać czytelności otoczenia.
- Jak wygląda ekran wydrukowany lub zapisany do PDF w czerni i bieli (np. skierowanie)? Informacja zakodowana kolorem musi przetrwać utratę koloru, a wydruk nie może wyjść z ciemnym tłem tylko dlatego, że aplikacja była w motywie ciemnym.
- Jak zachowuje się interfejs przy powiększeniu tekstu do 200%, wymaganym przez WCAG? Kontrast i hierarchia muszą zostać zachowane w obu motywach.
- Co się dzieje ze stanem fokusu klawiaturowego na elemencie w kolorze akcentu marki? Wskaźnik fokusu nie może zlewać się z tłem, na którym leży — w żadnym z motywów.

## Requirements *(mandatory)*

### Functional Requirements

#### System kolorystyczny

- **FR-001**: Aplikacja MUSI prezentować wyłącznie kolory należące do zatwierdzonego systemu kolorystycznego marki; żaden ekran nie może używać domyślnej palety narzędziowej ani wartości spoza systemu.
- **FR-002**: System kolorystyczny MUSI być zdefiniowany w jednym, centralnym miejscu, tak aby zmiana wartości koloru propagowała się na wszystkie ekrany bez edytowania poszczególnych widoków.
- **FR-003**: System MUSI definiować kolory poprzez role semantyczne (np. tło, powierzchnia, tekst główny, tekst drugorzędny, obramowanie, akcent, tekst akcentowy, treść na akcencie), a nie poprzez nazwy odcieni, tak aby zmiana odcienia nie wymagała zmian w widokach.
- **FR-004**: System MUSI rozróżniać rolę "akcent jako płaszczyzna" (tło przycisku, linia, kreska) od roli "akcent jako tekst"; złoto marki MUSI być niedostępne jako kolor tekstu na jasnym tle, przy czym w motywie ciemnym ta sama rola jest dopuszczalna.
- **FR-005**: System MUSI definiować cztery kolory funkcyjne o odrębnych znaczeniach: sukces, ostrzeżenie, błąd, informacja — każdy w wariancie dla motywu jasnego i ciemnego.
- **FR-006**: Kolor ostrzeżenia MUSI być wizualnie rozróżnialny od koloru akcentu marki w obu motywach, tak aby ostrzeżenie nie mogło zostać odczytane jako element dekoracyjny.
- **FR-007**: Każda rola semantyczna zdefiniowana w jednym motywie MUSI mieć zdefiniowany odpowiednik w drugim; żadna rola nie może pozostać bez wartości.

#### Przełącznik motywu

- **FR-008**: Aplikacja MUSI udostępniać przełącznik motywu jasny/ciemny na każdym ekranie, w tym przed zalogowaniem.
- **FR-009**: Przełączenie motywu MUSI zmieniać cały interfejs natychmiast, bez przeładowania strony i bez utraty stanu ekranu (danych wprowadzonych w formularzu, pozycji przewijania, otwartych paneli).
- **FR-010**: Wybór motywu MUSI być zapamiętywany w obrębie urządzenia i odtwarzany przy kolejnym otwarciu aplikacji. Preferencja MUSI NIE być powiązana z kontem użytkownika ani przenoszona między urządzeniami.
- **FR-011**: Wybór motywu MUSI przetrwać wylogowanie użytkownika — nie może być czyszczony razem z sesją.
- **FR-012**: Przy pierwszym otwarciu na urządzeniu bez zapisanej preferencji aplikacja MUSI przyjąć motyw zgodny z ustawieniem jasny/ciemny systemu operacyjnego.
- **FR-013**: Jawny wybór użytkownika MUSI mieć pierwszeństwo przed ustawieniem systemu operacyjnego; późniejsza zmiana ustawienia systemu MUSI NIE nadpisywać dokonanego wyboru.
- **FR-014**: Gdy zapisanie preferencji jest niemożliwe (tryb prywatny, zablokowane dane witryny) lub zapisana wartość jest nieprawidłowa, aplikacja MUSI działać dalej z zachowaniem domyślnym i MUSI NIE zgłaszać użytkownikowi błędu.
- **FR-015**: Przełącznik motywu MUSI być osiągalny klawiaturą, mieć widoczny wskaźnik fokusu oraz etykietę komunikującą aktualny stan i skutek uruchomienia.
- **FR-016**: Przełącznik motywu MUSI być osiągalny przy szerokości ekranu od 320 px bez poziomego przewijania i bez zagnieżdżenia głębszego niż jeden poziom.

#### Dostępność

- **FR-017**: Każda para tekst/tło przewidziana do użycia MUSI osiągać kontrast co najmniej 4.5:1 dla tekstu podstawowego oraz co najmniej 3:1 dla dużego tekstu i elementów interfejsu (WCAG 2.1 AA) — niezależnie w motywie jasnym i ciemnym.
- **FR-018**: Zgodność z progami kontrastu MUSI być weryfikowana automatycznym testem uruchamianym w ramach zestawu testów, a nie oceną ręczną; test MUSI wskazywać konkretny motyw i konkretną parę kolorów, która progu nie spełnia. Test MUSI również wykrywać rolę semantyczną bez odpowiednika w drugim motywie (FR-007).
- **FR-019**: Żadna informacja MUSI NIE być przekazywana wyłącznie kolorem; każdy stan i typ komunikatu MUSI mieć dodatkowy, niezależny od koloru sygnał (etykieta tekstowa lub ikona), zachowany w obu motywach.
- **FR-020**: Każdy element interaktywny MUSI mieć widoczny wskaźnik fokusu klawiaturowego spełniający próg kontrastu względem tła, na którym leży, w obu motywach.
- **FR-021**: Interfejs MUSI pozostać użyteczny przy powiększeniu tekstu do 200% oraz przy włączonym systemowym trybie wymuszonych kolorów.

#### Spójność i mobile-first

- **FR-022**: Akcja główna na ekranie MUSI być wyróżniona kolorem akcentu; na jednym ekranie MUSI występować co najwyżej jedna taka akcja.
- **FR-023**: Ekran logowania MUSI prezentować identyfikację wizualną gabinetu w kolorach systemu, poprawnie w obu motywach.
- **FR-024**: Wszystkie ekrany MUSZĄ zachowywać zdefiniowany kontrast i hierarchię wizualną przy szerokości ekranu od 320 px, bez poziomego przewijania, w obu motywach (Principle IV).
- **FR-025**: Stan interaktywny elementu (hover, aktywny, wciśnięty, wyłączony) MUSI być rozpoznawalny wizualnie również przy obsłudze dotykiem, gdy element jest częściowo zasłonięty palcem.
- **FR-026**: Aplikacja MUSI stosować jeden spójny motyw w obrębie całego interfejsu; MUSI NIE być możliwa sytuacja, w której część ekranu renderuje się w jednym motywie, a część w drugim — również w odniesieniu do komponentów przeglądarki, na które aplikacja ma ograniczony wpływ (pola formularza, autouzupełnianie, paski przewijania).
- **FR-027**: Aplikacja MUSI unikać błysku nieostylowanej treści lub błysku motywu innego niż zapamiętany podczas ładowania.
- **FR-028**: Informacja zakodowana kolorem MUSI pozostać czytelna po utracie koloru (wydruk lub zapis do dokumentu w skali szarości); wydruk MUSI NIE dziedziczyć ciemnego tła z motywu ciemnego.

### Key Entities

- **Preferencja motywu (na urządzeniu)**: zapamiętany wybór użytkownika między motywem jasnym a ciemnym. Przechowywana w obrębie przeglądarki danego urządzenia, wspólna dla wszystkich osób korzystających z tego urządzenia, niezwiązana z kontem ani sesją. Możliwe stany: brak zapisanej wartości (aplikacja podąża za systemem operacyjnym), motyw jasny, motyw ciemny. Nie zawiera żadnych danych osobowych ani medycznych.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% ekranów aplikacji prezentuje wyłącznie kolory z zatwierdzonego systemu — audyt wszystkich widoków w obu motywach nie wykrywa ani jednej wartości spoza listy.
- **SC-002**: Automatyczny audyt kontrastu zgłasza 0 naruszeń progów WCAG 2.1 AA dla wszystkich par tekst/tło i wszystkich elementów interfejsu, w obu motywach.
- **SC-003**: 100% ról semantycznych ma zdefiniowaną wartość w obu motywach — audyt nie wykrywa ani jednej roli bez odpowiednika.
- **SC-004**: 100% komunikatów i oznaczeń stanu przekazuje swoje znaczenie co najmniej dwoma niezależnymi sygnałami (kolor plus etykieta lub ikona), w obu motywach.
- **SC-005**: Przełącznik motywu jest osiągalny z każdego ekranu aplikacji w co najwyżej jednej interakcji, a zmiana motywu następuje bez przeładowania strony i bez utraty wprowadzonych danych — 0 przypadków utraty stanu w testach przełączania.
- **SC-006**: Wybór motywu zostaje odtworzony przy ponownym otwarciu aplikacji w 100% przypadków, w których urządzenie pozwala zapisać preferencję; w pozostałych przypadkach aplikacja działa dalej bez błędu widocznego dla użytkownika.
- **SC-007**: W teście z udziałem personelu, przeprowadzonym na tablecie w warunkach gabinetu, ponad 90% uczestników poprawnie identyfikuje typ komunikatu (sukces / ostrzeżenie / błąd / informacja) przy pierwszym spojrzeniu, w obu motywach.
- **SC-008**: Zmiana jednej wartości koloru w centralnej definicji zmienia wygląd wszystkich ekranów, na których ta rola występuje, bez żadnych pozostałości poprzedniej wartości.
- **SC-009**: Każdy ekran aplikacji jest w pełni użyteczny przy szerokości 320 px oraz przy powiększeniu tekstu do 200% — 0 przypadków poziomego przewijania i 0 przypadków przycięcia treści, w obu motywach.
- **SC-010**: Ponad 90% pracowników gabinetu rozpoznaje aplikację jako należącą do Projektu Uśmiech na podstawie samego zrzutu ekranu, bez widocznej nazwy.

## Assumptions

- **Paleta jest zatwierdzona i nie podlega renegocjacji w tym feature.** Wartości kolorystyczne pochodzą z `design/brand/_pu-tokens.scss` (wyprowadzone z próbkowania pliku logo) i zostały już zweryfikowane pod kątem WCAG 2.1 AA w obu wariantach. Ten feature wdraża paletę, nie projektuje jej od nowa.
- **Zakres obejmuje wszystkie ekrany istniejące na moment rozpoczęcia prac, w obu motywach** — logowanie, wyzwanie MFA, żądanie i potwierdzenie resetu hasła, powłoka aplikacji, ekran startowy roli, wyszukiwarka pacjentów, zakładanie kartoteki, kartoteka pacjenta, schemat uzębienia, historia wizyt, zarządzanie kontami, log audytu. Dopuszczenie dwóch motywów podwaja powierzchnię weryfikacji — każdy ekran musi zostać sprawdzony dwukrotnie.
- **Zmieniamy warstwę wizualną plus jeden nowy element sterujący.** Feature nie zmienia układu ekranów, przepływów, treści komunikatów ani zachowania aplikacji poza dodaniem przełącznika motywu i zapamiętywaniem jego stanu.
- **Preferencja motywu nie jest danymi osobowymi.** Nie podlega wymogom RODO dotyczącym danych pacjenta i nie wymaga wpisu w logu audytowym w rozumieniu Principle III — nie jest operacją na danych pacjenta ani klinicznych.
- **Schemat uzębienia zostaje przekolorowany w obecnym zakresie stanów** (zdrowy / chory). Wprowadzenie kolorów dla poszczególnych jednostek chorobowych zostało świadomie odroczone w specyfikacji 002 i pozostaje poza zakresem.
- **Znak marki jest dostępny jako zasób graficzny** w wariancie czytelnym na jasnym i na ciemnym tle.
- **Nie powstaje publiczna strona ani materiały marketingowe.** Repozytorium zawiera wyłącznie aplikację dla personelu; wszelkie powierzchnie skierowane do pacjenta są poza zakresem tego feature'u — motyw ciemny powstaje tu na potrzeby samego personelu, nie jako przygotowanie pod stronę.
- **Feature nie dotyka danych pacjenta, uwierzytelniania, autoryzacji ani logowania audytowego.** Preferencja motywu żyje na urządzeniu i nie dotyka modelu konta ani sesji (FR-010, FR-011). Zgodnie z Development Workflow & Quality Gates konstytucji nie uruchamia to wymogu udokumentowanego przeglądu bezpieczeństwa/zgodności, a zmiana może zostać scalona na zielonym CI. Jeśli w trakcie planowania okaże się, że wdrożenie wymaga zmian w logice tych obszarów, wymóg przeglądu wraca do gry.
- **Feature nie wprowadza ani nie modyfikuje modułu wysokiego ryzyka** w rozumieniu Principle V — jest przekrojową warstwą prezentacji, nie nowym modułem, więc nie zmienia granic domen awarii.

## Poza zakresem

- Publiczna strona internetowa gabinetu i materiały marketingowe dla pacjentów.
- Synchronizacja preferencji motywu między urządzeniami oraz powiązanie jej z kontem użytkownika (rozstrzygnięte w Clarifications — preferencja żyje na urządzeniu).
- Personalizacja kolorów przez użytkownika końcowego poza wyborem motywu jasny/ciemny (np. wybór własnego koloru akcentu).
- Zmiany typografii, ikonografii, siatki, odstępów i układu ekranów — ten feature dotyczy koloru i jednego przełącznika.
- Nowe kolory dla jednostek chorobowych na schemacie uzębienia (odroczone w specyfikacji 002).
- Tryb wysokiego kontrastu jako trzeci, osobno projektowany motyw aplikacji — wymagamy jedynie, by aplikacja nie psuła się przy systemowym trybie wymuszonych kolorów.
- Automatyczne przełączanie motywu według pory dnia lub czujnika oświetlenia.

## Dependencies

- `design/brand/_pu-tokens.scss` oraz `design/brand/README.md` — zatwierdzony system kolorystyczny wraz z udokumentowanymi wartościami kontrastu dla obu motywów.
- Feature 001 (uwierzytelnianie i RBAC) oraz feature 002 (kartoteka pacjentów) — dostarczają ekrany, które ten feature przekolorowuje.
- Zasób graficzny znaku marki w wariancie na jasne i na ciemne tło.
