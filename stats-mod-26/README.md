# Stats – mod do Minecrafta (Fabric 26.2)

Mod kliencki: wystarczy, że masz go Ty (serwer nie musi go mieć).
Naciśnij **B** w grze, żeby otworzyć okno ze statystykami (klawisz zmienisz w Opcje → Sterowanie → Stats).

Zakładki:
- **Profil** – ile razy zginąłeś na tym serwerze, ilu graczy zabiłeś, K/D, liczba otrzymanych uderzeń
- **Hit** – nicki graczy, którzy Cię uderzyli (z liczbą uderzeń)
- **Kill** – nicki graczy, którzy Cię zabili (z liczbą zabójstw)

Statystyki są liczone osobno dla każdego serwera i zapisują się w `.minecraft/config/stats/`.

## Instalacja gotowego moda
1. Zainstaluj Fabric Loader dla 26.2 oraz **Fabric API**.
2. Wrzuć `stats-1.0.0.jar` do folderu `.minecraft/mods`.

## Jak zbudować plik .jar (bez instalowania czegokolwiek)
1. Załóż darmowe konto na github.com i utwórz nowe repozytorium.
2. Wgraj do niego całą zawartość tego folderu (razem z ukrytym folderem `.github`).
3. W zakładce **Actions** uruchomi się „Build Stats mod" (albo kliknij *Run workflow*).
4. Po ok. 2–3 minutach otwórz zakończone uruchomienie i pobierz z sekcji **Artifacts** plik `stats-mod` – w środku jest `stats-1.0.0.jar`.

## Budowanie lokalnie
Java 25 + Gradle 9.5+: `gradle build` → plik w `build/libs/stats-1.0.0.jar`.

## Jeśli build zgłosi błąd
Numery wersji w `gradle.properties` (zwłaszcza `fabric_version`) trzeba czasem zaktualizować.
Wejdź na https://fabricmc.net/develop/template/ , wybierz 26.2 i przepisz stamtąd
`loader_version`, `loom_version` i `fabric_version`. Kod nie był kompilowany – jeśli build pokaże
błąd w plikach `.java`, wklej go do Claude, a zostanie poprawiony.

## Ograniczenia (jak to działa)
- **Zgony** – liczone, gdy Twoje zdrowie spada do 0.
- **Kto zabił / zabójstwa** – z wiadomości o śmierci na czacie (standardowe, vanilla). Gdy serwer ukrywa lub zmienia te wiadomości, za zabójcę uznawany jest ostatni gracz, który Cię uderzył w ciągu 10 s; zabójstwa liczone są tylko ze standardowych wiadomości.
- **Hit** – wykrywany heurystycznie: gracz w zasięgu ~6,5 bloku, który macha ręką w Twoją stronę w chwili otrzymania obrażeń. Strzały i inne obrażenia z dystansu nie są tak przypisywane.
