# EgoX SeedCracker

<img src="branding/logo_white.png" alt="EgoX Logo" width="96" height="96">

Eigenständige Fabric-Client-Mod von **EGOIST** für Minecraft Java **1.21.11** und **26.3**.

[Community Discord](https://discord.gg/FFuMpJg6yj) · [Community Seed-Datenbank](https://docs.google.com/spreadsheets/d/1y7HEf0x_j0Dg6THsi-lTT3T_u0-PdP0Q8SikVPsVacg/edit)

## Installation

1. Fabric Loader und die passende Fabric API für deine Minecraft-Version installieren.
2. Genau eine passende `EgoX-SeedCracker-0.6.0+<Minecraft-Version>.jar` aus `dist/` in den `mods`-Ordner legen. Vorherige SeedCracker-JARs dabei ersetzen.
3. Minecraft starten und eine Welt betreten. `/egox` zeigt die Befehle.

Minecraft 1.21.11 benötigt Java 21 oder neuer. Die 26.3-Fassung benötigt Java 25 oder neuer.

## Einstellungen und HUD ab 0.6.0

`/egox settings` oder `/egox gui` öffnet das Einstellungsmenü. Mit optional installiertem **Mod Menu** ist dieselbe Oberfläche über **Mods → EgoX → Konfigurieren** erreichbar; Mod Menu wird nicht benötigt, um EgoX oder die Befehle zu verwenden.

- **HUD:** Ein-/Ausschalten, mit der Maus frei verschieben, über Buttons in kleinen Schritten bewegen und auf oben mittig zurücksetzen. Kopfzeile, Datenzeile, Suchphase, Fortschrittsbalken und Detailzeile sind separat schaltbar. Ausgeschaltete Zeilen verkleinern das Panel. Die Vorschau funktioniert auch bei ausgeschaltetem HUD. Bei vollständig deaktivierten Zeilen kann zunächst eine Zeile im HUD-Menü aktiviert werden.
- **Hintergrund:** 0–100 %. Bei 0 % verschwinden die gesamte Panel-Fläche, Rahmen, Glanz und Schatten; Text und aktivierte Balken bleiben sichtbar. Positionen sind relativ zum verfügbaren Bildschirmbereich gespeichert und werden bei GUI-Skalierung/Fenstergrößenwechsel innerhalb eines kleinen Sicherheitsrands gehalten.
- **Module:** Seed-, Salt-, Biom-, Erz-, Bedrock-, Geoden- und Dungeon-Checker, Finder, Biom-Sammlung, Zusatzfunde und Seed-DB-Upload einzeln schalten. Biom-/Erz-Checker bleiben standardmäßig OFF; HUD bleibt standardmäßig OFF.
- **Outlines:** Globale Sichtbarkeit, Beschriftungen und jede Struktur-/Fundart separat schalten.
- **Leistung:** 1–64 Such-Threads und Presets für sparsam, ausgewogen, empfohlen oder maximale Parallelität. Mehr Threads können FPS und Speicher belasten und sind nicht automatisch schneller. Die Einstellung ist kein CPU-Prozentlimit und wirkt auf neu gestartete Jobs/Phasen; bereits gestartete Worker behalten ihre Zahl. Die automatischen Biom-/Feature-Fallbacks nutzen höchstens zwei Worker.

Änderungen werden beim Verlassen der Oberfläche gespeichert. Tab/Enter und die native Slider-Tastaturbedienung bleiben nutzbar; Esc speichert und kehrt zurück. Die lokale Bibliothek und vorhandene Beobachtungen bleiben beim Umschalten der Module erhalten. Das Logo in `branding/logo_white.png` ist das unverändert übernommene Projektlogo; `scripts/create-icon.py` kopiert dieselbe PNG in beide Varianten.

## Was die Mod bereits kann

- Intakte Wüstenpyramiden, Dschungeltempel, Hexenhütten und Ozeanmonumente anhand geprüfter Blockmuster erkennen.
- Native Startvorlagen für Iglus, Schiffswracks (einschließlich Holzpaletten und Drehungen), Endstädte, Pillager-Außenposten, fünf Dorfstile, antike Städte, Trial Chambers und Trail Ruins prüfen. Bastionen sind derzeit auf die erkennbare Brücken-Startvorlage beschränkt. Minecraft 26.3 liefert zusätzlich Startvorlagen für verlassene Lager. Nicht jede Untervariante oder prozessierte Vorlage ist erkennbar; `/egox finder list` und der Diagnoseexport zeigen die tatsächliche Abdeckung.
- Jede Strukturart hat eine eigene, stabile Outline-Farbe und einen gespeicherten An-/Aus-Schalter. Standardmäßig sind alle Typen außer Geoden an. Die Kennzeichnung einer passenden oder abweichenden Platzierung steht im Text und ändert diese Farbe nicht. Die zehn zentralen End-Säulen haben eigene Outlines.
- Mob-Spawner, Trial-Spawner und Vaults als einzelne Blockfunde markieren, auch mehrere im selben Chunk. Amethystdrusen liefern Geoden-Fundbereiche; diese werden räumlich zusammengefasst und zeigen keine berechneten Geodenzentren. Diese visuellen Beobachtungen werden separat exportiert und zählen keine Seed-Bits.
- Im HUD Datenmenge, Bit-Ziele, Seed-/Salt-Schalter und während einer Suche deren Phase, Prozent, geprüfte Kandidaten, Geschwindigkeit und geschätzte Restzeit dieser Phase anzeigen.
- Random-Spread-Profile einschließlich Spacing, Separation, Salt und Verteilung direkt aus der installierten Minecraft-Version lesen. Andere Random-Spread-Strukturen lassen sich manuell erfassen.
- Den vollständigen 48-Bit-Struktur-Seed-Raum mit einem eigenen parallelen Lifting-Verfahren durchsuchen. Seltene `nextInt`-Rejection-Fälle werden zusätzlich geprüft.
- Für jeden Struktur-Seed alle 65.536 oberen Seed-Bit-Kombinationen gegen den vom Server übertragenen Seed-Hash prüfen. Erst Hash-bestätigte Welt-Seeds werden gespeichert und hochgeladen.
- Die Größenreihenfolge intakter zentraler End-Säulen gegen alle 65.536 Säulen-Seeds prüfen. Bei hinreichenden Strukturstarts werden die verbleibenden 2^32 RNG-Zustände je Säulen-Seed durchsucht; das unterstützt auch trianguläre Endstadt-Platzierungen. Startbedingung: mindestens fünf Starts, 32 theoretische Platzierungsbits und höchstens vier Säulen-Seed-Kandidaten.
- Nach einem Fehlschlag der gemeinsamen Lifting-Suche ausreichend belegte Placement-Familien einzeln versuchen. So muss eine andere Strukturart mit verändertem Salt einen über passende Strukturen lösbaren Seed nicht blockieren.
- Nach Bestätigung des Welt-Seeds abweichende Strukturarten getrennt untersuchen und deren gesamten **signed-int-Salt-Bereich** automatisch durchsuchen. Ein global eindeutiger Treffer wird als Sitzungs-Override übernommen.
- Welt-Seed-Kandidaten unabhängig von Strukturen mit der nativen Biom-Generierung prüfen. Unterstützte Vanilla-Presets: `overworld`, `large_biomes`, `amplified`.
- Seit 0.5.1 auch Nether-Biome wie Wirrwald, Karmesinwald, Seelensandtal und Basaltdeltas sammeln und mit dem nativen Nether-Generator prüfen. Gleiche Quart-Koordinaten verschiedener Dimensionen überschreiben sich nicht. Im Vanilla-Legacy-Modell unterscheiden Nether-Biome die oberen 16 Seed-Bits nicht; der Biom-Checker bleibt standardmäßig OFF.
- Seit 0.4.0 Biomdaten automatisch einbeziehen: Hash-Treffer aus Struktur-/End-Suchen mit allen gesammelten Biom-Samples gegenprüfen. Nach erfolglosen Hash-Prüfungen aller Strukturgruppen werden die oberen 16 Bits der vorhandenen 48-Bit-Kandidaten anhand der Biome gesucht. Dieser Fallback prüft ohne Hash-Vorfilter; Treffer mit fehlendem oder widersprechendem Hash bleiben unbestätigte Kandidaten und werden nicht hochgeladen. Die bisherige 1000-Kandidaten-Grenze der Hash-Suche entfällt.
- Paper-Feature-Seeds und additive Decorator-Salts aus **exakten RNG-Traces** in einem angegebenen Bereich suchen.
- Bestätigte Multiplayer-Seeds automatisch mit Serveradresse, Dimension, Minecraft-Version und Prüfstatus in die eigene Google-Tabelle übertragen. Lokales Journal, ausstehende Uploads und Wiederholungen sind eingebaut.
- Ab 0.5.0 eine lokale Bibliothek pro Spieler, Serveradresse, Seed-Hash und Minecraft-Version führen. Strukturstarts, Biom-Samples, Salt-Overrides, Markierungen, Erz-Abbauereignisse, Bedrock-/Feature-Beobachtungen, Säulendaten und Analyseberichte werden beim Wiederbeitritt geladen. Rohereignisse werden laufend journalisiert; Arbeitsstände werden alle fünf Sekunden und beim Verlassen gesichert. Die CPU-Suchposition wird nicht exakt fortgesetzt: Suchläufe starten mit den wiederhergestellten Daten neu.
- Abgebaute Vanilla-Erze und Ancient Debris mit Block-/Chunk-Koordinaten, Dimension und Zeit erfassen, auch bei ausgeschaltetem Erz-Checker. Client-Vorhersage und spätere Server-Blockänderung beziehungsweise Ablehnung bleiben unterscheidbar. Natürliche Herkunft wird nicht behauptet.
- Vanilla-Bedrock-Übergangsschichten gegen Kandidaten prüfen; Geoden über Seltenheit, Platzierung und maximale Ausdehnung eingrenzen. Das optionale Smaragderz-Replay verzweigt unbekannte Generierungsausfälle und liefert konservative Bedingungen statt erfundener exakter RNG-Traces. Andere Erze bleiben bisher Rohdaten.
- Seit 0.5.1 intakte Overworld-Monsterräume anhand von Spawner, vollständigem 7×7-/7×9-/9×7-/9×9-Boden und passenden Wänden erkennen. Cobblestone-/Moosstein-Muster liefern separat angezeigte **bedingte Muster-Bits** und werden zur modernen Welt-Seed-Kandidatenprüfung verwendet. Diese Bits werden nicht auf das 44-Bit-Struktur-Lifting-Ziel addiert. Ein Spawner allein, Mineshaft-/Trial-Spawner oder ein beschädigter Raum genügt nicht. Ein freier Dungeon-RNG-Inversionssolver ist weiterhin nicht implementiert.

Die Veröffentlichung ist eine Entwicklungsversion. Ein vollständiger Ingame-Test auf realen Vanilla-/Paper-Servern und ein Vergleichsbenchmark stehen noch aus. Strongholds, Mineshafts, Netherfestungen, Waldanwesen, Ruinenportale, Ozeanruinen und vergrabene Schätze sind noch nicht automatisch als zuverlässige Seed-Beobachtungen erschlossen. Random-Spread-Strukturen mit bekanntem exaktem Start lassen sich manuell erfassen; Strongholds und Mineshafts benötigen andere Solver.

## Benutzung

Für die automatische Suche möglichst weit auseinanderliegende, intakte Strukturen erkunden. Ab fünf geeigneten Starts und mindestens 44 theoretischen Lifting-Bits beginnt die Overworld-Suche. Im End erst Endstädte sammeln und dann die mittleren End-Säulen laden; Beobachtungen bleiben bei Dimensionswechseln mit gleicher Serveradresse und gleichem Seed-Hash erhalten. Korrelationen können zusätzliche Starts erforderlich machen. Die Bits sind eine theoretische Informationsmenge und keine Zahl bereits rekonstruierter Seed-Bits. Eine vollständige Bestätigung gibt es erst durch den Seed-Hash.

Das HUD ist standardmäßig **aus**. Mit `/egox render hud ON` erscheint eine kompakte Glaskarte oben mittig: abgerundete transparente Flächen, Lichtkante und dezenter Verlauf. Es handelt sich um einen Glas-Look ohne bildschirmweiten Blur. Sein Balken zeigt während einer Suche den Fortschritt der benannten Phase; beim Sammeln zeigt er das Lifting-Datenziel. Die geschätzte Restzeit gilt nur für die aktuelle Phase, nicht für unbekannte weitere Versuche. Unter 220 GUI-Pixeln Bildschirmbreite wird das Panel ausgeblendet. F1 blendet HUD und Outlines aus.

Beim ersten Update von einer älteren Konfiguration werden HUD **einmalig ausgeschaltet** und Outline-Reichweite auf **512 Blöcke** gesetzt. Danach bleiben selbst gewählte Werte gespeichert. Upload-Einstellungen bleiben erhalten. Die Reichweite erzeugt keine zusätzlichen Chunk-Daten: Erkennung und Anzeige verwenden vom Server geladene Beobachtungen. Normale Spawner, Trial-Spawner und Vaults werden bereits beim Chunk-Empfang erfasst; Geoden-Funde laufen über den budgetierten Blockscanner.

Ab 0.3.1 sind Geoden-Outlines standardmäßig **aus**, auch beim Update einer bisherigen Konfiguration. Andere Typen sind standardmäßig an; bereits gesetzte globale Schalter bleiben erhalten. `/egox render outlines list` zeigt alle verfügbaren Typen und ihre gespeicherten Schalter. Beispiele: `/egox render outlines shipwreck OFF`, `/egox render outlines mob_spawner OFF` und `/egox render outlines geode ON`. Tab schlägt die vollständigen IDs vor. Ausgeblendete Strukturen werden weiter erfasst und für die Seed-Suche verwendet. Ausgeblendete Marker belegen keinen der 512 sichtbaren Plätze. Bei deaktivierten Geoden-Outlines entstehen keine neuen Geoden-Markierungen; seit 0.5.0 werden die beobachteten Drusen unabhängig davon für Bibliothek und Kandidatenprüfung erfasst. Einschalten plant einen erneuten Scan geladener Chunks ein. Die globalen Schalter `outlines` und `features` gelten weiterhin.

Alle 20 nativen Schiffswrack-Vorlagen sind einschließlich Holzpaletten und Drehungen im Finder enthalten. Auch halbe, umgedrehte und bereits natürlich verfallene Wracks können Platzierungsdaten liefern, wenn der genaue Start zuverlässig erkennbar ist. Nicht markiert bedeutet deshalb nicht „nutzlos“: Der konservative Finder verlangt alle ausgewählten Prüfblöcke. Fehlende Chunks, nachträgliche Änderungen, Überschneidungen mit anderer Generierung, Reichweite und das Limit sichtbarer Marker können eine Erkennung oder Anzeige verhindern. Weitere Wracks derselben Platzierungsregion liefern außerdem nicht automatisch unabhängige Bits.

Alle Befehle beginnen mit `/egox`:

Das Einstellungsmenü öffnet sich mit `/egox settings` beziehungsweise `/egox gui`.

| Befehl | Funktion |
| --- | --- |
| `/egox cracker seed ON` / `OFF` | Seed-Automatik dauerhaft an-/ausschalten; eine laufende automatische Seed-Suche abbrechen |
| `/egox cracker salt ON` / `OFF` | Salt-Automatik unabhängig an-/ausschalten |
| `/egox cracker biomes ON` / `OFF` | Automatische Biom-Gegenprüfung und Biom-Fallback schalten; standardmäßig OFF |
| `/egox cracker ore ON` / `OFF` | Smaragderz-Kandidatenprüfung schalten; standardmäßig OFF |
| `/egox cracker ore status` | Erz-/Bedrock-/Geoden-Beobachtungen und Analyse anzeigen |
| `/egox cracker ore search <Min> <Max>` | Vollständige Welt-Seed-Kandidaten anhand Smaragderz prüfen; bis 10.000 Werte |
| `/egox cracker ore feature <Min> <Max>` | Mögliche Smaragderz-Population-Eingangsseeds prüfen, z.B. für unabhängige Feature-Seeds; keine Welt-Seed-Bestätigung |
| `/egox cracker bedrock ON` / `OFF` | Bedrock-Prüfung schalten; standardmäßig ON |
| `/egox cracker bedrock search <Min> <Max>` | Einen vollständigen Welt-Seed-Bereich anhand Bedrock prüfen; bis 1.000.000 Werte |
| `/egox cracker geode ON` / `OFF` | Geoden-Kandidatenprüfung schalten; standardmäßig ON, unabhängig von Geoden-Outlines |
| `/egox cracker geode search <Min> <Max>` | Einen vollständigen Welt-Seed-Bereich anhand beobachteter Drusen prüfen; bis 1.000.000 Werte |
| `/egox cracker dungeon ON` / `OFF` | Dungeon-Bodenmusterprüfung schalten; standardmäßig ON |
| `/egox cracker dungeon status` | Dungeon-Muster-Bits und Feature-Analyse anzeigen |
| `/egox cracker dungeon search <Min> <Max>` | Welt-Seed-Bereich anhand intakter Dungeon-Böden prüfen; bis 1.000.000 Werte |
| `/egox cracker biomes status` | Biom-Prüfung, geprüften Suchraum und unbestätigte Kandidaten anzeigen |
| `/egox cracker biomes preset overworld` | Vanilla-Biom-Preset wählen; auch `large_biomes` und `amplified`, bei ruhender Suche |
| `/egox cracker status` | Gemeinsame Fortschrittsanzeige im Chat |
| `/egox render outlines ON` / `OFF` | Alle Outlines gemeinsam schalten |
| `/egox render outlines <Typ> ON` / `OFF` | Einzelnen Typ schalten, z.B. `shipwreck`, `egox:geode`, `egox:mob_spawner` |
| `/egox render outlines list` | Verfügbare Typen und gespeicherte Einzelschalter anzeigen |
| `/egox render hud ON` / `OFF` | Fortschritts-HUD schalten |
| `/egox render labels ON` / `OFF` | Outline-Beschriftungen schalten |
| `/egox render range <Blöcke>` | Outline-Reichweite von 16 bis 512 Blöcken, Standard und Maximum 512 |
| `/egox render features ON` / `OFF` | Spawner-, Vault- und Geoden-Markierungen schalten |
| `/egox finder ON` / `OFF` | Automatische Strukturerfassung schalten |
| `/egox finder list` | Automatische und manuelle Strukturunterstützung anzeigen |
| `/egox finder rescan` | Bereits geladene Chunks erneut einplanen |
| `/egox db bits` | Platzierungs-/Lifting-Bits je Familie, Säulen-Bits und bestätigte Seed-Bits |
| `/egox db biomes` | Biom-Analyse und Hash-Status der Biom-Kandidaten anzeigen |
| `/egox db ores` | Erz-Abbauereignisse, jüngste Block-/Chunk-Koordinaten und Serverstatus anzeigen |
| `/egox db library` | Status der lokalen Restore-Bibliothek |
| `/egox db library list` | Gesicherte Welten dieses Spielers anzeigen |
| `/egox db library open` | Lokalen Bibliotheksordner öffnen |
| `/egox db library save` | Aktuellen Arbeitsstand sichern |
| `/egox db library restore` | Passende Sicherung der aktiven Welt erneut einlesen; laufende Suche vorher beenden |
| `/egox db seeds` | Welt-Seed und einzelne Platzierungsseeds beobachteter Placement-Familien |
| `/egox db seeds <Typ>` | Salt, Beobachtungen und Platzierungsseed einer Strukturart, z.B. `minecraft:trial_chambers` |
| `/egox db data` | Gesammelte Strukturstarts mit Dimension und Chunk-Koordinaten |
| `/egox db export` | Beobachtungen, Salt-Overrides und Finder-Diagnosen exportieren |
| `/egox db upload ON` / `OFF` | Automatische Übertragung bestätigter Funde schalten |
| `/egox status` | Beobachtungen, laufende Suche und Upload-Status |
| `/egox stop` | Laufende Suche abbrechen, Automatik pausieren |
| `/egox resume` | Automatik wieder aktivieren |
| `/egox discord` | Community Discord öffnen |
| `/egox db` | Öffentliche Seed-Tabelle öffnen |
| `/egox db status` | Upload-Status anzeigen |
| `/egox db retry` | Ausstehende Uploads erneut versuchen |
| `/egox db auto false` | Automatischen Upload ausschalten |
| `/egox observe <Typ> <ChunkX> <ChunkZ>` | Exakten Strukturstart manuell erfassen; Typ-Vorschläge per Tab |
| `/egox diagnose <WeltSeed>` | Seed-Hash und Platzierungsübereinstimmungen je Strukturart prüfen |
| `/egox salt crack <Typ> <WeltSeed> <Min> <Max>` | Einen Struktur-Salt unabhängig suchen, Grenzen inklusive |
| `/egox salt set <Typ> <Salt>` | Sitzungs-Override setzen |
| `/egox crack structures <Min> <Max>` | Einen expliziten 48-Bit-Struktur-Seed-Bereich prüfen |
| `/egox crack lift <StrukturSeed>` | Alle oberen 16 Welt-Seed-Bits gegen den Server-Hash prüfen |
| `/egox biomes test <WeltSeed>` | Kandidaten gegen die nativen Biom-Samples prüfen |
| `/egox biomes search <Min> <Max>` | Strukturunabhängige Suche in einem bekannten Bereich, maximal 1.000.000 Seeds pro Job |
| `/egox feature crack <Datei.json> <Min> <Max>` | Paper-Feature-Seed oder Decorator-Salt aus einem RNG-Trace suchen |
| `/egox export` | Aktuelle Beobachtungen und Suchergebnisse als JSON exportieren |
| `/egox reload` | Eigene Konfiguration neu laden |

Die Autovervollständigung zeigt nur `ON` und `OFF`, akzeptiert aber weiterhin Kleinschreibung. Bestehende Befehle bleiben als kompatible Kurzwege erhalten. `/egox stop` pausiert die laufende Sitzung; `/egox resume` aktiviert beide Automatiken wieder.

Seit 0.5.0 sind Biom-Checker und Erz-Checker standardmäßig **OFF**. Beim Update werden beide einmalig ausgeschaltet; danach bleiben eigene ON/OFF-Entscheidungen erhalten. Biom-Sammlung (weiterhin maximal 2048 Proben), Erz-Erfassung und Restore-Bibliothek bleiben davon unabhängig aktiv. Bedrock- und Geoden-Checker sind standardmäßig ON; Geoden-Outlines bleiben OFF.

Die einschaltbare Biom-Automatik verwendet einen festen Snapshot der zu Suchbeginn gesammelten Overworld-Biome und das konfigurierte `biomePreset`. Beim Fallback werden bis zu 64 nach Biomvielfalt und räumlicher Verteilung ausgewählte Samples zuerst geprüft; ein überlebender Kandidat muss anschließend mit dem gesamten Snapshot übereinstimmen. Voraussetzung sind mindestens acht Samples aus zwei Biomen. Aktivierte Bedrock-/Geoden-/Erz-Bedingungen dienen als vorgeschaltete Filter. Der Fallback läuft mit höchstens zwei Hintergrund-Workern und zeigt Phase, Prozent und Restzeit im HUD. Neue Strukturstarts unterbrechen den Fallback zugunsten einer neuen Struktursuche. `/egox stop` bricht ab; `/egox cracker biomes OFF` beendet nur die automatische Biom-Prüfung. Einschalten oder `/egox resume` ermöglicht einen neuen Versuch mit aktuellen Daten. Der Diagnoseexport enthält `biomeAnalysis`, `featureAnalysis`, Rohbeobachtungen und den Status einer abgebrochenen oder gekappten Suche.

Biome sind deutlich teurer zu berechnen als ein Seed-Hash: Bereits 159 untere Seed-Kandidaten ergeben rund 10,4 Millionen native Biom-Prüfungen. Eine lange Restzeit dieses Fallbacks ist daher möglich. Unbestätigte Biom-Kandidaten erscheinen unter `/egox db biomes`; sie sind keine global eindeutigen oder durch den Server bestätigten Welt-Seeds. Passt der Server-Hash, aber die Biome widersprechen, bleibt die Hash-Bestätigung gültig und der Biom-Widerspruch wird ausdrücklich gemeldet, etwa bei einem anderen Preset, veränderten Biomen oder alten Chunks.

Eine Struktur mit eigenem Salt hat nicht automatisch einen anderen 64-Bit-Welt-Seed. Die Einzelübersicht berechnet den **Vanilla-äquivalenten Platzierungsseed** `(WeltSeed + tatsächlicherSalt - VanillaSalt) mod 2^48`. Damit reproduziert dieselbe native Vanilla-Platzierung die beobachteten Positionen. Unbekannte oder abweichende Salts werden gekennzeichnet; ohne ausreichende Grundlage steht dort „nicht bestimmt“. Paper-Feature-Seeds sind unabhängig und werden nicht aus dieser Formel abgeleitet.

Auch die erweiterten Suchaktionen sind einheitlich erreichbar: `/egox cracker seed structures …`, `/egox cracker seed lift …`, `/egox cracker seed biomes …`, `/egox cracker seed diagnose …`, `/egox cracker salt crack …`, `/egox cracker salt set …` und `/egox cracker salt feature crack …` verwenden dieselben Argumente wie die bisherigen Kurzwege in der Tabelle.

`observe` erwartet den **Start-Chunk**, nicht die Position des Spielers, einer beliebigen Gebäudeecke oder eines `/locate`-Markers. Bei negativen Blockkoordinaten immer abrunden: Block X = -1 liegt in Chunk X = -1.

Beispiel für einen begrenzten Salt-Suchlauf:

```text
/egox stop
/egox diagnose 123456789
/egox salt crack minecraft:desert_pyramid 123456789 -2147483648 2147483647
/egox status
```

Ein einzelner Treffer einer **begrenzten** Suche ist nur innerhalb dieses Bereichs eindeutig. Die automatische Salt-Suche prüft dagegen den vollständigen 32-Bit-Bereich. Struktur-Salts gelten je **Structure-Set**: Varianten mit gemeinsamem Placement teilen den Salt.

## Konfiguration und gespeicherte Daten

Eigene Dateien liegen in `config/egoxseedcracker/` der jeweiligen Minecraft-Instanz:

| Datei | Inhalt |
| --- | --- |
| `config.json` | Worker, Automatik, Biom-Preset, eigene Tabellen-/Upload-URL, optionaler Upload-Token |
| `results.jsonl` | Bestätigte Welt-Seeds einschließlich Serveradresse und beim Fund gesetzter Salt-Overrides |
| `outbox.json` | Noch nicht bestätigte Uploads |
| `uploaded.json` | Vom eigenen Endpunkt bestätigte Datensatz-IDs |
| `salt-results.jsonl` | Später automatisch gefundene Struktur-Salts mit Welt-/Serverbezug |
| `observations-*.json` | Mit `/egox export` gespeicherte Beobachtungen |
| `traces/*.json` | Eingabedateien für Feature-Suchen |
| `library/<ID>/session.json` | Automatischer Arbeitsstand einer Spieler-/Server-/Hash-/Versions-Kombination |
| `library/<ID>/session.previous.json` | Vorherige Sicherung als Rückfallebene |
| `library/<ID>/observations.jsonl` | Laufende lokale Rohdatenbank einschließlich Erzereignissen und Strukturerkennungen |

Die eigene Community-Tabelle und der Upload-Endpunkt sind vorkonfiguriert. `autoUpload` ist standardmäßig aktiv. Neue, bestätigte **Multiplayer**-Funde werden automatisch übertragen; Singleplayer-Funde werden lokal gespeichert. Spielernamen und Minecraft-Login-Tokens werden nicht erfasst. Ausfälle werden lokal gepuffert und im Minutentakt erneut versucht.

Google-Einsendungen sind zunächst `UNREVIEWED`: Der Endpunkt prüft Seed/Hash und Zahlenpräzision, kann die behauptete Zugehörigkeit zu einer Serveradresse aber nicht unabhängig beweisen. Später gefundene Salts werden separat lokal gespeichert; vorhandene Tabellenzeilen werden durch öffentliche Einsendungen nicht überschrieben.

Die Beobachtungsbibliothek ist lokal und pro Minecraft-Spieler-UUID getrennt. Sie enthält auch die beim Verbindungsaufbau beobachtete Serveradresse/IP. Erz- und Struktur-Koordinaten sowie Spieler-UUIDs werden nicht in die öffentliche Google-Tabelle hochgeladen. Das Rohjournal besitzt kein festes Ereignislimit; der aktive Arbeitsspeicher hält bis zu 4096 Erzpositionen, 8192 Feature-Blöcke und 1024 Bedrock-Funde je Overworld/Nether. [Bibliothek und Restore](docs/LIBRARY.md) beschreibt Format, Rückfallebenen und Grenzen.

## Technische Grenzen

Unbekannte Salts lassen sich nicht allgemein wegzaubern: Bei Random-Spread-Strukturen wirkt `Seed + Salt` auf denselben Zufallsgenerator. Sind der Seed und sämtliche Salts unbekannt, können dieselben Strukturpositionen mehrere Kombinationen beschreiben. Die automatische Salt-Suche benötigt deshalb einen bereits bestätigten Welt-Seed und mindestens fünf Starts derselben Placement-Familie.

Biomdaten können Kandidaten unabhängig von den Struktur-Platzierungsregeln überprüfen. Eine unbegrenzte, schnelle Inversion sämtlicher 2^64 Welt-Seeds aus beliebigen Biomen ist hier **nicht implementiert**. Der automatische Biom-Fallback sucht die oberen 16 Bits vorhandener unterer 48-Bit-Kandidaten; eine manuelle Biom-Suche ist zusätzlich in vorgegebenen vollständigen Seed-Bereichen möglich. Ein unbekannter Salt kann bereits die unteren Bits des aus Strukturen abgeleiteten Seeds verschieben. Fehlt dadurch der echte untere Welt-Seed im Kandidatenpool, repariert die obere-16-Bit-Biom-Suche diesen Fehler nicht. Eigene Datapacks, Custom-Generatoren, alte/neue Chunk-Mischungen oder ein anderes Biom-Preset können Ergebnisse verhindern.

Feature-Traces müssen die tatsächlich ausgeführten RNG-Aufrufe in korrekter Reihenfolge enthalten. Die Mod rekonstruiert diese versteckten Aufrufe derzeit nicht automatisch aus beliebigen Erzen, Bäumen oder Dungeons. Native Vorlagen werden aus dem installierten Spiel gelesen und nicht im Mod-JAR mitgeliefert. Template-Prüfungen sind konservativ: beschädigte, überbaute oder durch Server-Prozessoren veränderte Teile können ausfallen. Outlines beziehen sich auf erkannte Teile, nicht auf garantiert vollständige Grenzen mehrteiliger Anlagen. Details: [Weltgenerierung](docs/GENERATION.md), [Trace-Format](docs/FEATURE_TRACES.md).

Es gibt noch keinen belastbaren Vergleichsbenchmark, der eine generelle Überlegenheit bei Geschwindigkeit oder Erkennungsumfang belegt.

## Selbst bauen

JDK 21 und JDK 25 installieren. Unter Windows:

```powershell
./scripts/build.ps1 -Jdk25 'C:/Pfad/zum/jdk-25' -FullRecovery
```

Die beiden JARs und Prüfsummen landen in `dist/`. Ohne `-FullRecovery` werden die aufwendigen Vollraumsuchen in den Tests übersprungen. Linux/macOS: die jeweiligen Wrapper in `versions/1.21.11/` und `versions/26.3/` mit `build -PegoxFullRecovery` aufrufen.

Der Code ist unter `shared/src/main/java/de/egox/seedcracker/` organisiert; Minecraft-spezifische Adapter liegen in den jeweiligen Versionsordnern. [Tests und Prüfumfang](docs/TESTING.md) · [Google-Backend](database/README.md)
