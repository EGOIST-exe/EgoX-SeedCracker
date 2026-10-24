package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import java.util.*;
import java.util.function.*;

/** Additional vanilla-model candidate checks, separate from proof by the server hash. */
public final class FeatureEvidence {
    public record Report(String state, long checked, long total, List<String> candidates, String detail) { }
    private static volatile Report report = new Report("BEREIT", 0, 0, List.of(), "Noch keine Feature-Pruefung");
    private static long generation;
    private FeatureEvidence() { }
    public static synchronized void reset() { generation++; report = new Report("BEREIT", 0, 0, List.of(), "Noch keine Feature-Pruefung"); }
    private static synchronized long token() { return generation; }
    private static synchronized boolean publish(long token, Report next) { if (token != generation) return false; report = next; SessionLibrary.changed(); return true; }
    public static synchronized void restore(Report saved) {
        if (saved != null) report = new Report(saved.state().equals("SUCHT") ? "UNTERBROCHEN" : saved.state(), saved.checked(), saved.total(), List.copyOf(saved.candidates()), "Wiederhergestellte Analyse: " + saved.detail());
    }
    public static Report report() { return report; }
    public static String status() { return "Erz " + (EgoXConfig.get().autoOres ? "ON" : "OFF") + " | Bedrock " + (EgoXConfig.get().bedrockChecker ? "ON" : "OFF") + " | Geode " + (EgoXConfig.get().geodeChecker ? "ON" : "OFF") + " | Dungeon " + (EgoXConfig.get().dungeonChecker ? "ON" : "OFF") + " | " + report.detail(); }
    public static boolean ready() {
        return EgoXConfig.get().bedrockChecker && BlockEvidence.bedrock().size() >= 16
            || EgoXConfig.get().autoOres && BlockEvidence.ores().stream().filter(EmeraldReplay::supports).count() >= 2
            || EgoXConfig.get().geodeChecker && BlockEvidence.features().stream().filter(NativeGeode::supports).count() >= 4
            || EgoXConfig.get().dungeonChecker && !DungeonEvidence.snapshot().isEmpty();
    }
    public static LongPredicate predicate(List<BlockObservation> bedrock, List<BlockObservation> ores, List<BlockObservation> features, boolean useBedrock, boolean useOres, boolean useGeodes, List<DungeonObservation> dungeons, boolean useDungeon, BooleanSupplier cancelled) {
        LongPredicate bedrockCheck = useBedrock && !bedrock.isEmpty() ? NativeBedrock.predicate(bedrock) : seed -> true;
        LongPredicate oreCheck = useOres && ores.stream().anyMatch(EmeraldReplay::supports) ? NativeEmerald.predicate(ores, cancelled) : seed -> true;
        LongPredicate geodeCheck = useGeodes && features.stream().anyMatch(NativeGeode::supports) ? NativeGeode.predicate(features) : seed -> true;
        LongPredicate dungeonCheck = useDungeon && !dungeons.isEmpty() ? NativeDungeon.predicate(dungeons, cancelled) : seed -> true;
        return seed -> bedrockCheck.test(seed) && dungeonCheck.test(seed) && geodeCheck.test(seed) && oreCheck.test(seed);
    }
    public static void verify(long seed, BooleanSupplier cancelled) {
        long token = token();
        var target=SessionLibrary.identity();
        var bedrock = BlockEvidence.bedrock(); var ores = BlockEvidence.ores();
        var features = BlockEvidence.features(); boolean geode=EgoXConfig.get().geodeChecker&&features.stream().anyMatch(NativeGeode::supports);
        boolean bed = EgoXConfig.get().bedrockChecker && !bedrock.isEmpty(), ore = EgoXConfig.get().autoOres && ores.stream().anyMatch(EmeraldReplay::supports);
        var dungeons = DungeonEvidence.snapshot(); boolean dungeon = EgoXConfig.get().dungeonChecker && !dungeons.isEmpty();
        if ((!bed && !ore && !geode && !dungeon) || cancelled.getAsBoolean()) return;
        try {
            boolean matches = predicate(bedrock, ores, features, bed, ore, geode, dungeons, dungeon, cancelled).test(seed);
            if (cancelled.getAsBoolean()) return;
            String detail = (matches ? "Feature-Bedingungen passend" : "Hash passt; Vanilla-Feature-Bedingungen widersprechen") + " | Bedrock " + (bed ? bedrock.size() : 0) + " | Smaragderz " + (ore ? Math.min(64,ores.stream().filter(EmeraldReplay::supports).count()) : 0) + " | Drusen " + (geode?features.stream().filter(NativeGeode::supports).count():0) + " | Dungeons " + (dungeon ? Math.min(8, dungeons.size()) : 0);
            if (publish(token, new Report(matches ? "PASSEND" : "WIDERSPRUCH", 1, 1, List.of(), detail))) {
                SessionLibrary.eventFor(target,"FEATURE_CHECK",Map.of("worldSeed",Long.toString(seed),"report",report));EgoXSession.say(detail);
            }
        } catch (RuntimeException error) { if (!cancelled.getAsBoolean() && publish(token, new Report("FEHLER", 0, 1, List.of(), error.getMessage()))) EgoXSession.say("Feature-Pruefung fehlgeschlagen: " + error.getMessage()); }
    }
    public static void fallback(Collection<Long> lower, List<BlockObservation> bedrock, List<BlockObservation> ores, List<BlockObservation> features, boolean useBedrock, boolean useOres, boolean useGeodes, List<DungeonObservation> dungeons, boolean useDungeon,
                                Long hash, int workers, BooleanSupplier cancelled, CrackProgress progress) {
        long token = token();
        var target=SessionLibrary.identity();
        if (lower.isEmpty() || cancelled.getAsBoolean()) return;
        long total = Math.multiplyExact((long)lower.size(), 65536);
        EgoXSession.say("Feature-Fallback: " + total + " Welt-Seeds | Bedrock " + useBedrock + " | Smaragderz " + useOres + " | Geode " + useGeodes + " | Dungeon " + useDungeon + ". Nur Kandidatenbedingungen; /egox stop.");
        var result = WorldSeedLifting.search(lower, 0, 65535, Math.min(2, workers), 32,
            () -> predicate(bedrock, ores, features, useBedrock, useOres, useGeodes, dungeons, useDungeon, cancelled),
            () -> cancelled.getAsBoolean() || useBedrock && !EgoXConfig.get().bedrockChecker || useOres && !EgoXConfig.get().autoOres || useGeodes && !EgoXConfig.get().geodeChecker || useDungeon && !EgoXConfig.get().dungeonChecker,
            (phase, checked, count) -> { if (!cancelled.getAsBoolean() && publish(token, new Report("SUCHT", checked, count, List.of(), "Bedrock-/Erz-Kandidatenpruefung"))) progress.update(phase, checked, count); }, "Feature-Fallback: Welt-Seeds");
        if (cancelled.getAsBoolean()) { publish(token, new Report("CANCELLED", result.checked(), result.total(), List.of(), "Feature-Fallback unterbrochen")); return; }
        var candidates = result.seeds().stream().map(seed -> Long.toString(seed) + " | " + (hash == null ? "HASH_FEHLT" : SeedHash.of(seed) == hash ? "HASH_PASSEND" : "HASH_WIDERSPRUCH")).toList();
        if (!publish(token, new Report(result.state().name(), result.checked(), result.total(), candidates, result.state() + ": " + result.checked() + "/" + result.total() + " geprueft; " + candidates.size() + " Feature-Kandidaten; " + result.error()))) return;
        SessionLibrary.eventFor(target,"FEATURE_ANALYSIS",report);
        EgoXSession.say(report.detail()); candidates.stream().limit(8).forEach(c -> EgoXSession.say("Unbestaetigter Feature-Kandidat: " + c));
    }
    public static void describe() {
        EgoXSession.say(status()); BlockEvidence.describe();
        report.candidates().forEach(c -> EgoXSession.say("Feature-Kandidat " + c));
        EgoXSession.say("Erz-Replay: nur serverseitig beobachtete Smaragderz-Entfernungen, konservative Moeglichkeitspruefung. Andere Erze bleiben Rohdaten. Bedrock: Vanilla-Uebergangsschichten.");
        EgoXSession.say("Nether-Bedrock verwendet das Legacy-Modell und loest obere 16 Welt-Seed-Bits allein nicht auf. Flache/veraenderte Bedrock-Generierung kann dem Vanilla-Modell widersprechen.");
        EgoXSession.say("Geoden: Seltenheit, Platzierung und maximale Ausdehnung werden geprueft; keine exakte Formrekonstruktion.");
        EgoXSession.say(DungeonEvidence.status());
        EgoXSession.say("Dungeon-Muster-Bits gelten nur fuer intakte, natuerliche Monsterraeume. Modernes RNG-Replay filtert Welt-Seed-Kandidaten; kein eigenstaendiger 128-Bit-Inversionssolver.");
    }
}
