# Feature Specification: Rejestracja i logowanie personelu z kontrolą dostępu opartą na rolach (RBAC)

**Feature Branch**: `001-staff-auth-rbac`

**Created**: 2026-08-16

**Status**: Draft

**Input**: User description: "Rejestracja i logowanie użytkowników systemu klinicznego z rolami: recepcja, lekarz, administrator. System musi wspierać uwierzytelnianie użytkowników personelu (nie pacjentów) z przypisaniem do jednej z trzech ról, kontrolę dostępu opartą na roli (RBAC) zgodnie z zasadą najmniejszych uprawnień (recepcja: zarządzanie wizytami i danymi kontaktowymi pacjentów; lekarz: dostęp do dokumentacji medycznej i historii leczenia przypisanych pacjentów; administrator: zarządzanie kontami użytkowników, konfiguracją systemu, brak domyślnego dostępu do danych klinicznych pacjentów). Każda operacja logowania i zmiana uprawnień musi być rejestrowana w logu audytowym zgodnie z zasadą pełnej audytowalności."

## Clarifications

### Session 2026-08-16

- Q: Czy logowanie personelu wymaga uwierzytelniania wieloskładnikowego (MFA), biorąc pod uwagę że system przetwarza dane szczególnej kategorii (RODO Art. 9)? → A: Wymagane dla wszystkich ról (recepcja, lekarz, administrator) od startu.
- Q: Jak personel odzyskuje dostęp po zapomnieniu hasła? → A: Self-service — link resetujący wysyłany na zarejestrowany e-mail służbowy.
- Q: Skąd system ma wiedzieć, którzy pacjenci są "przypisani" do danego lekarza na potrzeby ograniczenia dostępu (FR-014)? → A: ~~Zależność od przyszłej funkcji harmonogramu/kartoteki pacjenta~~ **UCHYLONE** — patrz korekta poniżej.
- Q: Czy potrzebny jest mechanizm awaryjnego dostępu ("break-glass") dla lekarza dyżurnego obsługującego pacjentów spoza własnego przypisania? → A: ~~Brak w v1~~ **UCHYLONE** — patrz korekta poniżej.
- Q: Czy dostęp lekarza do dokumentacji medycznej powinien być ograniczony do przypisanych mu pacjentów? → A: Nie — w praktyce klinicznej lekarze konsultują się nawzajem i przejmują pacjentów podczas nieobecności (urlop) innego lekarza, więc każdy lekarz MUSI mieć dostęp do dokumentacji medycznej i historii leczenia wszystkich pacjentów kliniki, nie tylko "przypisanych". Rola "lekarz" jako całość (a nie relacja lekarz–pacjent) jest granicą uprawnień. Koncepcja "przypisania lekarz–pacjent" i mechanizm break-glass stają się zbędne i zostają usunięte ze specyfikacji.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Logowanie personelu z dostępem zgodnym z rolą (Priority: P1)

Pracownik kliniki (recepcja, lekarz lub administrator) loguje się do systemu przy użyciu swoich danych uwierzytelniających i po zalogowaniu widzi wyłącznie funkcje i dane odpowiadające jego roli.

**Why this priority**: To jest fundament całego systemu — bez bezpiecznego logowania i egzekwowania ról żadna inna funkcja (kartoteki, harmonogram, rozliczenia) nie może zostać bezpiecznie udostępniona. Bez tego nie da się spełnić wymogu RODO dot. dostępu opartego na roli i zasady najmniejszych uprawnień.

**Independent Test**: Można w pełni przetestować, zakładając trzy konta testowe (recepcja, lekarz, administrator), logując się na każde z nich i weryfikując, że każde konto widzi wyłącznie funkcje/dane przypisane do swojej roli, a próba dostępu poza zakresem roli jest odrzucana.

**Acceptance Scenarios**:

1. **Given** aktywne konto z rolą "recepcja", **When** użytkownik loguje się poprawnymi danymi, **Then** system przyznaje dostęp do zarządzania wizytami i danymi kontaktowymi pacjentów, ale nie do dokumentacji medycznej.
2. **Given** aktywne konto z rolą "lekarz", **When** użytkownik loguje się poprawnymi danymi, **Then** system przyznaje dostęp do dokumentacji medycznej i historii leczenia wszystkich pacjentów kliniki (nie tylko własnych), aby umożliwić konsultacje i przekazywanie opieki między lekarzami.
3. **Given** aktywne konto z rolą "administrator", **When** użytkownik loguje się poprawnymi danymi, **Then** system przyznaje dostęp do zarządzania kontami i konfiguracją systemu, ale nie przyznaje domyślnego dostępu do danych klinicznych pacjentów.
4. **Given** dowolne konto, **When** użytkownik poda niepoprawne hasło, **Then** system odmawia dostępu i nie ujawnia, czy błędny był identyfikator czy hasło.
5. **Given** zalogowany użytkownik z rolą "recepcja", **When** próbuje uzyskać dostęp do zasobu zarezerwowanego dla roli "lekarz" (np. przez bezpośredni odnośnik), **Then** system odmawia dostępu i nie ujawnia treści zasobu.
6. **Given** dowolne konto z poprawnym hasłem, **When** użytkownik nie ukończy drugiego składnika uwierzytelniania (MFA), **Then** system nie przyznaje dostępu do systemu.
7. **Given** użytkownik, który zapomniał hasła, **When** zgłasza żądanie resetu poprzez swój zarejestrowany e-mail służbowy, **Then** otrzymuje link resetujący, po użyciu którego może ustawić nowe hasło i zalogować się nim.

---

### User Story 2 - Pełny log audytowy logowań i zmian uprawnień (Priority: P2)

Każda próba logowania (udana i nieudana) oraz każda zmiana roli/uprawnień użytkownika jest rejestrowana w niemodyfikowalnym logu audytowym, dostępnym do przeglądu przez uprawnione osoby.

**Why this priority**: Wymóg konstytucyjny "Full Auditability" — bez tego funkcja logowania/RBAC nie spełnia wymagań zgodności i nie może zostać uznana za ukończoną, ale sama w sobie nie blokuje podstawowego logowania (P1), więc może być dostarczona jako kolejny przyrost.

**Independent Test**: Można przetestować niezależnie, wykonując serię logowań (udanych/nieudanych) i zmian ról na koncie testowym administratora, a następnie weryfikując, że każde zdarzenie pojawia się w logu audytowym z poprawnym "kto/co/kiedy/przed-po", oraz że logu nie da się edytować ani usunąć przez normalne ścieżki aplikacji.

**Acceptance Scenarios**:

1. **Given** użytkownik loguje się poprawnie, **When** logowanie się powiedzie, **Then** w logu audytowym pojawia się wpis z identyfikatorem użytkownika, znacznikiem czasu i wynikiem "sukces".
2. **Given** użytkownik podaje błędne hasło, **When** logowanie się nie powiedzie, **Then** w logu audytowym pojawia się wpis z wynikiem "niepowodzenie" (bez zapisywania samego hasła).
3. **Given** administrator zmienia rolę użytkownika, **When** zmiana zostaje zapisana, **Then** w logu audytowym pojawia się wpis zawierający wykonawcę zmiany, docelowego użytkownika, poprzednią rolę i nową rolę.
4. **Given** istniejący wpis w logu audytowym, **When** dowolny użytkownik (włącznie z administratorem) próbuje go edytować lub usunąć przez interfejs aplikacji, **Then** system uniemożliwia tę operację.

---

### User Story 3 - Zarządzanie kontami personelu przez administratora (Priority: P3)

Administrator tworzy, dezaktywuje i reaktywuje konta personelu oraz przypisuje im role, bez uzyskiwania przy tym dostępu do danych klinicznych pacjentów.

**Why this priority**: Niezbędne do obsługi cyklu życia pracownika (zatrudnienie/odejście), ale system może zostać uruchomiony z niewielką liczbą kont utworzonych wstępnie (np. migracja/skrypt startowy), więc ta funkcja nie blokuje wartości dostarczanej przez P1/P2.

**Independent Test**: Można przetestować niezależnie, logując się jako administrator, tworząc nowe konto z przypisaną rolą, weryfikując że nowe konto może się zalogować z uprawnieniami danej roli, a następnie dezaktywując je i weryfikując, że zdezaktywowane konto nie może się już zalogować.

**Acceptance Scenarios**:

1. **Given** zalogowany administrator, **When** tworzy nowe konto personelu i przypisuje mu jedną z trzech ról, **Then** nowe konto może się zalogować i ma dostęp zgodny z przypisaną rolą.
2. **Given** zalogowany administrator, **When** dezaktywuje istniejące konto, **Then** to konto nie może się już zalogować, a próba logowania jest odnotowana w logu audytowym jako odmowa dostępu do konta nieaktywnego.
3. **Given** zalogowany administrator, **When** próbuje bezpośrednio otworzyć dokumentację medyczną lub historię leczenia pacjenta bez wcześniej przyznanego uprawnienia specjalnego, **Then** system odmawia dostępu.

---

### Edge Cases

- Co się dzieje, gdy użytkownik wielokrotnie poda błędne hasło z rzędu (ryzyko ataku brute-force)?
- Jak system obsługuje próbę logowania na konto już zdezaktywowane? (System nie udostępnia mechanizmu usuwania kont — wyłącznie dezaktywację/reaktywację, patrz FR-009.)
- Co się dzieje, gdy rola użytkownika zostanie zmieniona w trakcie jego aktywnej sesji? Rozwiązane: zmiana obowiązuje natychmiast — system unieważnia wszystkie aktywne sesje konta (FR-007a), a nie dopiero po ponownym zalogowaniu.
- Jak system obsługuje próbę dostępu do zasobu spoza zakresu roli poprzez bezpośredni adres/identyfikator zasobu (nie tylko przez UI)?
- Co się dzieje, gdy administrator próbuje dezaktywować własne jedyne konto administratorskie (ryzyko braku dostępu administracyjnego do systemu)? Rozwiązane: system odmawia takiej dezaktywacji (FR-009a).
- Jak system postępuje z sesją użytkownika, którego konto zostało dezaktywowane w trakcie trwania sesji?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUSI umożliwiać uwierzytelnianie wyłącznie użytkownikom personelu (recepcja, lekarz, administrator); pacjenci nie są użytkownikami tego mechanizmu logowania.
- **FR-002**: System MUSI wymagać unikalnego identyfikatora (np. adres e-mail) i hasła dla każdego konta personelu.
- **FR-003**: System MUSI przypisywać każdemu kontu personelu dokładnie jedną rolę spośród: recepcja, lekarz, administrator.
- **FR-004**: System MUSI egzekwować kontrolę dostępu opartą na roli (RBAC) zgodnie z zasadą najmniejszych uprawnień:
  - recepcja: zarządzanie wizytami oraz danymi kontaktowymi wszystkich pacjentów kliniki; brak dostępu do dokumentacji medycznej.
  - lekarz: dostęp do dokumentacji medycznej i historii leczenia wszystkich pacjentów kliniki (nie tylko własnych) — wspiera konsultacje między lekarzami i przekazywanie opieki nad pacjentem (np. podczas urlopu innego lekarza).
  - administrator: zarządzanie kontami użytkowników i konfiguracją systemu; brak domyślnego dostępu do danych klinicznych pacjentów.
- **FR-005**: System MUSI odmawiać dostępu do funkcji i danych spoza zakresu roli użytkownika, niezależnie od sposobu próby dostępu (interfejs, bezpośredni odnośnik, itp.), i nie ujawniać przy tym istnienia ani treści zasobu.
- **FR-006**: System MUSI rejestrować w logu audytowym każdą próbę logowania (udaną i nieudaną) wraz z identyfikatorem użytkownika (jeśli znany), znacznikiem czasu i wynikiem, bez zapisywania samego hasła w logu.
- **FR-007**: System MUSI rejestrować w logu audytowym każdą zmianę roli lub uprawnień użytkownika, wraz z wykonawcą zmiany, docelowym użytkownikiem, stanem przed i po zmianie oraz znacznikiem czasu.
- **FR-007a**: System MUSI natychmiast unieważniać wszystkie aktywne sesje konta, którego rola została zmieniona, wymuszając ponowne uwierzytelnienie z nowym zakresem uprawnień — zmiana roli nie może obowiązywać dopiero po naturalnym wygaśnięciu starej sesji.
- **FR-008**: Log audytowy MUSI być tylko-do-odczytu (append-only) — żaden wpis nie może zostać zmodyfikowany ani usunięty przez normalne ścieżki aplikacji, w tym przez administratora.
- **FR-009**: System MUSI umożliwiać administratorowi tworzenie, dezaktywację i reaktywację kont personelu oraz przypisywanie/zmianę ich roli.
- **FR-009a**: System MUSI odmówić dezaktywacji konta z rolą "administrator", jeśli byłoby to jedyne pozostałe aktywne konto administratorskie w systemie, i zarejestrować taką odrzuconą próbę w logu audytowym.
- **FR-010**: System MUSI odmawiać logowania na konto zdezaktywowane i rejestrować taką próbę w logu audytowym.
- **FR-011**: System MUSI blokować możliwość logowania po przekroczeniu progu kolejnych nieudanych prób logowania na to samo konto, w celu ochrony przed atakami typu brute-force, i rejestrować to zdarzenie w logu audytowym.
- **FR-012**: System MUSI kończyć sesję użytkownika po okresie bezczynności i wymagać ponownego uwierzytelnienia.
- **FR-013**: System MUSI szyfrować dane uwierzytelniające (hasła) w sposób uniemożliwiający ich odczytanie w postaci jawnej, zarówno w spoczynku, jak i w trakcie przesyłania.
- **FR-014**: System MUSI zapewniać każdemu kontu z rolą "lekarz" dostęp do dokumentacji medycznej i historii leczenia wszystkich pacjentów kliniki (nie tylko pacjentów danego lekarza), aby wspierać konsultacje między lekarzami i przekazywanie opieki nad pacjentem (np. podczas nieobecności/urlopu innego lekarza); granicą uprawnień jest sama rola "lekarz", a nie indywidualne przypisanie pacjenta do lekarza.
- **FR-015**: System MUSI wymagać drugiego składnika uwierzytelniania (MFA) przy logowaniu, dla każdej z trzech ról (recepcja, lekarz, administrator).
- **FR-016**: System MUSI udostępniać pracownikowi mechanizm samoobsługowego resetu hasła poprzez link wysyłany na jego zarejestrowany e-mail służbowy; link resetujący MUSI mieć ograniczony czas ważności i MUSI zostać unieważniony po jednorazowym użyciu.
- **FR-017**: System MUSI rejestrować w logu audytowym każde żądanie resetu hasła oraz jego wynik (sukces/niepowodzenie/wygaśnięcie linku).

### Key Entities *(include if feature involves data)*

- **Konto użytkownika (personelu)**: reprezentuje pracownika kliniki uprawnionego do logowania; zawiera unikalny identyfikator, dane uwierzytelniające, przypisaną rolę oraz status (aktywne/nieaktywne).
- **Rola**: jedna z trzech predefiniowanych ról (recepcja, lekarz, administrator), determinująca zakres dostępnych funkcji i danych.
- **Sesja**: reprezentuje aktywne, uwierzytelnione połączenie użytkownika z systemem; posiada czas rozpoczęcia i wygaśnięcia.
- **Wpis logu audytowego**: niemodyfikowalny rekord zdarzenia (logowanie, nieudane logowanie, zmiana roli, odmowa dostępu) zawierający wykonawcę, obiekt zdarzenia, znacznik czasu oraz stan przed/po (gdy dotyczy).
- **Token resetu hasła**: jednorazowy, ograniczony czasowo token powiązany z kontem użytkownika, używany do weryfikacji żądania samoobsługowego resetu hasła.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Pracownik może zalogować się do systemu i zobaczyć ekran startowy zgodny ze swoją rolą w mniej niż 10 sekund od podania poprawnych danych.
- **SC-002**: 100% prób dostępu do funkcji/danych spoza zakresu roli użytkownika kończy się odmową dostępu — brak jakichkolwiek wyjątków w testach zgodności RBAC.
- **SC-003**: 100% zdarzeń logowania (udanych i nieudanych) oraz zmian ról jest widoczne w logu audytowym w ciągu 1 minuty od zdarzenia.
- **SC-004**: Żaden wpis w logu audytowym nie może zostać zmieniony ani usunięty w 100% przeprowadzonych prób takiej operacji przez dowolną rolę, włącznie z administratorem.
- **SC-005**: Nowe konto personelu utworzone przez administratora jest w pełni funkcjonalne (możliwość zalogowania z właściwym zakresem dostępu) w mniej niż 2 minuty od utworzenia.
- **SC-006**: Liczba incydentów nieautoryzowanego dostępu do danych klinicznych (dostęp spoza przypisanej roli) wynosi zero w okresie testów akceptacyjnych.
- **SC-007**: Pracownik, który zapomniał hasła, jest w stanie samodzielnie odzyskać dostęp do konta (od żądania resetu do udanego zalogowania nowym hasłem) w mniej niż 5 minut, bez interwencji administratora.

## Assumptions

- Klinika korzysta z pojedynczej, scentralizowanej bazy kont personelu (brak wymogu integracji z zewnętrznym dostawcą tożsamości/SSO w tej wersji funkcji).
- Próg blokady konta po nieudanych próbach logowania: 5 kolejnych nieudanych prób skutkuje czasową blokadą na 15 minut (standardowa praktyka branżowa dla aplikacji przetwarzających dane wrażliwe).
- Czas bezczynności prowadzący do wygaśnięcia sesji: 15 minut, zgodnie ze standardową praktyką dla aplikacji medycznych przetwarzających dane szczególnej kategorii (RODO Art. 9).
- Niezależnie od bezczynności, sesja wygasa twardo (bez możliwości przedłużenia) po 8 godzinach od momentu zalogowania, odpowiadając długości jednej zmiany roboczej personelu klinicznego — standardowa praktyka branżowa jako dodatkowe zabezpieczenie na wypadek pozostawienia niezablokowanego urządzenia przez cały dzień pracy.
- Konkretna metoda drugiego składnika MFA (aplikacja TOTP, kod SMS, e-mail) nie jest przesądzona w tej specyfikacji i zostanie doprecyzowana na etapie planowania jako decyzja techniczna.
- Link resetujący hasło ma czas ważności 30 minut (standardowa praktyka branżowa), po czym wygasa i wymaga ponownego żądania.
- Wszyscy lekarze i wszyscy pracownicy recepcji należą do jednej kliniki (jednej lokalizacji/placówki) współdzielącej wspólny zespół opieki; stąd granicą uprawnień jest rola, a nie indywidualne przypisanie do pacjenta. Jeśli w przyszłości klinika obejmie wiele niezależnych placówek/zespołów, ograniczenie dostępu per-placówka będzie wymagało osobnej decyzji o zakresie (nowa funkcja lub zmiana tej specyfikacji).
