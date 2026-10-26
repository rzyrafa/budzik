# Przegląd aplikacji Budzik (17.10.2026)

## Ogólna opinia

To solidny pomysł z dobrze wykonanym rdzeniem:
- Alarmy są planowane przez `AlarmManager.setAlarmClock()`. To najpewniejszy sposób na Androidzie.
- Zadania matematyczne, serie alarmów, drzemka, „Sprawdź czy wstałem” i pułapka na restart (reboot trap) tworzą spójny pomysł na budzik, którego nie da się łatwo oszukać.
- Generatory (`MathProblemGenerator`, `BatchAlarmGenerator`) to czysty Kotlin i mają testy (było 156, wszystkie przechodziły).
- UI w Compose wygląda starannie i ma własną, spójną stylistykę.

Słabsze strony:
- **`MainActivity.kt` ma ~3800 linii.** Cała logika (zapis do bazy, planowanie, drzemki, serie) siedzi w lambdach w UI. Nie ma ViewModelu, choć `PROJECT.md` go przewiduje. Ta sama logika jest skopiowana w kilku miejscach: tworzenie drzemki występuje 2×, a „wyłącz / przesuń alarm po zadzwonieniu” 3× (`AlarmEngine`, `RingingActivity`, częściowo `AlarmReceiver`).
- **Typy alarmów rozpoznaje się po etykiecie** (`label.startsWith("Drzemka")`, `startsWith("Test")`). Jeśli zmienisz tekst albo dodasz tłumaczenie, logika się posypie.
- Są dwa równoległe mechanizmy wyzwalania (AlarmManager i ticker `AlarmEngine` co 500 ms). Przez to łatwo o podwójne dzwonienie i niespójny stan.

## Naprawione bugi

| # | Problem | Skutek dla użytkownika | Poprawka |
|---|---------|------------------------|----------|
| 1 | „Testuj” w edycji alarmu otwierał ekran dzwonienia z prawdziwym ID alarmu | Po rozwiązaniu testu **prawdziwy alarm na jutro się wyłączał** (i planowało się „Sprawdź czy wstałem”) | Flaga `EXTRA_IS_PREVIEW`: test nie zmienia już bazy (`RingingActivity.kt`, `MainActivity.kt`) |
| 2 | `RingingActivity` jest `singleInstance`, ale nie obsługiwała `onNewIntent` | W serii: jeśli #2 zadzwonił, zanim rozwiązałeś #1, to #2 zostawał włączony z czasem w przeszłości, dzwonił ponownie zaraz po wyłączeniu, a „Sprawdź czy wstałem” się nie planowało | Ekran zbiera wszystkie alarmy, które zadzwoniły, i wyłącza je razem |
| 3 | `AlarmEngine` trzymał **24-godzinny WakeLock + ciągłe „ciche audio”**, gdy był włączony jakikolwiek alarm | Procesor nie zasypiał przez całą dobę, co mocno drenowało baterię | Usunięte. Budzenie zapewnia `setAlarmClock()` |
| 4 | Nowy `ToneGenerator` co sekundę w oknie „Sprawdź czy wstałem”, bez `release()` | Wyciek natywnych ścieżek audio: po kilkudziesięciu sekundach może **nie wystartować dźwięk alarmu** | `release()` po każdym piknięciu |
| 5 | Po zmianie strefy czasowej / godziny alarmy zachowywały stary bezwzględny czas | Po podróży alarm „7:00” dzwonił np. o 6:00 lub 8:00 czasu lokalnego | `realignTriggerToWallClock()` w `TimeChangeReceiver` i `BootReceiver` (z testem `RealignTriggerTest`) |
| 6 | Po restarcie telefonu przegapione jednorazowe alarmy planowały się z czasem w przeszłości | Telefon wyłączony w nocy: zaraz po włączeniu dzwonił alarm sprzed wielu godzin lub dni | Alarm przegapiony o więcej niż 15 min jest wyłączany. Alarm z pułapki na restart nie dzwoni już podwójnie |
| 7 | Zapis edycji alarmu liczył następny czas bez dni tygodnia | Alarm cykliczny po edycji ustawiał się jak jednorazowy | Przekazywany jest `daysOfWeekBitmask` |

Kopia plików sprzed zmian leży w `../budzik_backup_przed_poprawkami/src`.

## Sugestie (od najważniejszych)

1. **Direct Boot.** Baza Room jest w zwykłym (szyfrowanym) magazynie, więc przy `LOCKED_BOOT_COMPLETED` odczyt się nie udaje. Jeśli telefon zrestartuje się w nocy (np. aktualizacja) i nikt go nie odblokuje, **rano żaden alarm nie zadzwoni**. Rozwiązanie: trzymać bazę i preferencje w `createDeviceProtectedStorageContext()` i oznaczyć `AlarmReceiver`/serwis jako `directBootAware`.
2. **Android 15 i `mediaPlayback`.** Od API 35 nie można uruchomić usługi pierwszoplanowej typu `mediaPlayback` z `BOOT_COMPLETED`, więc pułapka na restart może cicho nie zadziałać. Usługa budzika powinna mieć typ `systemExempted` lub `shortService` (alarmy z `USE_EXACT_ALARM` mają do tego prawo). Warto to przetestować na emulatorze z API 35.
3. **Jedno źródło prawdy dla „alarm zadzwonił”.** Logika „wyłącz jednorazowy / usuń drzemkę / przesuń cykliczny” powinna siedzieć w jednej funkcji repozytorium. Teraz są 3 kopie, które już się rozjechały: `AlarmEngine` wyłącza drzemkę, a `RingingActivity` ją usuwa.
4. **Wyrzucić ticker `AlarmEngine` albo ograniczyć go do czasu, gdy aplikacja jest na ekranie.** Odpytuje bazę 2× na sekundę i dubluje AlarmManager. Po usunięciu WakeLocka i tak nie działa w tle.
5. **Dodać pole `type` do `AlarmEntity`** (`SINGLE`, `SERIES`, `NAP`, `TEST`) zamiast rozpoznawania po etykiecie. Przy okazji: schemat ma `fallbackToDestructiveMigration()`, więc **każda zmiana schematu kasuje użytkownikom alarmy**. Przed wydaniem dodaj prawdziwe migracje i `exportSchema = true`.
6. **Podzielić `MainActivity.kt`** na `AlarmViewModel` i osobne pliki ekranów/arkuszy (struktura z `PROJECT.md`: `ui/list`, `ui/dialogs`, `ui/ringing`).
7. **Furtka w „cichym czasie”.** Przycisk „Wycisz na 20s” można klikać w nieskończoność i alarm nigdy nie wraca na pełną głośność. Warto dać limit, np. 3 wyciszenia na alarm.
8. **`onBackPressed()` jest przestarzałe.** Przy `targetSdk 36` przestanie działać (predictive back). Lepiej użyć `onBackPressedDispatcher.addCallback { }`.
9. **Uprawnienia.** `SYSTEM_ALERT_WINDOW` nie jest potrzebne, bo full-screen intent wystarcza, a Google Play i użytkownicy patrzą na nie podejrzliwie. Trzeba też sprawdzać `NotificationManager.canUseFullScreenIntent()` (Android 14+) i kierować użytkownika do ustawień, jeśli zwraca `false`. Bez tego ekran nie pojawi się na blokadzie.
10. **Drobiazgi:** `START_STICKY` w serwisie po restarcie procesu zapisuje `alarmId = -1`. Testowe alarmy z panelu diagnostycznego zostają w bazie jako wyłączone śmieci. Teksty są zahardkodowane w kodzie zamiast w `strings.xml`. Plik `budzik.apk` i katalog `.agents/` warto trzymać poza projektem lub w `.gitignore`. Warto też założyć repozytorium git.

## Czego nie sprawdziłem

Poprawki są zweryfikowane kompilacją (`assembleDebug`) i testami jednostkowymi (159/159). **Nie testowałem ich na telefonie ani emulatorze.** Szczególnie warto ręcznie sprawdzić:
- serię z krótkim odstępem, gdy nie rozwiązuje się zadania;
- zmianę strefy czasowej w ustawieniach;
- restart przy włączonym alarmie.

## Runda 2: porządki w kodzie (17.10.2026)

- **Furtka w cichym czasie:** „Wycisz na 20s” można użyć maks. 3 razy na jedno dzwonienie (`RingingActivity.MAX_QUIET_USES`), a kliknięcie w trakcie nie przedłuża już czasu. Przycisk pokazuje, ile wyciszeń zostało.
- **Pole `type` w `AlarmEntity`** (`SINGLE`/`SERIES`/`NAP`/`TEST`) zamiast `label.startsWith(...)`. Baza ma wersję 4 i prawdziwą migrację 3→4, która nie kasuje alarmów. Test: `AlarmDatabaseMigrationTest`. Schemat eksportuje się do `app/schemas/`, a wyczyścić można już tylko bazy w wersji 1–2.
- **`AlarmLifecycle`**: jedno miejsce na „uruchom dzwonienie”, „co zrobić z alarmem po zadzwonieniu” i „wyłączenie”. Korzystają z niego `AlarmReceiver`, `AlarmEngine`, `WakeUpCheckReceiver` i `RingingActivity`. Drzemki i alarmy testowe są teraz usuwane po zadzwonieniu, więc w bazie nie zostają już śmieci z diagnostyki.
- **`AlarmViewModel`**: cała logika tworzenia, edycji, usuwania i planowania alarmów, wyjęta z lambd w UI.
- **`MainActivity.kt` podzielony** z ~3800 na 91 linii. Reszta trafiła do `MainAlarmScreen.kt`, `AlarmCards.kt`, `AlarmEditorSheets.kt`, `SheetComponents.kt`, `SoundSettingsSheet.kt`, `QuickNapSheet.kt`, `DiagnosticsSection.kt` i `WakeUpVerificationOverlay.kt`.
- **Diagnostyka** jest już tylko w panelu „DŹWIĘK ALARMU” (usunięta z „SZYBKA DRZEMKA”).

Weryfikacja: `assembleDebug` + 160/160 testów. Nadal bez testu na urządzeniu.

## Runda 3: dźwięk, diagnostyka, panel serii

- **Podwójny dzwonek:** prawdziwą przyczyną był kanał powiadomień `budzik_alarm_channel` z systemowym dźwiękiem alarmu, który grał razem z MP3. Nowy kanał `budzik_alarm_channel_silent` jest bez dźwięku i wibracji, bo usługa robi to sama i uwzględnia ustawienie wibracji. Stary kanał jest usuwany przy starcie. Usunięty też 300-ms „watchdog”: syrena włącza się tylko przy prawdziwym błędzie MediaPlayera, po zwolnieniu MP3. Po każdym dzwonieniu MP3 jest od razu wczytywany ponownie.
- **Diagnostyka:** zwinięta karta „Diagnostyka i szybkie testy” na dole panelu „DŹWIĘK ALARMU”.
- **Panel serii:** panele mają do 94% wysokości ekranu (wcześniej max 680 dp), mniejsze odstępy w formularzu i mniejszy przycisk „X” bez pustego miejsca pod spodem. Szacunek formularza serii: ~660 dp przy zwiniętym zegarze. Nie sprawdzone na urządzeniu.

## Runda 4: emulator, bęben godziny, aktualizacja narzędzi (19.10.2026)

- **Sprawdzone na emulatorze (Android 17):** formularz serii mieści się na ekranie bez przewijania (także z „Sprawdź czy wstałem”). Jedyny wyjątek: rozwinięty bęben godziny razem z włączonym sprawdzaniem. Menu chowa się z animacją. Przy własnym MP3 gra **jeden** odtwarzacz (`dumpsys audio`), kanał powiadomień jest cichy, a po rozwiązaniu zadania dźwięk i usługa się zatrzymują.
- **Bęben godziny:** `VerticalPager` z Compose 1.7 przy szybkim rzucie cofał się zamiast rozpędzać. Zastąpiłem go `LazyColumn` + `rememberSnapFlingBehavior` (`SnapPosition.Center`). Mocny rzut przelatuje przez całą skalę, a słaby przesuwa o 1–2 pozycje.
- **Narzędzia:** Android Studio przeniesione na `X:\Emulator` ma Javę 25, więc zaktualizowałem Gradle 8.10.2 → 9.8.1, AGP 8.8.2 → 9.4.1 (wbudowany Kotlin, bez wtyczki `kotlin-android`), Kotlin 2.0.21 → 2.4.21, KSP → 2.3.12, Room 2.6.1 → 2.8.5 i Robolectric 4.14.1 → 4.16.1. `gradle.properties` wskazuje `X:\Emulator\jbr`. 160/160 testów.
- **Potrójny start usługi** (naprawione): `AlarmRingingService` ignoruje kolejne starty, gdy już dzwoni. MP3 nie rusza od nowa, a w logu jest 1 start + 1 zignorowany.
- **Baner nad zadaniem** (naprawione): gdy ekran z zadaniem jest na wierzchu, powiadomienie przechodzi na cichy kanał `budzik_alarm_ongoing` z osobnym id (9998), a głośne (9999, z banerem) jest kasowane. Po wyjściu przyciskiem Home głośne wraca. Sprawdzone na emulatorze w obie strony.
