# Weltgenerierung und Suchmodelle

## Strukturen

EgoX verwendet für Random-Spread-Platzierungen das Legacy-RNG mit 48-Bit-Zustand. Der Eingabeseed einer Region lautet:

```text
worldSeed + regionX * 341873128712 + regionZ * 132897987541 + salt
```

Regionen werden mit `floorDiv(chunk, spacing)` bestimmt. Die Differenz `spacing - separation` bestimmt die Offset-Grenze. LINEAR verwendet einen `nextInt(bound)`-Wert pro Achse; TRIANGULAR den ganzzahligen Mittelwert zweier solcher Werte. Der entsprechende Seed-/Salt-Ansatz ist auch im [Paper-Quellcode zu WorldgenRandom](https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/world/level/levelgen/WorldgenRandom.java.patch) sichtbar.

Spacing, Separation und Verteilung sind getrennte Parameter. Ein nicht passendes Gebäude beweist deshalb noch keinen geänderten Salt. EgoX meldet die Abweichung, statt einen neuen Salt ohne Suchbeleg zu behaupten. Frequenzregeln, Biomeignung, Exclusion-Zones und die tatsächliche Bauwerksgenerierung sind zusätzliche Bedingungen; das Placement-Modell ist ein Positionsfilter, kein vollständiger Struktur-Generator.

Ein unbekannter 32-Bit-Salt lässt sich bei bekanntem Welt-Seed über alle signed-int-Werte prüfen. Ein unbekannter Welt-Seed plus unbekannte additive Salts besitzt dagegen eine Verschiebungsmehrdeutigkeit. Zusätzliche unabhängige Informationen sind erforderlich.

## Lifting

Bei einer geraden Offset-Grenze, die keine Zweierpotenz ist, liefern die niedrigen Bits der ersten Zufallswerte eine schnelle Vorauswahl niedriger Seed-Bits. Zweierpotenzen verwenden im nativen `nextInt` skalierte hohe Bits und werden von diesem Lifting-Weg ausgeschlossen. Anschließend werden die verbleibenden hohen Bits mit wiederverwendeten RNG-Instanzen geprüft. Das vermeidet Objektallokation pro Kandidat.

`java.util.Random.nextInt(bound)` kann einzelne rohe Zufallswerte verwerfen. Eine reine Modulo-Vorauswahl wäre in diesen seltenen Fällen unvollständig. EgoX invertiert deshalb zusätzlich alle möglichen ersten/zweiten Rejection-Zustände und prüft deren Kandidaten vollständig. Tests enthalten gezielt einen solchen Fall.

Die einfache Summe theoretischer Platzierungsbits garantiert keine unabhängigen Beobachtungen. Insbesondere ähnliche Regionen/Strukturarten können stark korrelieren. Mehrere verbleibende Seeds werden als Kandidaten behalten.

## Welt-Seed und Biome

Der übertragene Biom-Zoom-Hash ist die als Little-Endian-Long gelesene erste Hälfte von `SHA-256(littleEndian(worldSeed))`. Für einen bekannten unteren 48-Bit-Seed bleiben 65.536 obere Kombinationen. EgoX prüft diese gegen den Hash, bevor es einen Welt-Seed veröffentlicht.

Chunk-Biome werden in Quart-Koordinaten gelesen. Dadurch wird die zusätzliche seedabhängige Biom-Zoom-Stufe nicht versehentlich in den Vergleich aufgenommen. Für das jeweilige Vanilla-Preset erzeugt EgoX native `RandomState`-/`MultiNoiseBiomeSource`-Instanzen der tatsächlich gestarteten Minecraft-Version. Die 26.3- und 1.21.11-APIs besitzen dafür getrennte Adapter. Versionsänderungen sind in den offiziellen [Minecraft-26.3-Notizen](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) und [Fabric-Hinweisen](https://www.fabricmc.net/2026/09/15/263.html) beschrieben.

Die native Biom-Suche arbeitet mit Kandidaten/Bereichen. Sie ist kein allgemeiner Algorithmus zur schnellen Umkehrung des gesamten 64-Bit-Noise-Generators.

## Paper-Features

Papers `feature-seeds` überschreiben den Eingang für die Population eines Features. Das unterscheidet sich von einem additiven Struktur-Salt. Die [Paper-Weltkonfiguration](https://docs.papermc.io/paper/reference/world-configuration/#feature-seeds) beschreibt diese Einstellungen; der [ChunkGenerator-Patch](https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/world/level/chunk/ChunkGenerator.java.patch) zeigt `setDecorationSeed(configFeatureSeed, originX, originZ)` vor `setFeatureSeed(populationSeed, globalIndexOfFeature, stepIndex)`.

EgoX führt diese beiden Operationen für PAPER_SEED-Traces mit dem nativen Xoroshiro-RNG aus. DECORATOR_SALT prüft stattdessen einen additiven Decorator-Seed `populationSeed + candidate`, also den bereits zusammengefassten Wert aus Index und Schritt. Die Rekonstruktion versteckter RNG-Traces aus beliebigen Blöcken ist eine weitere, hier noch offene Aufgabe.
## Finder, Bits und Darstellung in 0.2.0

Finder lesen Startvorlagen direkt aus den nativen Minecraft-Ressourcen. Eine Palette wird als eigene Variante behandelt; vier Drehungen werden mit Minecraft `StructureTemplate.transform` gebildet. Bei benannten Jigsaw-Starts wird der Startanker zurückgerechnet. Außenposten verwenden die tatsächlichen Jigsaw-Verbindungen zwischen leerer Startplatte und Wachturm. Die gemeinsame Prüfung mehrerer räumlich verteilter Marker liefert einen Start-Chunk; fehlende Nachbarchunks lösen später einen erneuten Versuch aus. Die initiale Vorlagenvorbereitung läuft im Hintergrund. Chunk-Scans sind fortsetzbar und haben ein ungefähres Budget von 2 ms pro Client-Tick.

Platzierungsbits sind `-log2(P(Position))`: bei LINEAR gleichverteilt, bei TRIANGULAR aus den Anzahlen der zwei möglichen Summen der ganzzahligen Mittelung. Dieselbe Placement-Region wird nur einmal gezählt. Ein angezeigtes Bit-Ziel ist keine Erfolgswahrscheinlichkeit; korrelierte Beobachtungen, falsche Startkoordinaten und unbekannte Salts können diese theoretische Information entwerten.

Die Säulenreihenfolge im zentralen End wird gegen die 65.536 möglichen Säulen-Seeds geprüft. Für jeden passenden Wert lassen sich die übrigen 32 Bits des zweiten LCG-Zustands vollständig aufzählen und durch zwei inverse LCG-Schritte in 48-Bit-Struktur-Seeds umrechnen. Deshalb können auch TRIANGULAR-Platzierungen aus Endstädten einen vollständigen Suchweg bilden. Die obere Welt-Seed-Hälfte wird anschließend gegen den übertragenen Hash geprüft.

Outlines verwenden die nativen Minecraft-Gizmos in der jeweils richtigen Sammelphase. Minecraft 1.21.11 sammelt sie im `LevelRenderer`, 26.3 im `LevelExtractor`. Die HUD-Anbindung nutzt Fabric `HudElementRegistry`; die Zeichenoperationen liegen in getrennten Versionsadaptern. Outlines zeigen beobachtete Gebäudeteile und bleiben auch durch verdeckende Blöcke sichtbar. Der Radius und die Zahl sichtbarer Marker sind begrenzt. F1 blendet die Anzeige aus.

Die Fortschrittszählung umfasst erledigte Kandidaten je Suchphase. Der 48-Bit-Weg zeigt Vorfilter, Kandidatenprüfung, Rejection-Sonderfälle und Hash-Prüfung getrennt. Die Restzeitschätzung gilt für die aktuelle Phase und kann sich bei wechselnder CPU-Last ändern. Bei Salt-Abweichungen kann die Lifting-Suche weitere, separat ausreichend belegte Placement-Familien versuchen; solche neuen Versuche haben eigene Phasen.

## Darstellung und Einzelübersicht ab 0.3.0

Das HUD ist standardmäßig aus und wird als kompakte, zentrierte Glaskarte gezeichnet. Die Glaswirkung entsteht durch transparente Schichten, runde Kanten, Verlauf und Reflexkante; der Hintergrund der Spielwelt wird dabei nicht weichgezeichnet. Die volle Chat-Diagnose bleibt mit `/egox cracker status` verfügbar.

Spawner, Trial-Spawner und Vaults werden beim Chunk-Empfang über Block Entities und ergänzend beim Blockscan erfasst. Geoden-Fundbereiche sammeln beobachtete Amethystdrusen in 32-Block-Zellen. Sie sind eine sichtbare Fundzone, keine Rekonstruktion der ursprünglichen Geodengeometrie. Diese visuellen Daten fließen weder in Platzierungsbits noch in die automatische Struktur-Suche ein.

Seit 0.3.1 speichert `disabledOutlineTypes` einzelne ausgeblendete Typen; nur `egox:geode` ist standardmäßig enthalten. Der Filter läuft vor Sortierung und dem 512-Marker-Limit. Ausgeblendete Strukturbeobachtungen bleiben für den Cracker erhalten. Geoden werden bei ausgeschaltetem Einzelschalter nicht zusätzlich gesammelt; Einschalten stößt einen erneuten Scan geladener Chunks an. Das Update von 0.3.0 bewahrt bereits gewählte HUD-, Reichweiten- und globale Outline-Einstellungen. Die Struktur-, Seed-, Hash- und Salt-Prüfungen bleiben unverändert.

## Automatische Biom-Auswertung in 0.4.0

`WorldSeedLifting` führt die vollständigen 65.536 oberen Kombinationen je unterem Seed auf Hintergrund-Workern aus. Hash-Prüfungen überspringen keine Liste mehr aufgrund der früheren 1000-Kandidaten-Grenze. Nach einer eindeutigen Hash-Bestätigung aus Struktur- oder Säulen-Suche prüft `AutomaticBiomes` den Seed gegen alle Biom-Samples des Such-Snapshots. Ein Biom-Widerspruch wird mit Trefferzahl und Preset gemeldet; er verwirft einen Hash-bestätigten Seed nicht stillschweigend.

Bleiben alle Strukturgruppen ohne Hash-Treffer, sammelt die Automatik ihre unteren Kandidaten und prüft deren obere 16 Bits gegen native Biome, auch bei fehlendem oder widersprechendem Hash. Mindestens acht Samples aus zwei Biomen sind nötig. Bis zu 64 nach Vielfalt und Abstand gewählte frühe Samples dienen nur zur schnellen Ablehnung; ein Treffer muss alle Snapshot-Samples bestehen. Pro Seed wird ein eigener nativer Noise-Zustand erstellt. Der Fallback verwendet höchstens zwei Worker, liefert echten Arbeitsfortschritt und wird bei neuen Strukturstarts abgebrochen. Die Säulen-Suche nutzt denselben Fallback nach erfolgloser Hash-Prüfung.

Der Suchraum bleibt an die vorhandenen unteren 48 Bit gebunden. Unbekannte additive Salts können bereits diese Bits verschieben; es gibt hier keine allgemeine strukturunabhängige Inversion von 2^64 Welt-Seeds. Kosten können erheblich sein, weil native Noise-Initialisierung für jede obere Kombination benötigt wird. Der Sample-Snapshot und das Preset bleiben während eines Versuchs fest. Nach maximal 32 Biom-Treffern wird ein weiterer Treffer als `TRUNCATED` gemeldet; eine solche Suche ist unvollständig. `CANCELLED` und `FAILED` werden getrennt von `COMPLETE` ausgewiesen.

Biom-Kandidaten tragen `HASH_PASSEND`, `HASH_FEHLT` oder `HASH_WIDERSPRUCH`. Ohne passende Hash-Bestätigung bleiben sie nur in der Diagnose und im lokalen Export. Der öffentliche Ergebnis-Upload wird durch einen Biom-Treffer nicht ausgelöst. Beim Sitzungswechsel verhindert ein Generationstoken, dass alte Worker Biom-Berichte in die neue Sitzung übertragen.

## Bibliothek und Feature-Bedingungen in 0.5.0

Die [lokale Bibliothek](LIBRARY.md) führt ein fortlaufendes Rohjournal und atomare Arbeitsstände pro Spieler-/Server-/Hash-/Versions-Kombination. Sie speichert auch erkannte Strukturstarts außerhalb der erwarteten Regionregeln, ohne diese als gültige Lifting-Beobachtung zu zählen. Beim erneuten Beitreten werden Daten und sitzungsbezogene Salt-Overrides wiederhergestellt; laufende CPU-Suchen starten neu. Ein wiederhergestellter Welt-Seed benötigt erneut einen passenden Hash.

Biom- und Erz-Checker sind ab 0.5.0 standardmäßig OFF; Sammlung und Journaling bleiben aktiv. Bedrock- und Geoden-Checker sind ON. Geoden-Outlines bleiben separat OFF. Bei deaktiviertem Biom-Checker nutzt der automatische obere-16-Bit-Fallback verfügbare Feature-Bedingungen; bei aktiviertem Biom-Checker werden sie vorgeschaltet. Änderungen an Strukturen unterbrechen den Versuch. Hash-bestätigte Welt-Seeds werden bei einem Feature-Widerspruch ausdrücklich gemeldet und nicht stillschweigend verworfen.

Bedrock-Fabriken werden aus den passenden Vanilla-Legacy-/Xoroshiro-Zweigen gewonnen und gegen native `RandomState`-Fabriken geprüft. Geoden liefern Seltenheits-, Positions- und Bounding-Box-Bedingungen. Das Smaragderz-Replay unterstützt zunächst das native Size-3-Feature ohne Air-Discard, inklusive unbekannter Ausfälle zwischen Versuchen. Alle drei Modelle sind Kandidatenbedingungen unter Vanilla-Annahmen; keiner behauptet eine allgemeine Inversion unbekannter Feature-Seeds oder beliebiger Servergeneratoren. Spawner-Bodenmuster und andere Erze werden zunächst nur als Rohdaten gespeichert. Details und Befehle stehen in [LIBRARY.md](LIBRARY.md).

Die Einzelübersicht `/egox db seeds <Struktur>` verwendet `(worldSeed + salt - vanillaSalt) & ((1L << 48) - 1)`. Dies ist ein Vanilla-äquivalenter 48-Bit-Platzierungsseed, kein neuer 64-Bit-Welt-Seed. Overrides werden als gesetzt/ermittelt bezeichnet, unveränderte Standardwerte als Annahme. Ohne bekannte Grundlage beziehungsweise bei widersprüchlichen Standard-Salts bleibt der Seed unbestimmt. Unabhängige Paper-Feature-Seeds lassen sich daraus nicht ableiten.

## Dungeon-Bodenmuster und Nether-Biome in 0.5.1

Ein beliebiger Mob-Spawner wird nicht als Dungeon oder Strukturstart gezählt. Der Dungeon-Finder verlangt einen vollständigen Cobblestone-/Moosstein-Boden einer nativen Raumgröße, feste Unterstützung darunter, passende Cobblestone-Wände mit ein bis fünf Öffnungen und plausiblen Innenraum. Fehlende Nachbarchunks, beschädigte Böden und fremde Spawner-Anlagen liefern keine Muster. Die Erkennung kann natürliche Herkunft nicht beweisen; Spielerbauten oder spätere Änderungen können das Modell verletzen.

Bei `MonsterRoomFeature` ist ein verwendeter Bodenwurf `nextInt(4)`: Cobblestone hat Wahrscheinlichkeit 1/4, Moosstein 3/4. Daraus folgen **bedingt auf einen intakten natürlich generierten Raum** 2 Bits pro Cobblestone und `-log2(3/4)` Bits pro Moosstein. Mehrdeutige Raumgrößen verwenden für die Anzeige den kleinsten Musterwert; pro Chunk wird höchstens ein Musterwert addiert. Diese Modellinformation ist keine Anzahl rekonstruierter Seed-Bits. Sie wird separat unter `/egox db bits` angezeigt, nicht mit Struktur-Lifting-Bits zusammengerechnet. Der HUD-Fußtext verweist bei vorhandenen Dungeons auf diese Übersicht.

Der moderne Kandidatenfilter liest Versuchsanzahl, Höhenbereich und globale Feature-Indizes aus dem installierten Spiel. Er benutzt Xoroshiro/WorldgenRandom, nicht das alte 48-Bit-Decorator-Modell. Aus einem Welt-Seed werden Population- und Feature-Seed gebildet. Vorherige unbekannte Biome-/Terrain-Ausfälle und erfolgreiche Räume werden durch alle möglichen Rohstream-Offsets innerhalb einer konservativen Hülle überapproximiert. Je vorherigem Versuch sind drei Placement-Aufrufe und höchstens 100 Feature-Rohaufrufe ohne Rejections möglich: zwei Raumradien, bis zu 81 Bodenwürfe, zwölf Truhenkoordinaten-Würfe, vier Loot-Seed-Aufrufe und ein Mob-Wurf. Kandidaten müssen eine mögliche Spawnerposition, passende Radien und das vollständige Bodenmuster erklären. Der Prüfer prüft auf mögliche Rejections für alle relevanten nichtpotenzzweibasierten Bounds und behält solche Kandidaten vorsichtshalber bei. Die Hülle wird dadurch nicht zu einer stillen Falschausschlussgrenze. Verschiedene Raumbeobachtungen dürfen durch verschiedene mögliche Verläufe erklärt werden.

Dies ist ein notwendiger Kandidatenfilter unter dem Modell, kein vollständiger Terrain-Replay oder Inversionssolver über beliebige Xoroshiro-Zustände. Im automatischen Fallback werden bis zu acht informative Dungeons für die oberen 16 Bits vorhandener unterer 48-Bit-Kandidaten verwendet; `/egox cracker dungeon search <Min> <Max>` prüft alternativ einen begrenzten vollständigen Seed-Bereich. Ein unabhängig gesetzter Paper-Feature-Eingang kann vom Welt-Seed abweichen. Ein Dungeon-Widerspruch verwirft keinen bereits Hash-bestätigten Welt-Seed.

Die Biom-Beobachtung enthält ab 0.5.1 eine Dimension. Getrennte native MultiNoise-Quellen und Noise-Settings prüfen Overworld- und Nether-Samples; das Overworld-Preset beeinflusst den Nether nicht. Vanilla-Nether-Noise verwendet den Legacy-Zweig. Tests mit identischen unteren 48 Bits und verschiedenen oberen 16 Bits behalten deshalb dieselben Nether-Biome. Nether-Bedrock und Nether-Biome können Kandidaten prüfen, bestimmen diese oberen Bits allein jedoch nicht. Ein moderner Wirrpilz-Inversionssolver ist nicht implementiert; Pilzformen bekommen keine behaupteten Bits gutgeschrieben. Biom- und Erz-Checker bleiben standardmäßig OFF.
