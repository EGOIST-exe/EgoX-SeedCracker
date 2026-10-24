# Prüfumfang

Stand: 24. Oktober 2026. Beide Varianten werden unabhängig gebaut und getestet; die 1.21.11-Tests laufen mit Java 21, die 26.3-Tests mit Java 25.

Release 0.6.1: **91 Tests je Minecraft-Version, insgesamt 182, ohne Fehler oder übersprungene Tests** mit `-PegoxFullRecovery`. Die fertigen JARs werden zusätzlich auf Mod-ID, Version, Mixin-Datei/-Klassen, optionale Mod-Menu-Anbindung, unveränderte Logo-PNG und fehlende fremde Mod-/Minecraft-Inhalte geprüft. HUD-Vorschauen aus den tatsächlichen Java-Canvas-Zeichenoperationen sind Layoutprüfungen und keine Ingame-Screenshots. Die Bedienung einer gestarteten Minecraft-Clientinstanz ist weiterhin nicht vollständig getestet.

Geprüft werden:

- 100 Dungeon-Funde plus Wiederholungsscans ohne initialisierten Chat-Client: Funde bleiben gespeichert, nur neue Muster ändern die Datenrevision. Die Übersicht zeigt positive Muster-Bits ohne die missverständliche `+0`-Anzeige; mehrere Räume desselben Chunks addieren weiterhin höchstens einen Musterwert.

- Alle 32 Kombinationen der fünf HUD-Zeilen an allen vier Positionsecken bei verschiedenen GUI-Breiten und -Höhen: kein Zeichnen außerhalb des Bildschirms, abgeschaltete Zeilen verschwinden, das Panel passt sich an. Ein komplett ausgeschalteter HUD-Inhalt zeichnet nichts.
- Hintergrund 0 % entfernt alle Glas-, Rahmen- und Schatten-Layer; aktivierte Texte/Balken bleiben erhalten. 100 % enthält eine vollständig deckende Panel-Fläche.
- Maus-Griffpunkt ohne Sprung, Drag-Grenzen, Freigeben und anschließende Inaktivität; Verschiebung durch Buttons, normierte Position nach Größenwechsel, Reset, serialisierte Einstellungen und ungültige Werte.
- Paginierte Menü-Geometrie lässt Footer-/Navigationsplatz frei. Native Pointer-/Slider-Verträge und optionale Mod-Menu-Metadaten sind für beide Minecraft-Versionen vorhanden; `/egox settings` und `/egox gui` sind registriert.

- Native `MonsterRoomFeature`-Böden in beiden Höhenbereichen, bei negativen Chunks und signed-long-Grenzwerten, einschließlich echter Truhen-/Loot- und Mob-Zufallsaufrufe; vorherige erfolgreiche, durch Terrain verworfene und vom Biomfilter übersprungene Versuche. Der konservative Filter behält den echten Seed und verwirft einen anderen. Ein Spawner allein oder ein zerstörter zentraler Bodenblock wird nicht als nutzbares Muster erkannt.
- Vollständige obere-16-Bit-Suche ohne Hash mit einem nativ erzeugten Dungeon-Boden: 65.536 Kandidaten werden geprüft und nur der erwartete Welt-Seed bleibt im Fixture. Dies setzt den richtigen unteren 48-Bit-Kandidaten voraus und belegt keine unabhängige 64-Bit-Dungeon-Inversion.
- Dungeon-Muster-Bits gegen die native 1/4-/3/4-Verteilung, native Platzierungsparameter, Rejection-Grenzfall sowie Journal-/Snapshot-/Restore-Deduplizierung und explizite negative Chunk-Koordinaten.
- Native Nether-Biome über 256 räumlich gestreute Koordinaten: mehrere Biomtypen, unveränderte Nether-Ergebnisse bei anderem Overworld-Preset, Ausschluss eines anderen unteren Seeds und Erhalt der oberen-16-Bit-Mehrdeutigkeit. Gemischte Dimensions-Samples bleiben getrennt; ältere Biom-JSONs ohne Dimension werden als Overworld gelesen. Wiederholte lange Erkundung einer Dimension verdrängt im begrenzten Arbeitssatz nicht alle Proben der anderen Dimension.

- Lokale Bibliothek mit exakten signed-long-Seeds, negativen Block-/Chunk-Koordinaten, Salt-Overrides und Säulenhöhen; Trennung nach Spieler, Server, Hash und Minecraft-Version.
- Journal-Replay nach dem letzten Snapshot, ohne doppeltes Zählen; Wiederherstellung vor dem ersten Snapshot und nach einer unvollständigen letzten Zeile; Fallback von beschädigtem neuesten Snapshot auf vorherige Sicherung plus Journal.
- Jedes wiederholte Erz-Abbauereignis bleibt im Journal; hostile Servernamen werden ausschließlich als Daten gehasht. Dimensionswechsel lassen einen laufenden Restore gültig; Verlassen entwertet alte Ladeaufträge, auch beim sofortigen Wiederbeitritt zum selben Server, und überschreibt keine Sicherung. Singleplayer-Weltpfade gelangen nicht in den öffentlichen Outbox-Zweig.
- Einmalige Migration auf Biom-/Erz-Checker OFF ohne spätere ON-Entscheidungen, HUD oder Reichweite zurückzusetzen.
- Kopierbarer Xoroshiro-Worldgen-RNG gegen native gemischte `nextInt`, `nextFloat`, `nextDouble`, einschließlich Rejections; Smaragderz-Geometrie und frühe Terrain-Ausfälle gegen native Feature-Aufrufe. Replay behält nativ erzeugte Beobachtungen mit unbekannten übersprungenen Versuchen.
- Bedrock-Fabriken gegen native `RandomState` in Overworld und Nether, einschließlich signed-long-Grenzwerten; zusätzliche vollständige obere-16-Bit-Suche mit nativer Bedrock-Testbeobachtung ohne Hash. Verbleibende Mehrdeutigkeit bleibt erhalten und wird erst durch den Hash aufgelöst.
- Geoden-Ursprünge gegen native Rarity-/InSquare-/UniformHeight-Modifikatoren; Kandidatenbedingungen und vollständige obere-16-Bit-Suche mit synthetischen Beobachtungen innerhalb nativ modellierter Platzierungsboxen. Dies ist kein Test vollständiger Geodenformen oder eines kompletten Weltgenerators.
- Erz-Klassifikation, Serverkorrekturen, Ausschluss unbestätigter/abgelehnter Erzproben; native Mixin-Zielmethoden für Client-Abbau und Blockupdates beider Versionen.
- Markierungen unterschiedlicher Geoden-Höhenzellen, Feature-Blöcke und Analyseberichte bleiben beim Journal-Replay getrennt erhalten.

- Legacy-RNG gegen `java.util.Random`, einschließlich häufiger Rejections bei großen Bounds.
- Seed-Hash gegen Minecraft `BiomeManager.obfuscateSeed`, auch für `Long.MIN_VALUE` und `Long.MAX_VALUE`.
- LINEAR-/TRIANGULAR-Platzierung gegen 2.000 native Minecraft-Fälle mit positiven/negativen Koordinaten und zufälligen signed-int-Salts.
- Lifting gegen eine unabhängige erschöpfende Suche in einem begrenzten Bereich sowie ein gezielt konstruiertes Rejection-Beispiel.
- Vollständige 48-Bit-Rekonstruktion plus 16-Bit-Hash-Lifting aus nativen Testpositionen mit vorgegebenen geänderten Salts und zusätzlichen Strukturbedingungen.
- Wiederherstellung eines unbekannten Struktur-Salts durch eine vollständige Suche über alle 2^32 signed-int-Werte bei bekanntem Welt-Seed und zwölf nativen Testpositionen.
- Struktur-Signaturkoordinaten gegen Minecraft-Structure-Pieces in allen vier Ausrichtungen; intakte und beschädigte Muster.
- Native Biom-Prüfung für alle drei angebotenen Presets und Zurückweisung eines anderen Seeds.
- Vollständige automatische obere-16-Bit-Enumeration mit nativer Biom-Prüfung ohne Hash: alle 65.536 Kombinationen eines bekannten unteren Seeds werden geprüft und ergeben den erwarteten signed-long-Welt-Seed.
- Native Biom-Kandidatensuche mit zwei unteren Kandidaten und sechs vollständigen Seeds bei absichtlich widersprechendem Vergleichs-Hash. Kandidaten behalten `HASH_FEHLT`, `HASH_PASSEND` oder `HASH_WIDERSPRUCH` und exakte signed-long-Strings.
- Hash-Lifting über alle oberen 16 Bits für signed-long-Grenzwerte; 1708 untere Kandidaten werden in einer begrenzten oberen Testspanne ohne die frühere 1000er-Kappung vollständig geprüft.
- Vielfältige frühe Biom-Samples mit räumlicher Streuung; anschließend wird der vollständige Snapshot geprüft. Ein Widerspruch außerhalb der frühen Samples wird nicht übersehen.
- Abbruch vor und während einer oberen Suche, Ergebniskappung, echte Prädikatfehler, ungültige Bereiche und Deduplizierung unterer Kandidaten. Teil-/Fehlersuchen werden nicht als abgeschlossen gemeldet.
- Sitzungswechsel verwerfen alte Biom-Worker-Berichte einschließlich ihrer Kandidaten.
- Paper-Feature-Seed-Replay gegen mit dem nativen RNG erzeugte Testdaten.
- Befehlsbaum ausschließlich unter `/egox`, einschließlich `/egox discord`.
- Suchabbruch, Fehler, Ergebnisbegrenzung und Bereichsüberlauf.
- Alle geladenen Startvorlagen einschließlich Paletten, Drehungen und Jigsaw-Startversatz gegen Minecraft `StructureTemplate.filterBlocks`; künstlich beschädigte Signaturen werden verworfen.
- Monument-Finder gegen die tatsächlich ausgeführte native Erzeugung der Eingangsarchitektur in allen vier Ausrichtungen.
- End-Säulen-Permutation gegen den nativen Spike-Cache-Loader; inverse RNG-Zuordnung gegen 10.000 signed-long-Seeds und `java.util.Random.nextLong`.
- Vollständige Suche über alle 2^32 verbleibenden Säulen-Zustände mit zehn nativ erzeugten triangulären Endstadt-Positionen.
- Theoretische TRIANGULAR-Bits gegen vollständig ausgezählte Ziehungswahrscheinlichkeiten, Regions-/Dimensions-Deduplizierung und Ausschluss unzulässiger Power-of-two-Lifting-Annahmen.
- Wiederholungsplan für einzelne Placement-Familien, damit fremde Salt-Abweichungen die Kandidaten nicht endgültig verwerfen.
- Phasenfortschritt, Zählgrenzen, gemeinsame Befehlsgruppen, ON/OFF-Schalter und HUD-Geometrie bei verschiedenen GUI-Breiten.
- Einmaliger Wechsel älterer Konfigurationen auf HUD OFF / 512 Blöcke, ohne Upload-Einstellungen oder spätere Nutzerentscheidungen zu überschreiben.
- Genau zwei ON/OFF-Vorschläge bei weiterhin erlaubter Kleinschreibung; eindeutige Outline-Farben für alle geladenen Struktur-IDs und Zusatzmarkierungen.
- Einzelne Outline-Schalter, standardmäßig ausschließlich Geoden OFF, persistierbare Typ-Auswahl und Migration von 0.3.0 ohne Zurücksetzen gewählter HUD-/Reichweiten-/Globalschalter.
- 600 ausgeblendete Geoden verdrängen kein sichtbares Schiffswrack aus dem 512-Marker-Limit; Filterung entfernt keine gespeicherten Beobachtungen.
- Schiffswrack-Katalog gegen die nativen Ozean-/Strand-Vorlagenlisten: alle 20 Vorlagen und sämtliche Drehungen. Der indexierte Finder wird zusätzlich für jede enthaltene Holzpalette/Drehung gegen native Template-Blöcke geprüft.
- Mehrere Spawner im selben Chunk behalten getrennte Positionen. Visuelle Zusatzfunde werden nicht als Random-Spread-Starts registriert.
- Vanilla-äquivalente Platzierungsseeds reproduzieren nativ erzeugte Trial-Chambers-Positionen mit geändertem Salt; unbekannte widersprüchliche Salts ergeben keinen behaupteten Platzierungsseed.
- 39 lokale Google-Backend-Prüfungen einschließlich exakter 64-Bit-Werte, Hash-Prüfung, Formel-Escaping und ungültiger Eingaben.

Der veröffentlichte Google-Endpunkt wurde zusätzlich mit einem eindeutig gekennzeichneten Testdatensatz angesprochen. Der kleinste signed 64-Bit-Seed blieb unverändert, und ein erneuter POST wurde als Duplikat erkannt. Der Testdatensatz wurde danach entfernt.

Die gemessenen Zeiten für synthetische Vollraum-Fixtures sind keine allgemeine Vorhersage für reale Server und kein Vergleichsbenchmark. Korrelationen, Datengenauigkeit, CPU, Strukturarten und Generatoränderungen können den Aufwand stark ändern.

Noch nicht durchgeführt: vollständige Bedienung in einer gestarteten Minecraft-Clientinstanz, längere reale Server-Sitzungen, Vanilla-/Paper-Vergleichswelten mit administrativ kontrollierten Änderungen sowie Lasttests der öffentlichen Google-Instanz. Die Tests belegen die implementierten Suchmodelle, nicht die zuverlässige Erkennung beliebiger Custom-Generatoren.
