package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Biome support is evidence; only a matching server hash confirms a public world-seed result. */
public final class AutomaticBiomes {
    public record Candidate(String worldSeed, String hashStatus, int samples) { }
    public record Report(String state, String preset, int samples, long checked, long total, List<Candidate> candidates, String detail) { }
    private static volatile Report report = new Report("BEREIT", "", 0, 0, 0, List.of(), "Biom-Pruefung wartet auf Kandidaten");
    private static long generation;
    private AutomaticBiomes() { }
    public static synchronized void reset() { generation++; report = new Report("BEREIT", "", 0, 0, 0, List.of(), "Biom-Pruefung wartet auf Kandidaten"); }
    static synchronized long token() { return generation; }
    static synchronized boolean publish(long token, Report next) {
        if (token != generation) return false;
        report = next; SessionLibrary.changed(); return true;
    }
    public static Report report() { return report; }
    public static synchronized void restore(Report saved) {
        if (saved == null) return;
        report = new Report(saved.state().equals("SUCHT") ? "UNTERBROCHEN" : saved.state(), saved.preset(), saved.samples(), saved.checked(), saved.total(),
            List.copyOf(saved.candidates()), "Wiederhergestellte Analyse: " + saved.detail());
    }
    public static List<String> describe() {
        var r = report;
        var lines = new ArrayList<String>(); lines.add(status());
        lines.add("Biom-Snapshot: " + r.samples() + " Samples | Preset " + (r.preset().isEmpty() ? EgoXConfig.get().biomePreset : r.preset()));
        var samples = EgoXSession.biomes();
        lines.add("Gesammelt: Overworld " + samples.stream().filter(b -> b.dimension().equals("minecraft:overworld")).count() + " | Nether " + samples.stream().filter(b -> b.dimension().equals("minecraft:the_nether")).count());
        lines.add("Prueffortschritt: " + r.checked() + "/" + r.total() + "; " + r.candidates().size() + " Kandidaten");
        r.candidates().forEach(c -> lines.add("Biom-Kandidat " + c.worldSeed() + " | " + c.hashStatus() + " | " + c.samples() + " Samples"));
        lines.add("Fallback prueft nur obere 16 Bits vorhandener 48-Bit-Kandidaten; unbekannte Salts koennen bereits diese unteren Bits verschieben.");
        lines.add("Nether-Biome inklusive Wirrwald werden nativ geprueft; im Vanilla-Legacy-Modell unterscheiden sie die oberen 16 Bits nicht. Biom-Checker bleibt standardmaessig OFF.");
        return List.copyOf(lines);
    }
    public static String status() {
        var r = report;
        return "Biom-Automatik " + (EgoXConfig.get().autoBiomes ? "ON" : "OFF") + " | " + r.state() + " | " + r.detail();
    }
    public static void verify(long seed, List<BiomeObservation> samples, String preset, BooleanSupplier cancelled) {
        if (samples.isEmpty() || !EgoXConfig.get().autoBiomes || cancelled.getAsBoolean()) return;
        long token = token();
        BiomeEvidence.Comparison result;
        try { result = NativeBiomes.compare(seed, samples, preset); }
        catch (RuntimeException error) {
            if (!cancelled.getAsBoolean() && publish(token, new Report("FEHLER", preset, samples.size(), 0, samples.size(), List.of(), "Native Biom-Gegenpruefung fehlgeschlagen: " + error.getMessage())))
                EgoXSession.say(report.detail());
            return;
        }
        if (cancelled.getAsBoolean()) return;
        String detail = result.matching() + "/" + result.total() + " Biom-Samples passend (" + preset + ")";
        if (!result.matches()) detail += "; Hash passt, Biom-Preset/Generator/Chunk-Alter pruefen";
        if (publish(token, new Report(result.matches() ? "PASSEND" : "WIDERSPRUCH", preset, samples.size(), result.total(), result.total(), List.of(), detail))) EgoXSession.say(detail);
    }
    public static void fallback(Collection<Long> lower, List<BiomeObservation> samples, String preset, Long hash,
                                int workers, BooleanSupplier cancelled, CrackProgress progress) {
        long token = token();
        if (cancelled.getAsBoolean()) return;
        if (!EgoXConfig.get().autoBiomes) {
            publish(token, new Report("OFF", preset, samples.size(), 0, 0, List.of(), "Automatischer Biom-Abgleich ausgeschaltet")); return;
        }
        if (!BiomeEvidence.sufficient(samples)) {
            if (publish(token, new Report("DATEN_FEHLEN", preset, samples.size(), 0, 0, List.of(), "Mindestens 8 Samples aus 2 Biomen benoetigt"))) EgoXSession.say(report.detail()); return;
        }
        if (lower.isEmpty()) {
            if (publish(token, new Report("KEINE_KANDIDATEN", preset, samples.size(), 0, 0, List.of(), "Keine unteren 48 Bits vorhanden; keine unbeschraenkte Biom-Inversion implementiert"))) EgoXSession.say(report.detail()); return;
        }
        var selected = BiomeEvidence.select(samples, 64);
        var bedrock = BlockEvidence.bedrock(); var ores = BlockEvidence.ores();
        var features=BlockEvidence.features();boolean useGeodes=EgoXConfig.get().geodeChecker&&features.stream().filter(NativeGeode::supports).count()>=4;
        boolean useBedrock = EgoXConfig.get().bedrockChecker && bedrock.size() >= 16;
        boolean useOres = EgoXConfig.get().autoOres && ores.stream().filter(EmeraldReplay::supports).count() >= 2;
        var dungeons = DungeonEvidence.snapshot(); boolean useDungeon = EgoXConfig.get().dungeonChecker && !dungeons.isEmpty();
        long total = Math.multiplyExact((long)lower.size(), 65536L);
        String detail = "Obere 16 Bits fuer " + lower.size() + " Struktur-Kandidaten; " + samples.size() + " Biom-Samples (" + preset + ")";
        if (cancelled.getAsBoolean() || !publish(token, new Report("SUCHT", preset, samples.size(), 0, total, List.of(), detail))) return;
        EgoXSession.say("Biom-Fallback: " + total + " Welt-Seeds, unabhaengig vom Hash geprueft. Kann lange dauern; /egox stop. Neue Strukturstarts unterbrechen diesen Versuch.");
        var result = WorldSeedLifting.search(lower, 0, 65535, Math.min(2, workers), 32,
            () -> {
                var extra = FeatureEvidence.predicate(bedrock, ores, features, useBedrock, useOres, useGeodes, dungeons, useDungeon, cancelled);
                return seed -> extra.test(seed) && NativeBiomes.matches(seed, selected, samples, preset);
            },
            () -> cancelled.getAsBoolean() || !EgoXConfig.get().autoBiomes,
            (phase, checked, count) -> {
                if (!cancelled.getAsBoolean() && publish(token, new Report("SUCHT", preset, samples.size(), checked, count, List.of(), detail))) progress.update(phase, checked, count);
            }, "Biom-Fallback: Welt-Seeds");
        if (cancelled.getAsBoolean()) {
            publish(token, new Report("CANCELLED", preset, samples.size(), result.checked(), result.total(), List.of(), "Biom-Fallback unterbrochen; Teilsuche bestaetigt keinen Welt-Seed")); return;
        }
        var candidates = result.seeds().stream().map(seed -> candidate(seed, hash, samples.size())).toList();
        String summary = result.state() + ": " + result.checked() + "/" + result.total() + " geprueft; " + candidates.size() + " Biom-Kandidaten";
        if (!result.error().isEmpty()) summary += "; " + result.error();
        if (!candidates.isEmpty()) summary += "; ohne passenden Hash unbestaetigt, kein DB-Upload";
        if (!publish(token, new Report(result.state().name(), preset, samples.size(), result.checked(), result.total(), candidates, summary))) return;
        EgoXSession.say(summary);
        candidates.stream().limit(8).forEach(c -> EgoXSession.say("Biom-Kandidat: " + c.worldSeed() + " | " + c.hashStatus()));
    }
    static Candidate candidate(long seed, Long hash, int samples) {
        return new Candidate(Long.toString(seed), hash == null ? "HASH_FEHLT" : SeedHash.of(seed) == hash ? "HASH_PASSEND" : "HASH_WIDERSPRUCH", samples);
    }
}
