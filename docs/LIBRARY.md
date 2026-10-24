# Lokale Bibliothek ab 0.5.0

Die Bibliothek liegt in `config/egoxseedcracker/library/`. Ein SHA-256-Schlüssel aus Spieler-UUID, eingegebener Serveradresse, übertragenem Seed-Hash und Minecraft-Version bestimmt den Unterordner. Dimensionsdaten bleiben innerhalb derselben Welt getrennt gekennzeichnet. Singleplayer verwendet den lokalen Weltpfad; solche Datensätze werden nicht öffentlich hochgeladen. Unterschiedliche Spieler, Hashes und Clientversionen werden nicht automatisch zusammengeführt. Maskiert ein Server den Hash oder behält ihn nach einem Weltwechsel unverändert bei, ist er kein verlässliches Weltwechselmerkmal.

## Speicherung

- `observations.jsonl`: fortlaufende einzelne JSON-Ereignisse mit Schema, UTC-Zeit, Typ und Daten. Das Journal wächst mit den Beobachtungen und hat kein Ereignislimit.
- `identity.json`: lokale Spieler-/Server-/Hash-/Versions-Zuordnung, auch vor der ersten Sicherung verfügbar.
- `session.json`: aktueller Arbeitsstand mit Identität, Strukturen, begrenztem Biom-/Block-Arbeitssatz, Salts, optionalem Hash-bestätigtem Welt-Seed, Markierungen, Säulenhöhen und Analyseberichten.
- `session.previous.json`: vorherige Sicherung als Rückfallebene.

Dateiarbeit läuft auf einem eigenen Worker. Snapshot und Journal werden vor dem atomaren Austausch des Arbeitsstands synchronisiert. Ein Byte-Offset bezeichnet die bereits enthaltenen Journalereignisse. Beim Laden werden neuere Ereignisse nachgespielt. Ein beschädigter neuester Snapshot kann durch die vorherige Sicherung plus Journal ersetzt werden. Ist kein lesbarer Snapshot vorhanden, lassen sich Rohdaten aus dem Journal rekonstruieren. Eine unvollständige letzte Zeile wird übersprungen und vor dem nächsten Append abgetrennt.

Der Arbeitsstand wird bei Änderungen alle fünf Sekunden, beim Serverwechsel/Verlassen und beim regulären Beenden gesichert. Ein noch ladender oder nicht lesbarer Stand überschreibt keine vorhandene Sicherung. Beim Wiederbeitritt werden neue und gespeicherte Beobachtungen zusammengeführt; automatische Suchen warten auf die Wiederherstellung. Ein Welt-Seed aus einer Sicherung wird erneut gegen den aktuellen Hash geprüft.

Wiederhergestellt werden **Beobachtungen und Einstellungen der Welt**, keine exakte CPU-Suchposition. Struktur-/Seed-Suchen starten anhand dieser Daten neu. Ein als laufend gespeicherter Analysebericht wird als unterbrochen angezeigt. Globale Checker-Schalter bleiben Benutzerkonfiguration und werden durch Restore nicht wieder eingeschaltet.

## Rohereignisse

| Typ | Inhalt |
| --- | --- |
| `CONNECTION` | Eingegebene Adresse, beobachtete Remote-Adresse, Dimension, Hash und Clientversion |
| `DIMENSION` | Wechsel innerhalb derselben Server-/Hash-Kombination |
| `STRUCTURE_DETECTION` | Erkannter Vorlagentyp, exakter Start-Chunk, Quelle und erwartetes Vanilla-Placement; auch außerhalb der erwarteten Regionregeln |
| `STRUCTURE_START` | Für den aktuellen Solver übernommene Beobachtung |
| `STRUCTURE_CONFLICT` | Ein anderer Start in einer bereits belegten erwarteten Placement-Region |
| `BIOME` | Biom-ID und Quart-Koordinaten |
| `DUNGEON` | Seit 0.5.1: als Monsterraum erkannter Spawner, Overworld-Dimension, Block-/Chunk-Koordinaten und vollständige Bodenvarianten; `0` Cobblestone, `1` Moosstein |
| `SALT` | Struktur-Familie und gesetzter Override |
| `MARKER` | Beobachtete Geometrie und Typ, ggf. einzelne Blockposition |
| `ORE_MINED` | Erz-ID, Blockposition, explizite Chunk-X/Z-Koordinaten, Dimension, Zeitpunkt und `CLIENT_BREAK_PREDICTED` |
| `ORE_SERVER_RESULT` | Später beobachteter Serverzustand: `SERVER_BLOCK_CHANGE` oder `SERVER_REJECTED` |
| `BEDROCK` | Tatsächlich beobachteter Bedrock-Block in einer Übergangsschicht |
| `FEATURE_BLOCK` | Spawner, Trial-Spawner, Vault oder Amethystdruse; keine behauptete natürliche Herkunft |
| `SPAWNER_SURVEY` | Unklassifizierter Spawner und geladener 11×11-Bodenbereich eine Ebene darunter, Reihenfolge X dann Z |
| `PILLARS` | Beobachtete zentrale Säulenhöhen |
| `WORLD_SEED` | Hash-bestätigter vollständiger Welt-Seed als Dezimalstring |
| `FEATURE_CHECK` / `FEATURE_ANALYSIS` | Feature-Gegenprüfung beziehungsweise abgeschlossener Kandidatenbericht |
| `SEARCH_RESULT` | Zustand, geprüfter Bereich und Treffer eines manuell gestarteten Jobs; Seed-Werte als Dezimalstrings |

Der aktive Arbeitssatz hält maximal 4096 Erzpositionen, 8192 Feature-Blöcke, 1024 Bedrock-Positionen pro Overworld/Nether und die konfigurierte Anzahl Biom-Samples (Standard 2048). Wiederholter Abbau derselben Position erzeugt weiterhin einzelne Rohereignisse; die aktive Positionsansicht zeigt den neuesten Stand. Das Journal bleibt für nachträgliche Checks erhalten.

Eine erfolgreiche Client-Abbauvorhersage ist keine Serverbestätigung. Ein späterer Server-Blockwechsel belegt die beobachtete Zustandsänderung, aber weder natürliche Generierung noch zweifelsfrei den Verursacher. Creative, platzierte Erze, Anti-Xray, Plugins und geänderte Weltgenerierung bleiben mögliche Einflüsse. Erz-Replay verwendet nur passend gekennzeichnete Smaragderz-Entfernungen und bleibt eine konservative Kandidatenbedingung.

## Seed-Bedingungen

Bedrock-Checker und Geoden-Checker sind standardmäßig ON. Biom- und Erz-Checker sind standardmäßig OFF; ihr Sammeln/Journaling bleibt aktiv. Eine einmalige Migration schaltet die beiden Checker auch beim Update aus, ohne spätere Entscheidungen zurückzusetzen.

Bedrock verwendet die Vanilla-Übergangsschichten der Overworld und des Nethers. Garantiert flache Ebenen werden nicht als Beobachtungsbedingungen verwendet. Die schnellen Zufallsfabriken sind gegen native `RandomState`-Fabriken geprüft. Nether-Bedrock nutzt den Legacy-Zweig und kann allein die oberen 16 Welt-Seed-Bits nicht bestimmen.

Geoden werden anhand der nativen Seltenheits-, X/Z- und Höhenplatzierung sowie der maximalen Generierungsbox geprüft. Amethystdrusen müssen in einer möglichen Box liegen; die innere Geodenform wird nicht vollständig rekonstruiert. Die Prüfung ist unabhängig davon, ob Geoden-Outlines angezeigt werden.

Das optionale Erz-Replay unterstützt zunächst `emerald_ore` und `deepslate_emerald_ore` im nativen Vanilla-Size-3-Feature ohne Air-Discard. Feature-Index, Schritt und Höhenverteilung werden aus der installierten Version ermittelt. Pro Prüfung werden bis zu 64 bestätigte Positionsänderungen bevorzugt aus tiefen Höhen verwendet. Ein kopierbarer RNG wird gegen Minecraft getestet. Zwischen Versuchen werden fehlgeschlagene Biome-/Terrainprüfungen und vollständige Geometrie verzweigt. Eine großzügige Box umfasst mögliche Erzpositionen; verschiedene Beobachtungen dürfen auch durch verschiedene mögliche Verzweigungen erklärt werden. Ein Treffer ist deshalb **keine exakte Rekonstruktion** und beweist weder Seed noch tatsächlichen Salt. Kandidaten mit fehlendem/widersprechendem Hash bleiben unbestätigt. Andere Erze und Dungeon-Bodenmuster werden bisher nur erfasst; ein allgemeiner Erz-/Dungeon-Inversionssolver ist nicht implementiert.

Automatische Feature-Fallbacks prüfen die oberen 16 Bits vorhandener unterer 48-Bit-Kandidaten. Unbekannte Struktur-Salts können bereits die unteren Bits verschieben; das wird durch diese Filter nicht allgemein repariert. Eigenständige begrenzte Suchläufe sind mit `/egox cracker bedrock search`, `/egox cracker geode search` und `/egox cracker ore search` möglich. `/egox cracker ore feature` prüft stattdessen mögliche Population-Eingangsseeds ohne Welt-Seed-Bestätigung. Paper kann solche Feature-Eingänge unabhängig vom Welt-Seed setzen.

Die lokale Bibliothek wird nicht an die öffentliche Google-Tabelle gesendet. Dorthin gelangen weiterhin nur bestätigte Multiplayer-Welt-Seeds. Die Spieler-UUID dient ausschließlich der lokalen Zuordnung; Login-Tokens werden nicht gelesen oder gespeichert.

## Erweiterung in 0.5.1

`BIOME` trägt jetzt zusätzlich die Dimension. Fehlende Dimension in älteren Sicherungen bedeutet Overworld; damalige Versionen sammelten ausschließlich dort. Nether- und Overworld-Daten gleicher Koordinaten werden getrennt wiederhergestellt. Der aktive Biom-Arbeitssatz bleibt insgesamt auf den konfigurierten Wert begrenzt; überschüssige Samples werden aus der stärker vertretenen Dimension entfernt. Damit verdrängt eine längere Nether-Erkundung nicht sämtliche Overworld-Proben. Das Rohjournal bleibt erhalten. Nether-Biome werden nur bei eingeschaltetem Biom-Checker zur Kandidatenprüfung verwendet.

Das Dungeon-Archiv hält bis zu 1024 intakte Bodenmuster samt zulässigen Raumgrößen im Arbeitsstand. Snapshot und Journal-Replay laden sie auch beim Wiederbeitritt. Unklassifizierte `SPAWNER_SURVEY`-Daten aus 0.5.0 bleiben Rohdaten; sie werden ohne Prüfung der Wände und Unterstützung unter dem Boden nicht nachträglich als gültige Dungeons behandelt. Erneutes Laden/Scannen eines Raums kann ein geprüftes `DUNGEON`-Ereignis hinzufügen. [Weltgenerierung](GENERATION.md) beschreibt die Bedingungen und Grenzen des Kandidatenfilters.
