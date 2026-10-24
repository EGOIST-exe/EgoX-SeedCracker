# Feature-Trace-Format

Dateien unter `config/egoxseedcracker/traces/` ablegen. Ein Trace bezeichnet genau ein Feature und mindestens zwei Samples. Jeder Sample enthält den **Blockursprung eines Chunks** (beide Koordinaten durch 16 teilbar), den globalen Feature-Index, den Generierungsschritt und die exakte Reihenfolge der beobachteten `nextInt(bound)`-Ergebnisse.

Schematisches Beispiel — die Zahlen sind keine aus einer Welt gemessenen Daten:

```json
{
  "feature": "minecraft:example",
  "mode": "PAPER_SEED",
  "worldSeed": "123456789",
  "samples": [
    {"blockX": -160, "blockZ": 320, "index": 3, "step": 6,
     "draws": [{"bound": 16, "value": 4}, {"bound": 16, "value": 11}]},
    {"blockX": 640, "blockZ": -512, "index": 3, "step": 6,
     "draws": [{"bound": 16, "value": 7}, {"bound": 16, "value": 2}]}
  ]
}
```

```text
/egox feature crack example.json -1000000 1000000
```

`PAPER_SEED` ersetzt den Population-Eingangsseed durch jeden Kandidaten. `worldSeed` wird in diesem Modus nicht als Suchbedingung verwendet. `DECORATOR_SALT` verwendet dagegen den angegebenen Welt-Seed und sucht den additiven Wert nach der Population-Seed-Berechnung. Es handelt sich nicht um eine beliebige nachträgliche Änderung einzelner RNG-Ausgaben.

Nicht beobachtete Zwischenschritte, `nextFloat`/`nextLong`-Aufrufe, variable Schleifen, unbekannte Indizes oder geänderte Feature-Implementierungen machen einen solchen `nextInt`-Trace unpassend. Keine fiktiven Traces als Beweis eines gefundenen Feature-Seeds verwenden. Ergebnisse begrenzter Suchen bleiben außerhalb des geprüften Bereichs offen.
