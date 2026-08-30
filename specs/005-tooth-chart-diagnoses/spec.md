# Feature Specification: Interaktywny odontogram z rozpoznaniami i powierzchniami zębów

**Feature Branch**: `claude/teeth-visualization-model-6fhj1g` (katalog funkcji: `specs/005-tooth-chart-diagnoses`)

**Created**: 2026-08-30

**Status**: Draft

## Clarifications

### Session 2026-08-30

- Q: Czy odontogram ma obsługiwać trzecią warstwę wpisów — "plan leczenia" (proponowane zabiegi) —
  obok rozpoznań i stanu istniejącego? → A: Nie. Dwie warstwy: rozpoznanie i stan istniejący. Plan
  leczenia w całości należy do przyszłego modułu wizyt/planu leczenia, gdzie i tak potrzebne są
  terminy i kosztorys.
- Q: Czy rola ASSISTANT ma stracić prawo zapisu, które dziś ma na binarnym stanie zęba (FR-006a z
  `002-patient-records`)? → A: Nie — po konsultacji z lekarzem ASSISTANT ma ten sam zakres zapisu co
  DOCTOR, łącznie z odnotowaniem rozpoznania chorobowego. Rozliczalność zapewnia zapis autora przy
  każdym wpisie oraz dziennik audytu, a nie zawężenie roli. Jest to świadoma różnica względem
  `004-patient-medical-history`, gdzie ASSISTANT ma wyłącznie odczyt.
- Q: Czy słownik jednostek chorobowych jest zamknięty i wersjonowany razem z aplikacją, czy
  ADMINISTRATOR może go edytować w czasie działania systemu? → A: Zamknięty i wersjonowany z
  aplikacją, bez interfejsu administracyjnego, ale z zapasową pozycją "inne rozpoznanie",
  wymagającą opisu tekstowego — elastyczność przy fotelu bez utraty kontroli nad danymi
  referencyjnymi.

### Session 2026-08-30 (druga tura)

- Q: Czy potrzebna jest migracja istniejących danych binarnego stanu uzębienia z
  `002-patient-records`? → A: Nie. System nie jest wdrożony produkcyjnie i nie istnieją dane
  pacjentów do przeniesienia — binarny model stanu zęba jest zastępowany, a nie migrowany. Sekcja
  wymagań migracyjnych została usunięta.
- Q: Czy operator ma móc zaznaczyć naraz kilka zębów **i** kilka części zęba, a potem jednym
  zapisem określić ich stan? → A: Tak — zaznaczenie wielokrotne obejmuje zarówno zęby, jak i
  powierzchnie/kanały w ich obrębie, ze skrótami "cała ćwiartka", "cały łuk", "odcinek przedni"
  i zaznaczaniem przeciągnięciem (FR-004a..FR-004c).
- Q: Czy odontogram ma pokazywać liczbę kanałów korzeniowych? → A: Tak. Każdy ząb ma domyślną,
  podręcznikową liczbę kanałów widoczną bez dodatkowej interakcji, a operator może ją zmienić dla
  konkretnego pacjenta (nowa sekcja wymagań I, FR-063..FR-069).
- Q: Jak mają wyglądać zęby na łuku? → A: Anatomicznie rozpoznawalne sylwetki właściwe dla typu
  zęba (siekacz, kieł, przedtrzonowiec, trzonowiec), z koroną i korzeniami, nie prostokąty
  (FR-001a).

### Session 2026-08-30 (czwarta tura — po przeglądzie mockupu)

- Q: Jak ma wyglądać szybka ścieżka dla asystentki notującej rozpoznania dyktowane przez lekarza?
  → A: Menu kontekstowe wywoływane prawym przyciskiem myszy albo przytrzymaniem palca na zębie
  lub na strefie powierzchni, z najczęstszymi jednostkami chorobowymi, brakami zębowymi i
  uzupełnieniami. Wybór pozycji zapisuje wpis od razu, bez otwierania formularza, i działa też na
  zaznaczeniu wielu zębów (FR-020a, FR-020b).
- Q: Czy zaznaczone powierzchnie na głównym diagramie mają być opisane literą? → A: Nie. Litera
  jest zbędna — wystarczy wypełnienie kolorem i wyróżniona ramka pola. Oznaczenia literowe
  pozostają wyłącznie na powiększonej mapie powierzchni w panelu szczegółów, gdzie objaśniają
  układ stref (FR-029a).

### Session 2026-08-30 (trzecia tura — po przeglądzie mockupu)

- Q: Czy strefy powierzchni na głównym diagramie mają być klikalne także na ekranach dotykowych,
  mimo że przy widoku całego uzębienia są mniejsze niż 44 px? → A: Tak — klikanie powierzchni
  wprost z diagramu ma działać na każdym urządzeniu. Trafialność zapewnia powiększenie diagramu
  (2× i 3×), przy którym strefy przekraczają najpierw próg WCAG 2.5.8 AA, a potem 44 px
  (FR-029a, FR-029b, FR-049).
- Q: Gdzie mają być prezentowane powierzchnie zęba na głównym diagramie? → A: Poza koroną — w
  osobnym, powiększonym schemacie powierzchni umieszczonym w środkowym pasie diagramu, między
  łukami. Sylwetki zębów mają być mniejsze, bo ich rolą jest anatomia, a nie nośnik danych
  (FR-029).
- Q: Czy kanał korzeniowy ma stan kliniczny? → A: Tak, dokładnie trzy stany: `do leczenia`
  (czerwony), `wyleczony / wypełniony kanałowo` (zielony) oraz `niedoleczony — do ponownego
  leczenia` (zielony na całej długości z czerwonym wierzchołkiem). Kanały muszą być na diagramie
  wyraźnie widoczne (FR-066, FR-066a).
- Q: Czy ząb ma z góry ustaloną liczbę kanałów? → A: Nie. Żadna pozycja nie ma domyślnych kanałów;
  pierwsze kanały zakłada lekarz lub asystentka dopiero w trakcie leczenia. Tabela wartości
  domyślnych usunięta; typowa anatomia pozostaje wyłącznie niezapisywaną podpowiedzią przy
  dodawaniu (FR-063, FR-064).

**Input**: User description: "Interaktywny model wizualizacji zębów (diagram stomatologiczny /
odontogram): dwa łuki zębowe z klikalnymi zębami, w którym lekarz oznacza który ząb jest chory,
wybiera z listy szczegółową jednostkę chorobową (rozpoznanie) oraz wyklikuje konkretną
część/powierzchnię zęba, której dotyczy zmiana. Ma to być kompletne, praktyczne UX-owo i
estetyczne UI, uwzględniające wszystkie przypadki."

## Kontekst i relacja do już zbudowanych funkcji

Feature `002-patient-records` dostarczył **minimalny** schemat uzębienia: 32 zęby stałe w notacji
FDI/ISO 3950, jeden binarny stan na ząb (`HEALTHY` / `SICK`), bez powierzchni, bez słownika
rozpoznań i bez opisu. Jego `spec.md` (sekcja Assumptions, wiersze o schemacie uzębienia) jawnie
odkłada na później: kolory, opisy tekstowe, słownik jednostek chorobowych oraz uzębienie
mleczne/mieszane. **Ta specyfikacja realizuje dokładnie ten odłożony zakres** i zastępuje binarny
model stanu zęba modelem opartym na wpisach klinicznych (rozpoznaniach).

Zależności w obie strony:

- `002-patient-records` — istniejący diagram, endpointy stanu uzębienia, role DOCTOR/ASSISTANT,
  wpisy audytu `TOOTH_CHART_VIEWED` / `TOOTH_STATE_CHANGED`, eksport i usuwanie danych (RODO).
  System nie jest wdrożony produkcyjnie i nie ma danych pacjentów, więc binarny model stanu zęba
  jest **zastępowany, a nie migrowany** — migracja danych jest poza zakresem tej specyfikacji.
- `003-brand-ui-theme` — paleta marki, tryb jasny/ciemny, audyt kontrastu w testach jednostkowych,
  istniejące tokeny `tooth-healthy-*`, `tooth-diseased-*`, `tooth-selected-stroke`. Nowe stany
  zęba wymagają nowych tokenów w tym samym systemie.
- `004-patient-medical-history` — wzorzec dwóch niezależnych statusów na wpisie (kliniczny +
  techniczny "aktualny/nieaktualny" dla korekt) oraz model append-only. Ta specyfikacja stosuje
  ten sam wzorzec dla spójności. Alergie i choroby przewlekłe z 004 pozostają źródłem alertów
  ogólnoustrojowych — odontogram ich nie duplikuje.

### Mockup UI (przed `/speckit-plan`)

`mockup/odontogram-mockup.html` to klikalny prototyp interfejsu, uzgodniony z użytkownikiem przed
planowaniem. Jest samodzielnym dokumentem HTML bez zależności zewnętrznych poza krojami pisma —
otwiera się wprost z repozytorium, bez serwera ani budowania. Nie jest kodem produkcyjnym ani kontraktem — jest wizualnym odpowiednikiem wymagań z
sekcji A, C, G oraz I i służy jako referencja dla `/speckit-plan`. Odwzorowuje: dwa łuki z
anatomicznymi sylwetkami zębów (FR-001a), zaznaczanie wielu zębów i wielu części
(FR-004a..FR-004c), słownik z zakresami anatomicznymi (FR-011..FR-021), mapę powierzchni
(FR-024..FR-026), kanały dodawane ręcznie z trzema stanami leczenia (FR-063..FR-066a), warstwy i legendę
(FR-008, FR-009), tryb jasny/ciemny na tokenach z `003-brand-ui-theme` (FR-051) klikanie
powierzchni wprost z diagramu z powiększeniem 1×/2×/3× (FR-029a, FR-029b, FR-049) oraz szybkie
menu kontekstowe pod prawym przyciskiem i przytrzymaniem (FR-020a, FR-020b). Przełącznik „Wymagania" w pasku
górnym pokazuje przy elementach interfejsu numery wymagań, których dotyczą.

Moduł pozostaje w klasyfikacji **high-risk "patient records"** (Principle V) i przetwarza dane
szczególnej kategorii wg RODO Art. 9.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Lekarz odnotowuje rozpoznanie na konkretnej powierzchni zęba (Priority: P1)

Lekarz podczas badania widzi ubytek próchnicowy na powierzchni żującej zęba 36. Otwiera zakładkę
uzębienia w kartotece pacjenta, dotyka zęba 36 na diagramie, w panelu szczegółów wybiera ze
słownika rozpoznanie "próchnica zębiny", klika powierzchnię żującą na powiększonym schemacie zęba,
opcjonalnie dopisuje notatkę i zapisuje. Diagram natychmiast pokazuje ząb 36 jako chory, z
oznaczeniem wskazującym dokładnie tę jedną powierzchnię.

**Why this priority**: To sedno prośby użytkownika i jedyny element, bez którego funkcja nie ma
sensu — cała reszta (odczyt, korekty, stany specjalne, uzębienie mleczne) jest nadbudową nad tym
jednym przepływem. Dostarcza natychmiastową wartość kliniczną: precyzyjny zapis lokalizacji zmiany
zamiast dzisiejszego "ząb chory/zdrowy".

**Independent Test**: Można w pełni przetestować logując się jako DOCTOR, otwierając kartotekę
pacjenta z domyślnie zdrowym uzębieniem, dodając jedno rozpoznanie powierzchniowe na jednym zębie
i weryfikując, że po odświeżeniu strony diagram i panel szczegółów pokazują ten sam ząb, to samo
rozpoznanie i tę samą powierzchnię — bez implementacji korekt, stanów specjalnych czy uzębienia
mlecznego.

**Acceptance Scenarios**:

1. **Given** kartoteka pacjenta z uzębieniem bez odnotowanych zmian, **When** lekarz otwiera
   zakładkę uzębienia, **Then** widzi oba łuki zębowe (górny i dolny) z kompletem zębów w stanie
   "bez odnotowanych zmian" oraz czytelny komunikat, że dla tego pacjenta nie odnotowano dotąd
   żadnych rozpoznań.
2. **Given** otwarty odontogram, **When** lekarz dotyka zęba 36, **Then** ząb zostaje wizualnie
   zaznaczony, a otwarty panel szczegółów pokazuje numer FDI, nazwę anatomiczną zęba ("pierwszy
   trzonowiec dolny lewy"), powiększony schemat jego powierzchni, domyślną liczbę kanałów
   korzeniowych właściwą dla tej pozycji oraz listę dotychczasowych wpisów (pustą).
3. **Given** wybrany ząb 36, **When** lekarz wybiera ze słownika rozpoznanie o zakresie
   powierzchniowym (np. "próchnica zębiny"), **Then** system wymaga wskazania co najmniej jednej
   powierzchni i nie pozwala zapisać wpisu bez tego wskazania.
4. **Given** wybrane rozpoznanie powierzchniowe i kliknięta powierzchnia żująca, **When** lekarz
   zapisuje wpis, **Then** wpis pojawia się na liście wpisów zęba 36 z datą, autorem, nazwą
   rozpoznania i oznaczeniem powierzchni, a ząb 36 na diagramie zmienia wygląd na "z aktywnym
   rozpoznaniem".
5. **Given** zapisany wpis, **When** lekarz odświeża stronę lub inny uprawniony użytkownik otwiera
   tę samą kartotekę, **Then** widzi identyczny stan diagramu i identyczną treść wpisu.
6. **Given** wybrany ząb sieczny (np. 11), **When** lekarz otwiera schemat powierzchni,
   **Then** widzi powierzchnię sieczną zamiast żującej — system nie oferuje powierzchni żującej
   dla siekaczy i kłów.
7. **Given** wybrane rozpoznanie o zakresie "cały ząb" (np. "zapalenie miazgi"), **When** lekarz
   otwiera formularz, **Then** wybór powierzchni jest niedostępny/ukryty, a zapis nie wymaga
   wskazania powierzchni.

---

### User Story 2 - Lekarz odczytuje pełny obraz uzębienia z jednego widoku (Priority: P2)

Lekarz przed zabiegiem otwiera odontogram, żeby w kilka sekund zorientować się, które zęby wymagają
uwagi, czego dotyczą zmiany i co już zostało zaopatrzone. Korzysta z legendy, przełącza warstwy
(rozpoznania / stan istniejący), najeżdża lub dotyka zęba, żeby zobaczyć skrót, i otwiera pełną
historię wpisów wybranego zęba.

**Why this priority**: Zapisywanie danych bez czytelnego odczytu jest bezużyteczne klinicznie, ale
odczyt sam w sobie nie ma czego pokazywać, dopóki US1 nie działa — stąd P2 bezpośrednio po US1.

**Independent Test**: Można przetestować na kartotece z przygotowanym zestawem wpisów (kilka
rozpoznań na różnych zębach i powierzchniach), sprawdzając, że diagram, legenda, filtr warstw i
historia zęba prezentują je poprawnie — bez korzystania z formularza dodawania.

**Acceptance Scenarios**:

1. **Given** uzębienie z rozpoznaniami na kilku zębach, **When** lekarz otwiera zakładkę,
   **Then** widzi oba łuki naraz, a zęby z aktywnymi rozpoznaniami są odróżnialne od pozostałych
   zarówno kolorem, jak i cechą nieopartą na kolorze (wzór/symbol/obrys).
2. **Given** otwarty odontogram, **When** lekarz otwiera legendę, **Then** widzi objaśnienie
   każdego użytego oznaczenia (stan zęba, warstwa, symbol powierzchni) w języku polskim.
3. **Given** ząb z wpisem powierzchniowym na powierzchni mezjalnej, **When** lekarz patrzy na
   diagram, **Then** oznaczenie znajduje się po stronie mezjalnej schematu tego zęba, a nie na
   całym zębie.
4. **Given** ząb z wieloma wpisami, **When** lekarz otwiera panel tego zęba, **Then** widzi
   wszystkie aktywne wpisy uporządkowane od najnowszego, a wpisy zakończone i nieaktualne są
   ukryte pod jawnym rozwinięciem "historia zęba".
5. **Given** włączony filtr warstwy "tylko rozpoznania", **When** lekarz patrzy na diagram,
   **Then** oznaczenia stanu istniejącego (np. wypełnienia) są wygaszone, a widoczne pozostają
   wyłącznie aktywne rozpoznania.
6. **Given** kartoteka bez żadnych wpisów, **When** lekarz otwiera zakładkę, **Then** system
   pokazuje czytelny stan pusty, a nie pusty ekran ani błąd.

---

### User Story 3 - Lekarz koryguje wpis i zamyka rozpoznanie po leczeniu (Priority: P2)

Lekarz zaopatrzył ubytek na zębie 36 — musi oznaczyć rozpoznanie jako zakończone i odnotować
powstałe wypełnienie. Osobno: zauważa, że wcześniej pomylił powierzchnię przy innym zębie i
poprawia wpis. Żadna z tych operacji nie może skasować śladu po poprzednim stanie.

**Why this priority**: Bez zamykania rozpoznań odontogram po kilku wizytach przestaje odzwierciedlać
rzeczywistość i traci wiarygodność. Bez ścieżki korekty lekarz zostaje z błędnym zapisem w
dokumentacji medycznej, której nie wolno nadpisywać po cichu (Principle III).

**Independent Test**: Można przetestować na kartotece z jednym istniejącym rozpoznaniem: zamknąć je,
sprawdzić że ząb przestaje być oznaczony jako "z aktywnym rozpoznaniem", a wpis jest nadal widoczny
w historii zęba ze statusem "zakończone"; następnie skorygować inny wpis i sprawdzić, że w historii
widoczne są obie wersje z powiązaniem korekta → wpis korygowany.

**Acceptance Scenarios**:

1. **Given** ząb z aktywnym rozpoznaniem, **When** lekarz oznacza rozpoznanie jako zakończone
   (wyleczone/zaopatrzone), **Then** wpis otrzymuje status kliniczny "zakończone" wraz z datą
   zakończenia, a ząb przestaje być prezentowany jako mający aktywne rozpoznanie.
2. **Given** wpis oznaczony jako zakończony, **When** lekarz otwiera historię zęba, **Then** wpis
   jest tam widoczny wraz z datą rozpoznania i datą zakończenia.
3. **Given** wpis z błędnie wskazaną powierzchnią, **When** lekarz go koryguje, **Then** system
   zapisuje nowy wpis oznaczony jako korekta poprzedniego, a poprzedni oznacza jako nieaktualny —
   bez fizycznego usunięcia lub nadpisania treści.
4. **Given** wpis oznaczony jako nieaktualny, **When** lekarz przegląda listę bieżących wpisów zęba,
   **Then** wpis nieaktualny nie jest tam pokazywany, ale pozostaje dostępny w historii zęba.
5. **Given** dowolna operacja zapisu, zamknięcia lub korekty, **When** operacja się powiedzie,
   **Then** w dzienniku audytu powstaje wpis zawierający kto, co, kiedy oraz stan przed i po.
6. **Given** wpis utworzony przez innego lekarza, **When** bieżący lekarz go koryguje, **Then**
   historia zęba pokazuje obu autorów przy odpowiednich wersjach wpisu.

---

### User Story 4 - Lekarz odnotowuje braki zębowe i stany nie-chorobowe (Priority: P3)

Pacjent ma usunięty ząb 46, implant w miejscu 16, koronę protetyczną na zębie 24 i zatrzymany ząb
38. Lekarz musi móc to wszystko odwzorować na diagramie, bo zmienia to zarówno obraz kliniczny, jak
i to, jakie rozpoznania w ogóle mają sens dla danej pozycji.

**Why this priority**: Odontogram bez braków zębowych i uzupełnień protetycznych jest w realnej
praktyce niekompletny, ale rozpoznania chorobowe (US1) są warunkiem koniecznym i muszą powstać
pierwsze.

**Independent Test**: Można przetestować oznaczając na kartotece testowej po jednej pozycji z każdego
stanu specjalnego i weryfikując, że diagram odróżnia je wizualnie od zębów zdrowych i chorych oraz
że dla pozycji "ząb usunięty" system nie pozwala dodać rozpoznania powierzchniowego.

**Acceptance Scenarios**:

1. **Given** ząb obecny, **When** lekarz oznacza go jako usunięty (z datą, jeśli znana), **Then**
   pozycja na diagramie jest prezentowana jako brak zęba, wyraźnie odróżnialny od zęba zdrowego i
   od zęba chorego.
2. **Given** pozycja oznaczona jako brak zęba, **When** lekarz próbuje dodać do niej rozpoznanie o
   zakresie powierzchniowym, **Then** system odmawia i wyjaśnia dlaczego, natomiast dopuszcza wpisy
   właściwe dla bezzębnej pozycji (np. stan po ekstrakcji, implant, przęsło mostu).
3. **Given** pozycja z brakiem zęba, **When** lekarz oznacza w niej implant z koroną, **Then**
   diagram pokazuje ją jako pozycję zaopatrzoną implantologicznie, a nie jako ząb naturalny.
4. **Given** ząb zatrzymany lub niewyrznięty, **When** lekarz go tak oznaczy, **Then** pozycja jest
   odróżnialna od pozycji z brakiem zęba, ponieważ ząb fizycznie istnieje.
5. **Given** ząb z leczeniem kanałowym i koroną protetyczną, **When** lekarz oznaczy oba stany,
   **Then** oba są widoczne jednocześnie na diagramie i na liście wpisów zęba — jeden nie zastępuje
   drugiego.

---

### User Story 5 - Lekarz pracuje na uzębieniu mlecznym i mieszanym (Priority: P3)

Do gabinetu przychodzi siedmioletnie dziecko: część zębów mlecznych już wypadła, część stałych się
wyrzyna. Lekarz musi odnotować próchnicę na zębie mlecznym 74 i jednocześnie widzieć wyrznięte zęby
stałe.

**Why this priority**: Rozszerza grupę pacjentów, których da się obsłużyć, ale nie jest potrzebne do
działania funkcji dla pacjentów dorosłych (dominująca część praktyki i całość dzisiejszych danych z
`002`).

**Independent Test**: Można przetestować na kartotece pacjenta z datą urodzenia wskazującą wiek
dziecięcy: sprawdzić, że domyślnie widoczne jest uzębienie mleczne lub mieszane, że da się dodać
rozpoznanie na zębie mlecznym w numeracji 51–85 i że przełączenie trybu uzębienia nie kasuje
istniejących wpisów.

**Acceptance Scenarios**:

1. **Given** pacjent z datą urodzenia wskazującą wiek poniżej 6 lat, **When** lekarz otwiera
   odontogram, **Then** domyślnie prezentowane jest uzębienie mleczne (20 pozycji, numeracja FDI
   51–55, 61–65, 71–75, 81–85).
2. **Given** pacjent w wieku 6–13 lat, **When** lekarz otwiera odontogram, **Then** domyślnie
   prezentowane jest uzębienie mieszane — pozycje mleczne i stałe równocześnie, wizualnie
   rozróżnione.
3. **Given** dowolny pacjent, **When** lekarz ręcznie przełącza tryb uzębienia (mleczne / mieszane /
   stałe), **Then** widok się zmienia, wybór jest zapisywany na kartotece pacjenta, a żaden
   istniejący wpis nie zostaje usunięty ani ukryty bez ostrzeżenia.
4. **Given** ząb mleczny z odnotowanym rozpoznaniem, **When** ten ząb zostaje oznaczony jako
   wypadnięty/usunięty, a na jego pozycji pojawia się ząb stały, **Then** wpisy zęba mlecznego
   pozostają dostępne w historii i nie są przypisywane do zęba stałego.
5. **Given** widok uzębienia mieszanego, **When** lekarz patrzy na diagram, **Then** zęby mleczne są
   jednoznacznie odróżnialne od stałych (rozmiar/oznaczenie/numeracja), a nie tylko numerem.

---

### User Story 6 - Operator oznacza stan wielu zębów i wielu części naraz (Priority: P3)

Lekarz stwierdza kamień nazębny i zapalenie dziąseł w całym odcinku przednim dolnym. Zamiast
sześciokrotnie powtarzać ten sam formularz, zaznacza kilka zębów naraz i przypisuje im wspólne
rozpoznanie jednym zapisem.

**Why this priority**: Znacząco skraca realną pracę przy fotelu i zmniejsza liczbę pominięć, ale
każdy z tych zapisów da się wykonać pojedynczo przez przepływ z US1 — więc jest to optymalizacja, a
nie warunek działania.

**Uwaga**: zaznaczenie wielokrotne obejmuje zarówno zęby, jak i części zęba — operator może wskazać
np. sześć zębów przednich i w każdym z nich powierzchnię przedsionkową, a następnie zapisać jeden
wspólny stan (FR-004a..FR-004c).

**Independent Test**: Można przetestować zaznaczając wielokrotnie kilka zębów, przypisując im jedno
rozpoznanie o zakresie przyzębia i weryfikując, że powstało tyle odrębnych wpisów, ile zaznaczono
zębów, a każdy da się później skorygować niezależnie.

**Acceptance Scenarios**:

1. **Given** otwarty odontogram, **When** lekarz włącza tryb zaznaczania wielokrotnego i dotyka
   kilku zębów, **Then** wszystkie są widocznie zaznaczone, a licznik pokazuje ich liczbę.
2. **Given** zaznaczonych kilka zębów, **When** lekarz wybiera rozpoznanie i zapisuje, **Then**
   powstaje osobny wpis dla każdego zaznaczonego zęba, każdy z własną historią i możliwością
   niezależnej korekty.
3. **Given** zaznaczenie obejmujące pozycję z brakiem zęba i rozpoznanie niedopuszczalne dla takiej
   pozycji, **When** lekarz zapisuje, **Then** system zapisuje wpisy dla pozycji dopuszczalnych,
   jawnie wymienia pominięte pozycje wraz z powodem i nie przerywa całej operacji bez informacji.
4. **Given** rozpoznanie o zakresie powierzchniowym w trybie wielokrotnym, **When** lekarz wskazuje
   powierzchnie, **Then** ten sam zestaw powierzchni jest stosowany do każdego zaznaczonego zęba, z
   pominięciem powierzchni nieistniejących dla danego typu zęba, i lekarz jest o tym poinformowany
   przed zapisem.
5. **Given** otwarty odontogram, **When** lekarz używa skrótu "odcinek przedni górny" albo
   przeciąga palcem po sąsiadujących zębach, **Then** wszystkie objęte zęby zostają zaznaczone
   jednym gestem, a licznik pokazuje ich liczbę.
6. **Given** zaznaczenie wielu zębów, **When** lekarz odznacza jeden z nich, **Then** pozostałe
   zaznaczenie zostaje nienaruszone.

---

### Edge Cases

**Dane i model uzębienia**

- Co się dzieje, gdy pacjent ma dorosły komplet 32 zębów, ale brakuje mu zębów ósmych? Pozycje 18,
  28, 38, 48 muszą dać się oznaczyć jako brak wrodzony/nieobecny bez traktowania tego jako choroby.
- Jak system zachowuje się przy zębach nadliczbowych (hiperdoncja), których notacja FDI nie
  obejmuje w podstawowym zakresie? (Patrz Assumptions — poza zakresem tej wersji, obsługiwane
  wyłącznie jako notatka opisowa przy najbliższym zębie.)
- Co się dzieje, gdy lekarz wskaże powierzchnię, która dla danego typu zęba nie istnieje (np.
  powierzchnia żująca dla siekacza)? System nie może takiej powierzchni w ogóle zaoferować.
- Co się dzieje, gdy dwa różne rozpoznania dotyczą tej samej powierzchni tego samego zęba (np.
  próchnica wtórna przy istniejącym wypełnieniu)? Muszą współistnieć jako odrębne wpisy, a diagram
  musi zasygnalizować, że powierzchnia ma więcej niż jeden wpis.
- Co się dzieje, gdy ząb ma tyle wpisów, że nie da się ich zmieścić na schemacie? Diagram pokazuje
  wskaźnik "wiele wpisów", a pełna lista jest w panelu zęba.
- Co się dzieje, gdy pierwszy trzonowiec górny ma czwarty kanał (MB2)? Operator podnosi liczbę
  kanałów z domyślnych 3 na 4, a ząb zostaje oznaczony jako mający liczbę kanałów inną niż
  domyślna.
- Co się dzieje, gdy operator zmniejsza liczbę kanałów zęba, który ma wpisy przypisane do
  usuwanych kanałów? System ostrzega przed zapisem, a wpisy zostają w historii zęba oznaczone jako
  dotyczące kanału nieobecnego w bieżącym modelu — nic nie znika po cichu.
- Co się dzieje przy zaznaczeniu wielu zębów o różnej liczbie kanałów i wskazaniu kanału trzeciego?
  Wpis powstaje tylko dla zębów, które ten kanał mają, a pominięte pozycje są jawnie wymienione
  przed zapisem.
- Co się dzieje, gdy zaznaczenie wielokrotne obejmuje zęby mleczne i stałe naraz? Zestaw części
  jest stosowany tam, gdzie istnieje, a różnice są wymienione przed zapisem — operacja nie jest
  blokowana w całości.
- Co się dzieje z wpisami zęba mlecznego po jego wymianie na stały? Zostają w historii pozycji,
  jawnie przypisane do zęba mlecznego (patrz US5 scenariusz 4).

**Uprawnienia i bezpieczeństwo**

- Co się dzieje, gdy użytkownik z rolą RECEPTION próbuje otworzyć zakładkę uzębienia? Dostęp musi
  być odmówiony, bez ujawnienia treści klinicznej ani samego faktu istnienia rozpoznań.
- Co się dzieje, gdy ASSISTANT dodaje, koryguje lub zamyka rozpoznanie? Operacja jest dozwolona w
  tym samym zakresie co dla roli DOCTOR (FR-057), ale wpis MUST odnotować autora wraz z rolą, w
  jakiej działał (FR-058), tak aby dokumentacja pozwalała odtworzyć, kto postawił rozpoznanie.
- Co się dzieje, gdy lekarz potrzebuje odnotować zmianę, której nie ma w słowniku? Wybiera pozycję
  "inne rozpoznanie", podaje opis i wskazuje zakres anatomiczny; wpis jest oznaczony jako
  wymagający doprecyzowania (FR-011a) i pozostaje wyszukiwalny po rozszerzeniu słownika.
- Co się dzieje, gdy ADMINISTRATOR otworzy kartotekę? Nie ma rutynowego wglądu w treść kliniczną
  odontogramu; ewentualny dostęp serwisowy jest audytowany tak samo jak każdy inny.
- Co się dzieje, gdy sesja użytkownika wygaśnie w trakcie wypełniania formularza rozpoznania?
  System nie może po cichu porzucić niezapisanej treści — musi ją zachować do czasu ponownego
  zalogowania albo jasno ostrzec przed jej utratą.

**Współbieżność, błędy i stany brzegowe interfejsu**

- Co się dzieje, gdy dwóch lekarzy edytuje odontogram tego samego pacjenta jednocześnie? Zapis
  oparty na nieaktualnym stanie musi zostać wykryty i zakończyć się czytelnym komunikatem z
  możliwością przeładowania aktualnego stanu, nigdy cichym nadpisaniem cudzej zmiany.
- Co się dzieje, gdy zapis nie powiedzie się z powodu błędu sieci lub serwera? Interfejs nie może
  pokazać zmiany jako zapisanej; musi zgłosić błąd i pozwolić ponowić próbę bez ponownego
  wypełniania formularza.
- Co się dzieje przy bardzo wolnym łączu? Diagram musi mieć czytelny stan ładowania, a nie pusty
  obszar sugerujący brak zębów.
- Co się dzieje, gdy pacjent nie istnieje albo kartoteka została objęta procedurą usunięcia danych
  (RODO)? Zakładka uzębienia musi zachować się identycznie jak reszta kartoteki.
- Co się dzieje na ekranie 320 px szerokości? Oba łuki muszą pozostać obsługiwalne, cel dotknięcia
  każdego zęba nie może być mniejszy niż 44×44 px, a strona nie może przewijać się poziomo jako
  całość.
- Co się dzieje, gdy użytkownik korzysta wyłącznie z klawiatury lub czytnika ekranu? Każdy ząb i
  każda powierzchnia muszą być osiągalne i opisane słownie, bez polegania na wskazaniu myszą.
- Co się dzieje w trybie ciemnym oraz przy druku/eksporcie do dokumentacji? Oznaczenia muszą
  pozostać rozróżnialne, w tym w skali szarości.
- Co się dzieje, gdy lekarz cofnie się z formularza bez zapisu? Niezapisane zmiany muszą wymagać
  potwierdzenia porzucenia.

## Requirements *(mandatory)*

### Functional Requirements

#### A. Diagram — struktura i nawigacja

- **FR-001**: System MUST prezentować odontogram jako **dwa łuki zębowe** — górny (szczęka) i dolny
  (żuchwa) — widoczne jednocześnie w jednym widoku, bez konieczności przełączania między nimi.
- **FR-001a**: System MUST rysować każdy ząb jako anatomicznie rozpoznawalną sylwetkę właściwą dla
  jego typu (siekacz, kieł, przedtrzonowiec, trzonowiec, trzonowiec mleczny), z widoczną koroną i
  korzeniami w liczbie odpowiadającej pozycji — nie jako jednolity prostokąt, kółko ani samą
  etykietę z numerem. Zęby górne i dolne MUST być zwrócone koronami do siebie, korzeniami na
  zewnątrz łuku, tak jak wyglądają w zwarciu. Rysunek korzeni MUST
  pozostać czytelny przy domyślnym powiększeniu — grubość obrysu i kontrast wypełnienia MUST
  wystarczać, by policzyć korzenie i odczytać stan kanałów bez powiększania diagramu.
- **FR-002**: System MUST rozmieszczać zęby w konwencji klinicznej: prawa strona pacjenta po lewej
  stronie ekranu, obie połowy łuku odbite względem linii pośrodkowej, oraz MUST oznaczać ćwiartki
  (1–4 dla uzębienia stałego, 5–8 dla mlecznego) etykietami czytelnymi dla użytkownika.
- **FR-003**: System MUST używać notacji FDI/ISO 3950 jako jedynej numeracji prezentowanej
  użytkownikowi: 11–18, 21–28, 31–38, 41–48 (stałe) oraz 51–55, 61–65, 71–75, 81–85 (mleczne).
- **FR-004**: System MUST umożliwiać wybór pojedynczego zęba jednym kliknięciem/dotknięciem i MUST
  wyraźnie oznaczać ząb aktualnie wybrany.
- **FR-004a**: System MUST umożliwiać jednoczesne zaznaczenie **wielu zębów oraz wielu części
  zęba** (powierzchni i/lub kanałów) i przypisanie im jednego stanu w jednym zapisie; wskazany
  zestaw części MUST być stosowany do każdego zaznaczonego zęba, z pominięciem części, które dla
  danego zęba nie istnieją, i z jawną informacją o tych pominięciach przed zapisem.
- **FR-004b**: System MUST udostępniać skróty zaznaczania obejmujące co najmniej: całą ćwiartkę,
  cały łuk (górny/dolny), odcinek przedni i odcinki boczne, oraz zaznaczanie przeciągnięciem po
  sąsiadujących zębach; MUST też pozwalać odznaczyć pojedynczy ząb bez utraty pozostałego
  zaznaczenia.
- **FR-004c**: System MUST pokazywać licznik zaznaczonych zębów i części, MUST utrzymywać
  zaznaczenie przy otwieraniu i zamykaniu panelu szczegółów oraz MUST wymagać jawnego wyczyszczenia
  zaznaczenia — zaznaczenie nie może znikać jako efekt uboczny innej czynności.
- **FR-005**: System MUST wyświetlać dla wybranego zęba panel szczegółów zawierający: numer FDI,
  polską nazwę anatomiczną zęba, powiększony schemat jego powierzchni, listę aktywnych wpisów oraz
  dostęp do historii zęba.
- **FR-006**: System MUST prezentować panel szczegółów w sposób nieprzysłaniający diagramu na
  ekranach szerokich (widok obok siebie) oraz jako warstwę wysuwaną na ekranach wąskich, przy czym
  na żadnym z nich wybrany ząb nie może pozostać niewidoczny.
- **FR-007**: System MUST umożliwiać zamknięcie panelu i odznaczenie zęba bez skutku ubocznego dla
  zapisanych danych.
- **FR-008**: System MUST udostępniać zawsze dostępną legendę objaśniającą każde używane oznaczenie
  (stan pozycji, warstwa wpisu, symbolika powierzchni) w języku polskim.
- **FR-009**: System MUST umożliwiać filtrowanie widoku po warstwie wpisu, przy czym warstwy są
  dokładnie dwie — `rozpoznanie` i `stan istniejący` — plus opcja "wszystkie"; filtrowanie MUST NOT
  modyfikować danych. Trzecia warstwa "plan leczenia" jest jawnie poza zakresem tej specyfikacji
  (patrz Clarifications, Assumptions).
- **FR-010**: System MUST pokazywać na diagramie wskaźnik "wiele wpisów" dla zęba, którego wpisów
  nie da się jednocześnie odwzorować na schemacie.

#### B. Słownik jednostek chorobowych

- **FR-011**: System MUST udostępniać predefiniowany, zamknięty słownik jednostek chorobowych i
  stanów zęba, wersjonowany i dostarczany razem z aplikacją; użytkownik wybiera pozycję z tego
  słownika, a nie wpisuje rozpoznania z wolnej ręki. Słownik MUST NOT być edytowalny przez
  użytkowników aplikacji, w tym ADMINISTRATORA — jego zmiana przechodzi przez ten sam pipeline, co
  każda inna zmiana w systemie (Principle VI).
- **FR-011a**: Słownik MUST zawierać zapasową pozycję "inne rozpoznanie", dla której system MUST
  wymagać opisu tekstowego, i MUST oznaczać wpisy z tą pozycją jako wymagające doprecyzowania, tak
  aby dało się je odnaleźć i uzupełnić po rozszerzeniu słownika. Pozycja "inne rozpoznanie" MUST
  wymagać jawnego wskazania zakresu anatomicznego (FR-021) przez użytkownika, ponieważ nie wynika
  on ze słownika.
- **FR-012**: Każda pozycja słownika MUST mieć: unikalny kod techniczny, nazwę w języku polskim,
  kategorię, zakres anatomiczny (patrz FR-021) oraz — tam, gdzie istnieje odpowiednik —
  przypisany kod ICD-10.
- **FR-013**: System MUST umożliwiać wyszukiwanie pozycji słownika po fragmencie nazwy i po kodzie,
  z wynikami zawężanymi w miarę pisania.
- **FR-014**: System MUST grupować pozycje słownika w kategorie, obejmujące co najmniej: choroby
  twardych tkanek zęba, choroby miazgi i tkanek okołowierzchołkowych, urazy i pęknięcia, ubytki
  niepróchnicowego pochodzenia, choroby przyzębia i tkanek miękkich, zaburzenia wyrzynania i braki
  zębowe, stany po leczeniu / uzupełnienia.
- **FR-015**: Słownik MUST zawierać co najmniej następujące pozycje kliniczne (nazwy robocze,
  ostateczne brzmienie ustala plan):
  - próchnica początkowa (plamka próchnicowa), próchnica szkliwa, próchnica zębiny, próchnica
    głęboka, próchnica wtórna (przy istniejącym wypełnieniu), próchnica korzenia,
  - zapalenie miazgi odwracalne, zapalenie miazgi nieodwracalne, martwica miazgi,
  - zapalenie tkanek okołowierzchołkowych (ostre/przewlekłe), ropień okołowierzchołkowy, torbiel
    korzeniowa,
  - złamanie korony, złamanie korzenia, pęknięcie zęba, odłamanie fragmentu korony,
  - starcie patologiczne (atrycja), abrazja, abfrakcja, erozja, nadwrażliwość zębiny,
  - zapalenie dziąsła, zapalenie przyzębia, recesja dziąsłowa, kamień nazębny, kieszonka
    przyzębna,
  - ząb zatrzymany, ząb niewyrznięty, nieprawidłowe ustawienie/rotacja, wrodzony brak zęba
    (agenezja),
  - ząb usunięty (stan po ekstrakcji),
  - wypełnienie (istniejące), wypełnienie tymczasowe, uszczelnienie bruzd (lak), leczenie kanałowe
    (stan po), wkład koronowo-korzeniowy, korona protetyczna, licówka, przęsło mostu, implant,
    ząb filarowy protezy.
- **FR-016**: System MUST oznaczać w słowniku, które pozycje są rozpoznaniami chorobowymi, a które
  opisują stan istniejący / uzupełnienie, i MUST używać tego rozróżnienia do warstw z FR-009 oraz
  do wyliczenia stanu pozycji na diagramie.
- **FR-017**: System MUST umożliwiać dodanie do wpisu opcjonalnej notatki tekstowej lekarza
  (ograniczonej co do długości), która nie zastępuje wyboru pozycji słownikowej.
- **FR-018**: System MUST umożliwiać — dla pozycji słownika, które tego wymagają — wskazanie
  stopnia/nasilenia zmiany z zamkniętej listy właściwej dla tej pozycji (np. próchnica: szkliwa /
  zębiny / głęboka; zapalenie przyzębia: stopień I–IV).
- **FR-019**: System MUST wersjonować słownik tak, aby zmiana lub wycofanie pozycji nie zmieniała
  treści wpisów już zapisanych w dokumentacji medycznej.
- **FR-020**: System MUST prezentować listę wyboru rozpoznania w sposób użyteczny przy fotelu:
  najczęściej używane pozycje dostępne bez wyszukiwania, pełna lista dostępna zawsze.
- **FR-020a**: System MUST udostępniać **szybkie menu kontekstowe** wywoływane bezpośrednio na
  diagramie — prawym przyciskiem myszy oraz przytrzymaniem palca (długie dotknięcie) — na sylwetce
  zęba i na strefie powierzchni. Menu MUST zawierać co najmniej: pozycje ostatnio używane,
  najczęstsze rozpoznania powierzchniowe (gdy wskazano powierzchnie), najczęstsze rozpoznania
  obejmujące cały ząb i przyzębie, braki zębowe oraz uzupełnienia, a także przejście do pełnego
  formularza. Wybór pozycji MUST zapisywać wpis natychmiast, bez otwierania formularza.
  Uzasadnienie: typowy przebieg pracy to lekarz dyktujący rozpoznanie i asystentka notująca je w
  kartotece — droga „zaznacz powierzchnie → menu → pozycja" musi być krótsza niż wypełnianie
  formularza.
- **FR-020b**: Szybkie menu MUST działać na zaznaczeniu wielu zębów: wywołane na zębie należącym
  do zaznaczenia MUST stosować wybraną pozycję do wszystkich zaznaczonych zębów, jawnie informując
  o zakresie działania przed wyborem oraz o pozycjach pominiętych po zapisie. Wywołanie menu
  MUST NOT zmieniać istniejącego zaznaczenia. Każdy zapis z menu MUST oferować natychmiastowe
  cofnięcie, realizowane jako korekta zgodna z FR-033 (wpis nie znika, staje się nieaktualny).

#### C. Powierzchnie i zakres anatomiczny

- **FR-021**: Każda pozycja słownika MUST deklarować zakres anatomiczny należący do zamkniętego
  zbioru: `powierzchnia zęba`, `cały ząb`, `korzeń / tkanki okołowierzchołkowe`, `przyzębie wokół
  zęba`.
- **FR-022**: System MUST wymagać wskazania co najmniej jednej powierzchni dla pozycji o zakresie
  `powierzchnia zęba` i MUST uniemożliwiać zapis bez tego wskazania.
- **FR-023**: System MUST nie oferować wyboru powierzchni dla pozycji o pozostałych zakresach.
- **FR-024**: System MUST obsługiwać pięć powierzchni zęba: mezjalna, dystalna, przedsionkowa
  (wargowa/policzkowa), językowa lub podniebienna, oraz — zależnie od typu zęba — żująca
  (przedtrzonowce i trzonowce) albo sieczna (siekacze i kły).
- **FR-025**: System MUST nazywać powierzchnię językową "podniebienna" dla zębów górnych i
  "językowa" dla dolnych oraz "wargowa" / "policzkowa" odpowiednio dla zębów przednich i bocznych.
- **FR-026**: System MUST prezentować powierzchnie jako klikalne obszary powiększonego schematu
  zęba, rozmieszczone zgodnie z ich rzeczywistym położeniem anatomicznym względem linii
  pośrodkowej.
- **FR-027**: System MUST umożliwiać wskazanie wielu powierzchni w jednym wpisie (np. ubytek
  mezjalno-okluzyjny) oraz odznaczenie już wskazanej powierzchni przed zapisem.
- **FR-028**: System MUST umożliwiać — dla pozycji o zakresie `korzeń / tkanki okołowierzchołkowe`
  — wskazanie konkretnego korzenia lub konkretnego kanału korzeniowego w zębach
  wielokorzeniowych/wielokanałowych (patrz sekcja I).
- **FR-029**: System MUST prezentować powierzchnie na głównym diagramie **poza sylwetką zęba** —
  jako osobny, powiększony schemat powierzchni umieszczony w środkowym pasie diagramu, między
  łukiem górnym a dolnym, w jednej kolumnie z odpowiadającym mu zębem. Sylwetka zęba niesie
  wyłącznie anatomię (typ zęba, korzenie, kanały) i MUST NOT być używana jako nośnik oznaczeń
  powierzchniowych; korona MUST być na tyle mała, żeby schemat powierzchni pozostał dominującym
  elementem odczytu.
- **FR-029a**: Schemat powierzchni w widoku łuku MUST być klikalny na **każdym rodzaju urządzenia
  wskazującego** — myszą, piórem i dotykiem — a wskazanie powierzchni bezpośrednio na diagramie
  MUST zaznaczać odpowiedni ząb i tę powierzchnię, bez konieczności wchodzenia najpierw w panel
  szczegółów. Każda strefa MUST mieć podpowiedź z nazwą powierzchni dostępną przed kliknięciem, a
  strefa zaznaczona MUST być jednoznacznie odróżnialna zarówno od stref pustych, jak i od stref z
  odnotowanym wpisem — samo pogrubienie obrysu nie wystarcza. Oznaczenia literowe powierzchni
  (M/D/B/L/O/I) MUST NOT być rysowane na głównym diagramie; ich miejscem jest powiększona mapa
  powierzchni w panelu szczegółów, gdzie objaśniają układ stref.
- **FR-029b**: Ponieważ przy widoku całego uzębienia pojedyncza strefa powierzchni jest mniejsza
  niż minimalny cel dotknięcia z FR-049, system MUST udostępniać powiększenie diagramu (co
  najmniej dwa stopnie ponad widok domyślny), przy którym każda strefa osiąga co najmniej 24×24 px
  (WCAG 2.5.8, poziom AA), a na najwyższym stopniu 44×44 px. Powiększony diagram MUST przewijać
  się poziomo wewnątrz własnego kontenera (nigdy jako cała strona, FR-049), MUST utrzymywać
  zaznaczony ząb w polu widzenia po zmianie powiększenia i MUST NOT być warunkiem korzystania z
  funkcji — pełna, powiększona mapa powierzchni w panelu szczegółów pozostaje równorzędną drogą
  wskazania powierzchni.

#### D. Cykl życia wpisu

- **FR-030**: System MUST traktować każdy wpis odontogramu jako **append-only** — treść zapisanego
  wpisu nie może być nadpisywana ani usuwana przez zwykłe przepływy aplikacji, także przez
  ADMINISTRATORA.
- **FR-031**: Każdy wpis MUST mieć dwa niezależne statusy, zgodnie z wzorcem z
  `004-patient-medical-history`: status kliniczny (`aktywne` / `zakończone`) oraz status techniczny
  korekty (`aktualny` / `nieaktualny`).
- **FR-032**: System MUST umożliwiać zamknięcie rozpoznania (przejście na `zakończone`) z datą
  zakończenia i opcjonalną informacją, czym zostało zaopatrzone.
- **FR-033**: System MUST realizować korektę wpisu przez utworzenie nowego wpisu wskazującego wpis
  korygowany, przy jednoczesnym oznaczeniu poprzedniego jako `nieaktualny`.
- **FR-034**: System MUST domyślnie pokazywać na liście wpisów zęba wyłącznie wpisy `aktualne`;
  wpisy `zakończone` i `nieaktualne` MUST być dostępne pod jawnym rozwinięciem "historia zęba".
- **FR-035**: Każdy wpis MUST przechowywać i prezentować datę rozpoznania oraz autora (użytkownika,
  który go utworzył).
- **FR-036**: System MUST pozwalać na datę rozpoznania wcześniejszą niż dzisiejsza (uzupełnianie
  dokumentacji), ale MUST odrzucać daty z przyszłości oraz wcześniejsze niż data urodzenia
  pacjenta.
- **FR-037**: System MUST wyliczać prezentowany na diagramie stan każdej pozycji z jej aktualnych
  wpisów, a nie przechowywać go jako niezależnie edytowalne pole.

#### E. Braki zębowe i stany specjalne

- **FR-038**: System MUST umożliwiać oznaczenie pozycji jako: ząb obecny, ząb usunięty, wrodzony
  brak zęba, ząb niewyrznięty/zatrzymany.
- **FR-039**: System MUST wizualnie odróżniać pozycję z brakiem zęba od zęba zdrowego oraz od zęba
  z rozpoznaniem, w sposób nieoparty wyłącznie na kolorze.
- **FR-040**: System MUST blokować dodawanie wpisów o zakresie `powierzchnia zęba` do pozycji
  oznaczonej jako brak zęba i MUST wyjaśniać przyczynę odmowy.
- **FR-041**: System MUST dopuszczać dla pozycji z brakiem zęba wpisy właściwe dla takiej pozycji
  (implant, przęsło mostu, ząb filarowy protezy, stan po ekstrakcji).
- **FR-042**: System MUST umożliwiać współistnienie na jednym zębie wielu wpisów z różnych warstw
  (np. leczenie kanałowe + korona protetyczna + próchnica wtórna) bez wzajemnego zastępowania.

#### F. Uzębienie mleczne i mieszane

- **FR-043**: System MUST obsługiwać trzy tryby uzębienia: stałe (32 pozycje), mleczne (20 pozycji),
  mieszane (pozycje stałe i mleczne równocześnie).
- **FR-044**: System MUST proponować tryb domyślny na podstawie wieku pacjenta wyliczonego z daty
  urodzenia i MUST umożliwiać lekarzowi jawne nadpisanie tego wyboru.
- **FR-045**: System MUST zapisywać wybrany tryb uzębienia na kartotece pacjenta, tak aby kolejne
  otwarcie pokazało ten sam widok.
- **FR-046**: System MUST wizualnie odróżniać zęby mleczne od stałych w widoku uzębienia mieszanego
  w sposób nieoparty wyłącznie na numeracji.
- **FR-047**: Zmiana trybu uzębienia MUST NOT usuwać ani modyfikować żadnego istniejącego wpisu;
  jeżeli zmiana ukrywa pozycje mające wpisy, system MUST ostrzec o tym przed zastosowaniem zmiany.

#### G. Interfejs, estetyka, dostępność, mobile-first

- **FR-048**: System MUST projektować odontogram mobile-first (Principle IV): pełna obsługa
  wszystkich przepływów tej specyfikacji na ekranie o szerokości 320 px, z progresywnym
  wzbogaceniem dla tabletu i desktopu.
- **FR-049**: Wybór zęba i wybór jego schematu powierzchni MUST mieć obszar dotknięcia o wymiarach
  co najmniej 44×44 px niezależnie od rozmiaru rysunku. Dla pojedynczych stref powierzchni w
  widoku całego uzębienia obowiązuje próg WCAG 2.5.8 (24×24 px) osiągany przez powiększenie z
  FR-029b. Jeżeli którykolwiek z tych wymogów wymaga przewijania poziomego, MUST ono odbywać się
  wewnątrz kontenera diagramu, nigdy jako przewijanie całej strony.
- **FR-050**: System MUST rozróżniać wszystkie stany pozycji i warstwy wpisów w sposób nieoparty
  wyłącznie na kolorze (kształt, wzór, obrys lub symbol), tak aby diagram pozostał czytelny w skali
  szarości i dla osób z zaburzeniami widzenia barw.
- **FR-051**: System MUST obsługiwać tryb jasny i ciemny wprowadzone w `003-brand-ui-theme`,
  definiując wszystkie nowe kolory odontogramu jako tokeny w istniejącym systemie tokenów marki,
  objęte istniejącym audytem kontrastu.
- **FR-052**: System MUST zapewniać pełną obsługę klawiaturą: przechodzenie między zębami klawiszami
  kierunkowymi, otwarcie panelu i wybór powierzchni bez użycia wskaźnika, oraz widoczny wskaźnik
  fokusu.
- **FR-053**: System MUST udostępniać czytnikom ekranu tekstowy opis każdej pozycji zawierający
  numer FDI, nazwę anatomiczną i skrót stanu (np. "ząb 36, pierwszy trzonowiec dolny lewy,
  próchnica zębiny powierzchni żującej").
- **FR-054**: System MUST prezentować odrębne, czytelne stany: ładowania, pusty (brak odnotowanych
  zmian), błędu odczytu i błędu zapisu — żaden z nich nie może objawiać się pustym obszarem.
- **FR-055**: System MUST wymagać potwierdzenia przed porzuceniem niezapisanego formularza wpisu.
- **FR-056**: System MUST potwierdzać każdy udany zapis komunikatem zwrotnym widocznym bez
  przewijania.

#### H. Uprawnienia, audyt, RODO

- **FR-057**: System MUST zezwalać na odczyt, tworzenie, korygowanie i zamykanie wpisów
  odontogramu — łącznie z rozpoznaniami chorobowymi — rolom DOCTOR oraz ASSISTANT, w tym samym
  zakresie dla obu ról. Zachowuje to uprawnienia przyznane roli ASSISTANT przez FR-006a z
  `002-patient-records` i odzwierciedla faktyczny podział pracy przy fotelu.
- **FR-058**: Ponieważ zakres zapisu obu ról jest identyczny, rozliczalność MUST opierać się na
  zapisie autora przy każdym wpisie (FR-035) oraz na dzienniku audytu (FR-060), a nie na zawężeniu
  uprawnień. Każdy wpis MUST jednoznacznie wskazywać, czy jego autor działał jako DOCTOR czy
  ASSISTANT. **Uwaga**: jest to świadoma różnica względem `004-patient-medical-history`, gdzie
  ASSISTANT ma wyłącznie odczyt — różnicę należy odnotować w `/speckit-plan` i w przeglądzie
  bezpieczeństwa wymaganym przed merge.
- **FR-059**: System MUST odmawiać roli RECEPTION jakiegokolwiek dostępu do odontogramu — zarówno do
  treści klinicznej, jak i do informacji o istnieniu rozpoznań.
- **FR-060**: System MUST zapisywać w niezmienialnym dzienniku audytu każdą operację odczytu i
  zapisu odontogramu (kto, co, kiedy, a dla zapisów stan przed i po), zgodnie z Principle III.
- **FR-061**: System MUST obejmować dane odontogramu eksportem danych pacjenta (RODO — prawo
  dostępu) w formie czytelnej dla pacjenta, tj. z nazwami rozpoznań i powierzchni, nie samymi
  kodami.
- **FR-062**: System MUST obejmować dane odontogramu procedurą usuwania danych pacjenta na tych
  samych zasadach i z tym samym uwzględnieniem ustawowych okresów retencji dokumentacji medycznej,
  co pozostałe dane kartoteki.

#### I. Kanały korzeniowe

- **FR-063**: System MUST NOT zakładać żadnych kanałów korzeniowych z góry. Każda pozycja zębowa
  zaczyna bez odnotowanych kanałów, a brak kanałów oznacza wyłącznie "nie odnotowano", nigdy
  "ząb nie ma kanałów". Pierwszy kanał zakłada użytkownik dopiero wtedy, gdy leczenie tego
  wymaga.
- **FR-064**: System MAY podpowiadać typową dla danej pozycji FDI liczbę i nazwy kanałów w
  momencie dodawania, ale ta podpowiedź MUST NOT być zapisywana jako dane pacjenta ani
  prezentowana jako stan faktyczny dopóki użytkownik jej nie zatwierdzi.
- **FR-065**: System MUST umożliwiać rolom DOCTOR i ASSISTANT dodanie kanału (do 6 na ząb),
  nadanie mu nazwy anatomicznej (np. policzkowy bliższy, policzkowy dalszy, podniebienny,
  MB2) oraz usunięcie omyłkowo dodanego kanału, z zapisem autora i daty każdej z tych operacji.
- **FR-066**: Każdy kanał MUST mieć dokładnie jeden z trzech stanów leczenia:
  1. **do leczenia** — kanał rozpoznany, leczenie jeszcze nie wykonane,
  2. **wyleczony** — kanał wypełniony kanałowo, leczenie zakończone,
  3. **niedoleczony** — wypełniony niekompletnie, wymaga ponownego leczenia.
- **FR-066a**: Stany kanałów MUST być czytelne wprost z głównego diagramu, wewnątrz sylwetek
  korzeni, w następującym kodowaniu: `do leczenia` — czerwony na całej długości; `wyleczony` —
  zielony na całej długości; `niedoleczony` — zielony na całej długości z czerwonym odcinkiem
  przywierzchołkowym. Kanały MUST być rysowane wyraźnie (grubiej niż linie pomocnicze rysunku), a
  każdy stan MUST nieść dodatkowy sygnał nieoparty na kolorze (kreskowanie, znacznik wierzchołka),
  zgodnie z FR-050.
- **FR-067**: Wpis o zakresie `korzeń / tkanki okołowierzchołkowe` MAY dotyczyć całego korzenia
  bez wskazania kanału (np. zapalenie tkanek okołowierzchołkowych); jeżeli wskazuje kanał, MUST to
  być kanał, który na tym zębie faktycznie istnieje. Brak odnotowanych kanałów MUST NOT blokować
  zapisu takiego wpisu.
- **FR-068**: Usunięcie kanału MUST NOT usuwać ani ukrywać wpisów do niego przypisanych; system
  MUST ostrzec przed operacją i zachować takie wpisy w historii zęba, jawnie oznaczone jako
  dotyczące kanału już nieobecnego w modelu.
- **FR-069**: Dodanie, usunięcie i zmiana stanu kanału MUST być traktowane jak każda inna zmiana
  danych klinicznych: append-only, z wpisem w dzienniku audytu zawierającym stan przed i po
  (FR-030, FR-060).

#### J. Współbieżność, wydajność, odporność

- **FR-070**: System MUST wykrywać próbę zapisu opartego na nieaktualnym stanie odontogramu i
  MUST zwracać czytelną informację z możliwością przeładowania, zamiast cicho nadpisywać zmianę
  innego użytkownika.
- **FR-071**: System MUST zachować niezapisaną treść formularza przy nieudanym zapisie, tak aby
  ponowienie próby nie wymagało wypełniania go od nowa.
- **FR-072**: System MUST prezentować zmianę zaznaczenia zęba i wskazania powierzchni natychmiast,
  niezależnie od czasu trwania zapisu na serwerze.
- **FR-073**: Moduł MUST pozostać w granicach modułu wysokiego ryzyka "kartoteka pacjenta"
  (Principle V) i nie może współdzielić domeny awarii z modułami niższego poziomu ryzyka.

### Key Entities *(include if feature involves data)*

- **Odontogram pacjenta (ToothChart)**: zbiór pozycji zębowych jednej kartoteki wraz z wybranym
  trybem uzębienia. Nie przechowuje stanu zęba wprost — stan wynika z wpisów (FR-037).
- **Pozycja zębowa (ToothPosition)**: konkretne miejsce w łuku identyfikowane numerem FDI, z
  informacją o obecności zęba (obecny / usunięty / wrodzony brak / niewyrznięty), o tym, czy
  dotyczy uzębienia stałego czy mlecznego, oraz o liczbie kanałów korzeniowych (domyślnej lub
  skorygowanej przez operatora). Determinuje zbiór dostępnych powierzchni (FR-024) i kanałów
  (FR-063).
- **Kanał korzeniowy (RootCanal)**: pojedynczy kanał w obrębie pozycji zębowej, z nazwą
  anatomiczną i opcjonalnym stanem leczenia kanałowego. Może być celem wpisu o zakresie
  `korzeń / tkanki okołowierzchołkowe` (FR-067).
- **Wpis odontogramu (ToothFinding)**: pojedyncza obserwacja kliniczna przypisana do pozycji
  zębowej. Atrybuty: pozycja słownika, warstwa (rozpoznanie / stan istniejący), zbiór powierzchni
  albo część anatomiczna (w tym konkretny kanał), opcjonalny stopień nasilenia, opcjonalna notatka, data rozpoznania, data
  zakończenia, autor, status kliniczny, status korekty, odniesienie do wpisu korygowanego.
- **Pozycja słownika rozpoznań (DiagnosisCatalogEntry)**: wersjonowana definicja jednostki
  chorobowej lub stanu: kod, nazwa polska, kategoria, zakres anatomiczny, warstwa, opcjonalny kod
  ICD-10, opcjonalna lista dopuszczalnych stopni nasilenia, reguły dopuszczalności dla pozycji z
  brakiem zęba i dla zębów mlecznych.
- **Powierzchnia zęba (ToothSurface)**: mezjalna, dystalna, przedsionkowa, językowa/podniebienna,
  żująca albo sieczna — z zależnością od typu zęba (FR-024, FR-025).
- **Historia zęba**: uporządkowany chronologicznie zbiór wszystkich wpisów danej pozycji, łącznie z
  zakończonymi i nieaktualnymi, wraz z powiązaniami korekta → wpis korygowany.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Lekarz odnotowuje kompletne rozpoznanie powierzchniowe (wybór zęba → wybór jednostki
  chorobowej → wskazanie powierzchni → zapis) w nie więcej niż 4 interakcjach i poniżej 20 sekund od
  otwarcia zakładki uzębienia.
- **SC-002**: 100% prób dostępu roli RECEPTION do odontogramu kończy się odmową bez ujawnienia
  treści klinicznej ani faktu istnienia rozpoznań; jednocześnie 100% wpisów utworzonych przez role
  uprawnione (DOCTOR, ASSISTANT) daje się przypisać do konkretnego autora wraz z rolą, w jakiej
  działał.
- **SC-003**: 100% operacji zapisu i odczytu odontogramu ma odpowiadający wpis w dzienniku audytu z
  kompletem: kto, co, kiedy oraz stan przed i po (dla zapisów).
- **SC-004**: Na ekranie 320×568 px oba łuki zębowe są dostępne, a każdy ząb daje się wskazać bez
  powiększania strony; strona nie przewija się poziomo jako całość.
- **SC-005**: Każdy stan pozycji i każda warstwa wpisu pozostają rozróżnialne po konwersji widoku do
  skali szarości — 100% oznaczeń niesie informację niezależną od koloru.
- **SC-006**: Wszystkie nowe kolory odontogramu spełniają w obu motywach progi kontrastu przyjęte w
  `003-brand-ui-theme` (co najmniej 3:1 dla elementów graficznych, 4.5:1 dla tekstu), potwierdzone
  automatycznym audytem uruchamianym w tym samym miejscu, co dotychczasowy.
- **SC-007**: 100% przepływów tej specyfikacji (wybór zęba, wybór rozpoznania, wskazanie
  powierzchni, zapis, korekta, zamknięcie wpisu) da się wykonać wyłącznie klawiaturą.
- **SC-008**: Żadna zmiana zapisana w odontogramie nie daje się usunąć ani nadpisać przez interfejs
  aplikacji — 100% korekt tworzy nową wersję wpisu z zachowaniem poprzedniej.
- **SC-009**: W teście z udziałem co najmniej 5 lekarzy 90% uczestników poprawnie odczytuje
  znaczenie wszystkich oznaczeń na przykładowym odontogramie, korzystając wyłącznie z legendy
  dostępnej w interfejsie i bez wcześniejszego szkolenia.
- **SC-010**: Równoległa edycja odontogramu tego samego pacjenta przez dwóch użytkowników nigdy nie
  skutkuje cichą utratą zmiany — 100% konfliktów kończy się komunikatem i możliwością przeładowania.
- **SC-011**: Odontogram w pełnym trybie mieszanym (52 pozycje) reaguje na wskazanie zęba widoczną
  zmianą zaznaczenia w czasie poniżej 0,1 s, niezależnie od czasu zapisu na serwerze.
- **SC-012**: Stan każdego odnotowanego kanału (do leczenia / wyleczony / niedoleczony) jest
  odczytywalny wprost z głównego diagramu, bez otwierania panelu zęba i bez polegania na samym
  kolorze; dodanie pierwszego kanału do zęba wymaga nie więcej niż 3 interakcji.
- **SC-013**: Odnotowanie tego samego stanu na sześciu zębach odcinka przedniego wymaga jednego
  zapisu i nie więcej niż 1/3 liczby interakcji potrzebnych przy oznaczaniu każdego zęba osobno.

## Assumptions

- **Zakres tej specyfikacji to dokumentowanie stanu klinicznego, nie planowanie leczenia ani
  rozliczenia.** Kosztorysy, procedury NFZ/cenniki i kalendarz wizyt pozostają poza zakresem —
  moduł wizyt ma powstać w osobnej specyfikacji (patrz `002-patient-records`, Assumptions).
- Numeracja prezentowana użytkownikowi to wyłącznie FDI/ISO 3950; notacje Palmera i uniwersalna
  (1–32) są poza zakresem tej wersji.
- Zęby nadliczbowe (hiperdoncja) są poza zakresem tej wersji — odnotowuje się je jako notatkę przy
  najbliższym zębie; pełne wsparcie wymaga osobnej decyzji o notacji.
- Zaawansowany periodontogram (pomiary głębokości kieszonek w sześciu punktach na ząb, krwawienie,
  ruchomość, furkacje) jest poza zakresem — choroby przyzębia odnotowuje się na poziomie zęba.
- Załączanie zdjęć RTG i fotografii wewnątrzustnych do wpisów jest poza zakresem tej wersji.
- Słownik rozpoznań jest dostarczany z aplikacją jako dane referencyjne i wersjonowany razem z nią;
  jego pozycje nie są edytowalne przez użytkowników aplikacji, a ewentualne braki pokrywa zapasowa
  pozycja "inne rozpoznanie" z obowiązkowym opisem (FR-011a). Ekran administracyjny do zarządzania
  słownikiem jest poza zakresem tej wersji.
- Uprawnienia zapisu są identyczne dla ról DOCTOR i ASSISTANT (decyzja kliniczna właściciela
  produktu, Clarifications). Rozliczalność zapewnia autor wpisu i dziennik audytu. Rola
  ADMINISTRATOR nie ma rutynowego wglądu klinicznego w odontogram; dostęp serwisowy podlega
  audytowi na tych samych zasadach co każdy inny.
- Wpisy odontogramu podlegają tym samym regułom RODO co reszta kartoteki: należą do danych
  szczególnej kategorii wg Art. 9, wymagają udokumentowanego przeglądu bezpieczeństwa przed merge i
  nie są usuwane wcześniej niż pozwalają na to przepisy o retencji dokumentacji medycznej.
- Model dwóch niezależnych statusów (kliniczny + techniczny status korekty) jest przejęty wprost z
  `004-patient-medical-history` dla spójności doświadczenia i implementacji.
- Wiek pacjenta wyliczany z daty urodzenia (pole wprowadzone w `002-patient-records`) jest
  wystarczającą podstawą do zaproponowania domyślnego trybu uzębienia; granice 6 i 13 lat są
  przyjęte jako typowe i podlegają korekcie lekarza.
- Widok odontogramu pozostaje zakładką w istniejącym widoku szczegółów pacjenta, obok zakładek
  danych podstawowych, historii medycznej i historii wizyt.
- **Brak migracji danych**: system nie jest wdrożony produkcyjnie i nie istnieją dane pacjentów,
  więc binarny model `ToothStatus` z `002-patient-records` jest zastępowany bez ścieżki migracyjnej.
  Plan może usunąć go wraz z odpowiadającą mu tabelą, zamiast utrzymywać zgodność wsteczną.
- Kanały korzeniowe są danymi wprowadzanymi w trakcie leczenia, nie danymi referencyjnymi. Brak
  kanałów przy zębie oznacza "nie odnotowano", a nie "ząb nie ma kanałów" — typowa anatomia służy
  wyłącznie jako podpowiedź przy dodawaniu (FR-064).
- Trzy stany kanału (do leczenia / wyleczony / niedoleczony) opisują postęp leczenia
  endodontycznego i są niezależne od statusu klinicznego wpisu (aktywne/zakończone) oraz od
  statusu korekty (aktualny/nieaktualny).
- Sylwetka zęba na diagramie jest nośnikiem anatomii, a nie danych powierzchniowych; dane
  powierzchniowe czyta się ze schematu powierzchni w środkowym pasie diagramu (FR-029).
- Zaznaczenie wielokrotne jest narzędziem wprowadzania danych, nie osobnym bytem — jego rezultatem
  jest zawsze zbiór niezależnych wpisów, po jednym na ząb, każdy z własną historią i możliwością
  osobnej korekty.
- **Zależność międzymodułowa**: ta specyfikacja zastępuje binarny model stanu zęba z
  `002-patient-records` i rozszerza system tokenów z `003-brand-ui-theme`. Uprawnienia ról
  pozostają zgodne z FR-006a z `002` (DOCTOR i ASSISTANT z prawem zapisu, RECEPTION bez dostępu).
  Zmianę modelu danych należy jawnie uwzględnić w `/speckit-plan`.
