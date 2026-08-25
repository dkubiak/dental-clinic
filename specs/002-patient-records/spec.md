# Feature Specification: Kartoteka pacjentów (dane podstawowe i stan uzębienia)

**Feature Branch**: `002-patient-records`

**Created**: 2026-08-24

**Status**: Draft

**Input**: User description: "Robimy nowy moduł, kartotekę pacjentów. Rejestrator i lekarz ma możliwość dodawania nowego pacjenta. Podstawowe dane imię nazwisko adres pesel. Ta kartoteka będzie mocno powiązana z kolejnym modułem wizyt pacjentów ale zrobimy to w kolejnej spec. Teraz kartoteka, tam oprócz danych, historia wizyt, aktualny stan uzębienia. Fajnie gdyby operator lekarz/asystentka miała obraz szczęki gdzie wybiera ząb i określa czy zdrowy czy chory. W późniejszym etapie to rozwiniemy dodamy kolory na różne problemy na zębie opisy, jednostki chorobowe itd. Teraz prosty schemat."

## Clarifications

### Session 2026-08-24

- Q: Sekcja "historia wizyt" w kartotece — skoro pełny moduł wizyt będzie osobną spec, co ma robić teraz? → A: Placeholder bez edycji — sekcja widoczna w UI, ale bez możliwości dodawania wpisów, dopóki nie powstanie moduł wizyt.
- Q: RBAC z modułu 001 ma dokładnie 3 role (recepcja, lekarz, administrator) — kto edytuje stan uzębienia, czy "asystentka" to nowa rola? → A: Dodajemy nową rolę "asystent/asystentka". Role "lekarz" i "asystent/asystentka" mogą zmieniać stan uzębienia; rola "recepcja" nie ma do tego dostępu. Wymaga to rozszerzenia modelu RBAC ustalonego w module 001 o czwartą rolę (zob. Assumptions).
- Q: Czy PESEL jest polem obowiązkowym, czy trzeba wspierać pacjentów bez PESEL? → A: Opcjonalny bez zamiennika — brak PESEL jest dopuszczalny, bez wymogu podania innego dokumentu tożsamości; pacjent może być tymczasowo identyfikowany danymi osobowymi (imię, nazwisko, data urodzenia).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Założenie kartoteki nowego pacjenta (Priority: P1)

Rejestrator lub lekarz zakłada nową kartotekę pacjenta, wprowadzając dane podstawowe: imię, nazwisko, adres i PESEL.

**Why this priority**: To fundament modułu — bez możliwości założenia kartoteki nie istnieje żaden pacjent, do którego można by przypisać historię wizyt czy stan uzębienia. Musi działać jako pierwsze i samodzielnie dostarcza wartość (rejestr pacjentów kliniki).

**Independent Test**: Można w pełni przetestować, logując się jako rejestrator (i osobno jako lekarz), zakładając nową kartotekę z kompletem danych podstawowych i weryfikując, że pacjent pojawia się w rejestrze z poprawnie zapisanymi danymi.

**Acceptance Scenarios**:

1. **Given** zalogowany rejestrator, **When** wprowadza imię, nazwisko, adres i (opcjonalnie) PESEL nowego pacjenta i zapisuje formularz, **Then** system tworzy nową kartotekę pacjenta i wyświetla potwierdzenie.
2. **Given** zalogowany lekarz, **When** wprowadza dane podstawowe nowego pacjenta, **Then** system tworzy kartotekę na tych samych zasadach co dla rejestratora.
3. **Given** formularz nowej kartoteki, **When** użytkownik poda PESEL o nieprawidłowym formacie lub sumie kontrolnej, **Then** system odrzuca zapis i wskazuje błąd bez tworzenia kartoteki.
4. **Given** istniejąca kartoteka z danym PESEL, **When** ktoś próbuje założyć drugą kartotekę z tym samym PESEL, **Then** system odrzuca operację i informuje o istniejącym duplikacie.
5. **Given** formularz nowej kartoteki bez podanego PESEL, **When** użytkownik zapisuje dane podstawowe (imię, nazwisko, adres, data urodzenia), **Then** system tworzy kartotekę bez automatycznego sprawdzenia duplikatu (brak PESEL uniemożliwia jednoznaczną deduplikację).
6. **Given** dowolna rola inna niż rejestrator/lekarz/administrator, **When** próbuje uzyskać dostęp do formularza zakładania kartoteki, **Then** system odmawia dostępu.

---

### User Story 2 - Wizualne oznaczanie stanu uzębienia (Priority: P2)

Lekarz lub asystent/asystentka otwiera kartotekę pacjenta, widzi graficzny schemat szczęki, wybiera pojedynczy ząb i oznacza jego stan jako zdrowy lub chory.

**Why this priority**: To główna kliniczna wartość kartoteki ponad zwykłą listą danych kontaktowych — pozwala szybko ocenić aktualny stan jamy ustnej pacjenta. Zależy od istnienia kartoteki (P1), ale nie blokuje jej dostarczenia.

**Independent Test**: Można przetestować niezależnie, otwierając istniejącą kartotekę testowego pacjenta jako lekarz i osobno jako asystent/asystentka, wybierając kolejno kilka zębów na schemacie, oznaczając je jako zdrowy/chory i weryfikując, że stan zapisuje się i jest widoczny po ponownym otwarciu kartoteki.

**Acceptance Scenarios**:

1. **Given** otwarta kartoteka pacjenta, **When** lekarz albo asystent/asystentka wybiera ząb na schemacie szczęki i oznacza go jako "chory", **Then** system zapisuje ten stan i odzwierciedla go wizualnie na schemacie.
2. **Given** ząb oznaczony wcześniej jako "chory", **When** lekarz albo asystent/asystentka zmienia jego stan na "zdrowy", **Then** system aktualizuje stan i zachowuje poprzednią wartość w logu audytowym.
3. **Given** nowo założona kartoteka bez wcześniejszych oznaczeń, **When** uprawniony operator otwiera schemat szczęki, **Then** wszystkie zęby domyślnie mają stan "zdrowy" (brak odnotowanych problemów).
4. **Given** rola "recepcja" (rejestrator), **When** próbuje zmienić stan zęba lub uzyskać dostęp do schematu szczęki, **Then** system odmawia operacji — dostęp do stanu uzębienia jest zarezerwowany dla ról "lekarz" i "asystent/asystentka".

---

### User Story 3 - Podgląd historii wizyt pacjenta z poziomu kartoteki (Priority: P3)

Rejestrator lub lekarz otwiera w kartotece pacjenta sekcję historii wizyt, widoczną jako miejsce docelowo zasilane przez przyszły moduł wizyt.

**Why this priority**: Sygnalizuje personelowi, gdzie w przyszłości pojawi się historia wizyt, i domyka układ kartoteki, ale nie dostarcza jeszcze samodzielnej wartości klinicznej — stąd priorytet niższy niż dane podstawowe i stan uzębienia.

**Independent Test**: Można przetestować niezależnie, otwierając kartotekę dowolnego testowego pacjenta jako rejestrator i jako lekarz oraz weryfikując, że sekcja historii wizyt jest widoczna, wyświetla stan pusty i nie oferuje żadnej akcji dodawania wpisu.

**Acceptance Scenarios**:

1. **Given** otwarta kartoteka dowolnego pacjenta, **When** rejestrator lub lekarz otwiera sekcję historii wizyt, **Then** system wyświetla czytelny stan pusty/placeholder informujący, że historia wizyt pojawi się po wdrożeniu modułu wizyt.
2. **Given** sekcja historii wizyt, **When** rejestrator lub lekarz próbuje dodać wpis, **Then** system nie udostępnia takiej akcji w tej wersji (formularz/przycisk dodawania nie istnieje).

---

### Edge Cases

- Co się dzieje, gdy dwóch operatorów jednocześnie edytuje tę samą kartotekę (np. oboje zmieniają stan tego samego zęba)? System musi zapisać ostateczny stan bez utraty danych audytowych obu prób.
- Jak system reaguje na próbę założenia kartoteki z niekompletnymi danymi podstawowymi (np. brak adresu)?
- Co się dzieje, gdy PESEL wygląda poprawnie (suma kontrolna zgodna), ale odpowiada innej płci/dacie urodzenia niż podana ręcznie przez operatora? System powinien sygnalizować niespójność do weryfikacji, nie blokując bezwzględnie zapisu.
- Pacjent bez PESEL: ponieważ deduplikacja opiera się na PESEL, dwie kartoteki tej samej osoby (bez PESEL) mogą zostać założone niezależnie bez ostrzeżenia systemu — personel odpowiada za ręczną weryfikację przed założeniem nowej kartoteki, gdy podejrzewa istniejący wpis.
- Co się dzieje z historią zmian stanu zęba, gdy pacjent zgłasza żądanie usunięcia danych (prawo do bycia zapomnianym) — patrz FR-010 i zasada retencji dokumentacji medycznej.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST umożliwiać rolom "rejestrator" i "lekarz" założenie nowej kartoteki pacjenta z danymi podstawowymi: imię, nazwisko, data urodzenia, adres, PESEL (opcjonalny).
- **FR-002**: System MUST walidować format i sumę kontrolną numeru PESEL, gdy jest podany; PESEL jest polem opcjonalnym — kartoteka może zostać założona bez PESEL i bez wymogu podania jakiegokolwiek dokumentu zastępczego.
- **FR-003**: System MUST odrzucać założenie nowej kartoteki, jeśli podany PESEL już istnieje w rejestrze (wykrywanie duplikatów); ta kontrola dotyczy wyłącznie przypadków, w których PESEL został podany — system nie wykonuje automatycznej deduplikacji dla kartotek bez PESEL.
- **FR-004**: System MUST udostępniać w kartotece sekcję historii wizyt jako widoczny, tylko-do-odczytu placeholder (bez możliwości dodawania, edycji ani usuwania wpisów w tej wersji), sygnalizujący miejsce docelowo zasilane przez przyszły moduł wizyt.
- **FR-005**: System MUST wyświetlać graficzny schemat szczęki (standardowe uzębienie stałe osoby dorosłej) umożliwiający wybór pojedynczego zęba.
- **FR-006**: System MUST pozwalać wyłącznie rolom "lekarz" i "asystent/asystentka" na oznaczenie stanu wybranego zęba jako "zdrowy" albo "chory"; rola "recepcja" nie ma dostępu do wyświetlania ani edycji stanu uzębienia.
- **FR-006a**: System MUST wprowadzić nową rolę RBAC "asystent/asystentka", rozszerzając model ról ustalony w module 001 (recepcja, lekarz, administrator) o czwartą rolę z uprawnieniami ograniczonymi do odczytu danych podstawowych pacjenta (na potrzeby identyfikacji) oraz odczytu/edycji stanu uzębienia.
- **FR-007**: System MUST rejestrować każdą operację utworzenia, odczytu, aktualizacji i usunięcia danych kartoteki (dane podstawowe, stan uzębienia) w niemodyfikowalnym logu audytowym (kto/co/kiedy/przed-po), zgodnie z zasadą pełnej audytowalności.
- **FR-008**: System MUST szyfrować dane kartoteki (dane podstawowe i dane medyczne) w spoczynku i w trakcie przesyłania, jako dane szczególnej kategorii w rozumieniu RODO Art. 9.
- **FR-009**: System MUST umożliwiać uprawnionej osobie wyeksportowanie kompletu danych pojedynczego pacjenta na potrzeby realizacji prawa do przenoszenia/wglądu w dane (RODO).
- **FR-010**: System MUST umożliwiać obsługę żądania usunięcia/anonimizacji danych pacjenta, z zachowaniem danych wymaganych przez obowiązujące przepisy o retencji dokumentacji medycznej.
- **FR-011**: System MUST pozwalać rolom "rejestrator" i "lekarz" na edycję istniejących danych podstawowych pacjenta (imię, nazwisko, data urodzenia, adres, PESEL), z zachowaniem wpisu w logu audytowym każdej zmiany.
- **FR-012**: System MUST umożliwiać wyszukanie istniejącej kartoteki pacjenta po nazwisku lub numerze PESEL (gdy podany).

### Key Entities *(include if feature involves data)*

- **Pacjent (kartoteka)**: reprezentuje osobę leczoną w klinice; dane podstawowe (imię, nazwisko, data urodzenia, adres, opcjonalny PESEL jako unikalny identyfikator gdy podany), powiązanie ze stanem uzębienia i (docelowo) historią wizyt.
- **Stan uzębienia**: zestaw pozycji odpowiadających poszczególnym zębom pacjenta, każda z aktualnym stanem (zdrowy/chory), znacznikiem czasu ostatniej zmiany i powiązaniem z operatorem (lekarz lub asystent/asystentka), który dokonał zmiany; w tej wersji bez kolorów, opisów czy jednostek chorobowych (planowane w kolejnej iteracji).
- **Sekcja historii wizyt**: w tej wersji wyłącznie widoczny placeholder bez własnych danych ani operacji zapisu; docelowo zasilana przez przyszły moduł wizyt.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Rejestrator lub lekarz może założyć nową kartotekę pacjenta z kompletem danych podstawowych w mniej niż 2 minuty.
- **SC-002**: Uprawniony operator może zmienić stan pojedynczego zęba na schemacie szczęki w mniej niż 15 sekund od otwarcia kartoteki pacjenta.
- **SC-003**: 100% operacji utworzenia, odczytu, edycji i usunięcia danych kartoteki (w tym stanu uzębienia) posiada odpowiadający wpis w logu audytowym.
- **SC-004**: Personel odnajduje istniejącą kartotekę pacjenta po nazwisku lub numerze PESEL w mniej niż 10 sekund.
- **SC-005**: System odrzuca 100% prób założenia duplikatu kartoteki dla tego samego numeru PESEL.

## Assumptions

- Schemat uzębienia w tej wersji obejmuje standardowe uzębienie stałe osoby dorosłej (32 zęby, numeracja FDI/ISO 3950); uzębienie mleczne/mieszane dzieci jest poza zakresem tej specyfikacji ("teraz prosty schemat").
- Stan zęba w tej wersji jest binarny (zdrowy/chory), bez kolorów, opisów tekstowych ani słownika jednostek chorobowych — te elementy są świadomie odłożone przez użytkownika do kolejnej iteracji.
- Adres pacjenta przechowywany jest jako pola strukturalne (ulica, numer, kod pocztowy, miasto), zgodnie z typowym polskim formatem adresowym.
- Do danych podstawowych dodano pole "data urodzenia" (nieobecne w pierwotnym opisie), ponieważ przy opcjonalnym PESEL jest to jedyny praktyczny sposób odróżnienia pacjentów o tym samym imieniu i nazwisku oraz wspierania identyfikacji bez PESEL.
- Pełna funkcjonalność planowania/rezerwacji wizyt (kalendarz, przypomnienia, rezerwacja terminu) jest jawnie poza zakresem tej specyfikacji i zostanie opisana w osobnej, przyszłej specyfikacji modułu wizyt; sekcja historii wizyt w tej wersji jest wyłącznie placeholderem UI.
- Żądania usunięcia danych pacjenta (prawo do bycia zapomnianym) muszą być rozpatrywane z uwzględnieniem obowiązujących przepisów o wymaganym okresie retencji dokumentacji medycznej — nie skutkują natychmiastowym, bezwarunkowym usunięciem danych.
- **Zależność międzymodułowa**: ta specyfikacja wymaga rozszerzenia modelu RBAC ustalonego w module 001 (recepcja, lekarz, administrator) o nową rolę "asystent/asystentka" (FR-006a). Sam model konstytucyjny dopuszcza to rozszerzenie (Principle II wymienia role jako przykład — "e.g. recepcja, lekarz, administrator" — nie jako zamkniętą listę), ale wdrożona już implementacja modułu 001 będzie wymagała zmiany (dodania roli do systemu uwierzytelniania/autoryzacji) przed lub równolegle z implementacją tej specyfikacji. Należy to jawnie uwzględnić w `/speckit-plan`.
- Brak PESEL nie blokuje założenia kartoteki, ale oznacza brak automatycznej deduplikacji — ryzyko to jest świadomie akceptowane na tym etapie (patrz Edge Cases).
