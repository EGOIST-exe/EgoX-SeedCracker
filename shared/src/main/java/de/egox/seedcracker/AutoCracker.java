package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import java.util.*;
import java.util.concurrent.*;

/** Automatic recovery from independently observed linear random-spread starts. */
public final class AutoCracker {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "EgoX-auto"); t.setDaemon(true); return t; });
    private static volatile boolean running, stop;
    private static volatile String status = "Automatik: sammelt Strukturstarts";
    public static final CrackProgress PROGRESS = new CrackProgress();
    private static volatile long revision, cancellationGeneration;
    private static long attemptedRevision = -1;
    private static long startAfter;

    public static synchronized void observationsChanged() { revision++; startAfter = System.nanoTime() + 2_000_000_000L; }
    public static synchronized void stop() { stop = true; cancellationGeneration++; }
    public static synchronized void resume() { stop = false; attemptedRevision = -1; }
    public static boolean running() { return running; }
    public static boolean paused() { return stop || !EgoXConfig.get().autoCrack; }
    public static String status() { return status; }
    static long dataRevision() { return revision; }
    public static synchronized void retry() { attemptedRevision = -1; }
    public static synchronized void newSession() { stop = false; attemptedRevision = -1; revision = 0; PROGRESS.reset(); AutomaticBiomes.reset(); status = "Automatik: sammelt Strukturstarts"; }

    public static synchronized void tick() {
        if (SessionLibrary.loading() || running || AutoBedrockCracker.running() || PillarCracker.running() || stop || !EgoXConfig.get().autoCrack || EgoXSession.knownSeed() != null || revision == attemptedRevision
            || System.nanoTime() < startAfter || EgoXSession.context().server().isEmpty()) return;
        if (EgoXSession.job != null && EgoXSession.job.snapshot().state() == SearchJob.State.RUNNING) return;
        var all = EgoXSession.crackingStructures();
        var linear = all.stream().filter(o -> ObservationBits.liftable(o.placement())).toList();
        double bits = ObservationBits.summarize(linear).liftingBits();
        if (bits < 44 || linear.size() < 5) { status = "Automatik: mindestens 5 unabhaengige lineare Starts und 44 Platzierungsbits benoetigt (aktuell " + String.format(Locale.ROOT, "%.1f", bits) + ")"; return; }
        attemptedRevision = revision;
        running = true;
        PROGRESS.reset();
        long epoch = EgoXSession.context().epoch();
        long searchRevision = revision;
        long generation = cancellationGeneration;
        Long hash = EgoXSession.context().hash();
        var biomeSamples = EgoXSession.biomes();
        var bedrockSamples = BlockEvidence.bedrock(); var oreSamples = BlockEvidence.ores();
        var featureSamples=BlockEvidence.features(); boolean useGeodes=EgoXConfig.get().geodeChecker&&featureSamples.stream().filter(NativeGeode::supports).count()>=4;
        boolean useBedrock = EgoXConfig.get().bedrockChecker && bedrockSamples.size() >= 16;
        boolean useOres = EgoXConfig.get().autoOres && oreSamples.stream().filter(EmeraldReplay::supports).count() >= 2;
        var dungeons = DungeonEvidence.snapshot(); boolean useDungeon = EgoXConfig.get().dungeonChecker && !dungeons.isEmpty();
        String biomePreset = EgoXConfig.get().biomePreset;
        var saltSnapshot = new HashMap<Placement, Long>();
        all.forEach(o -> saltSnapshot.put(o.placement(), EgoXSession.salt(o.placement())));
        var groups = RecoveryGroups.plan(all);
        int workers = EgoXConfig.get().workers;
        WORKER.execute(() -> {
            try {
                var fallback = new HashSet<Long>();
                status = "Automatik: 48-Bit-Struktur-Seed-Suche laeuft";
                EgoXSession.say(status + " (" + linear.size() + " Starts). Abbruch: /egox stop");
                for (int attempt = 0; attempt < groups.size(); attempt++) {
                var scope = groups.get(attempt);
                var constraints = scope.stream().filter(o -> ObservationBits.liftable(o.placement()))
                    .map(o -> new LiftingSearch.Constraint(o, saltSnapshot.get(o.placement()))).toList();
                final String prefix = "Gruppe " + (attempt + 1) + "/" + groups.size() + ": ";
                status = "Automatik: " + prefix + (attempt == 0 ? "alle Starts" : scope.getFirst().placement().id());
                var candidates = LiftingSearch.search(constraints, 0, LegacyRandom.MASK, workers,
                    () -> stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch(), () -> {
                        var random = new LegacyRandom();
                        return seed -> { for (var o : scope) if (!o.matches(seed, saltSnapshot.get(o.placement()), random)) return false; return true; };
                    }, (phase, done, total) -> PROGRESS.update(prefix + phase, done, total));
                if (stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch()) { status = "Automatik: abgebrochen"; return; }
                status = "Automatik: " + candidates.size() + " Struktur-Seed-Kandidaten";
                EgoXSession.say(status);
                if (candidates.isEmpty()) continue;
                fallback.addAll(candidates);
                if (hash == null) { EgoXSession.say("Kein Server-Seed-Hash vorhanden; Kandidaten fuer Biom-Fallback gesammelt."); continue; }
                status = "Automatik: prueft obere 16 Welt-Seed-Bits gegen Server-Hash";
                var lifted = WorldSeedLifting.search(candidates, 0, 65535, workers, 256, () -> seed -> SeedHash.of(seed) == hash,
                    () -> stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch(), PROGRESS::update, prefix + "4/4 Welt-Seed-Hash pruefen");
                if (stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch()) return;
                if (lifted.state() != SearchJob.State.COMPLETE) throw new IllegalStateException("Hash-Suche " + lifted.state() + ": " + lifted.error());
                var worldSeeds = lifted.seeds();
                if (worldSeeds.size() == 1) {
                    FeatureEvidence.verify(worldSeeds.getFirst(), () -> stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch());
                    AutomaticBiomes.verify(worldSeeds.getFirst(), biomeSamples, biomePreset, () -> stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch());
                    if (stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch()) return;
                    EgoXSession.acceptWorldSeed(worldSeeds.iterator().next(), "AUTOMATIC_STRUCTURES_AND_HASH");
                    status = "Automatik: Welt-Seed bestaetigt";
                    return;
                } else { status = "Automatik: " + worldSeeds.size() + " passende Welt-Seeds"; EgoXSession.say(status); }
                }
                if (stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch()) return;
                status = EgoXConfig.get().autoBiomes ? "Automatik: Biom-Fallback fuer vorhandene 48-Bit-Kandidaten" : "Automatik: Feature-Abgleich vorhandener 48-Bit-Kandidaten";
                if ((useBedrock || useOres || useGeodes || useDungeon) && !EgoXConfig.get().autoBiomes) FeatureEvidence.fallback(fallback, bedrockSamples, oreSamples, featureSamples, useBedrock, useOres, useGeodes, dungeons, useDungeon, hash, workers,
                    () -> stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch() || revision != searchRevision, PROGRESS);
                AutomaticBiomes.fallback(fallback, biomeSamples, biomePreset, hash, workers,
                    () -> stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch() || revision != searchRevision, PROGRESS);
                if (stop || generation != cancellationGeneration || epoch != EgoXSession.context().epoch()) return;
                status = revision != searchRevision ? "Automatik: neue Strukturstarts; Suche wird mit neuen Daten wiederholt"
                    : "Automatik: kein Hash-bestaetigter Welt-Seed. " + (EgoXConfig.get().autoBiomes ? AutomaticBiomes.report().detail() : FeatureEvidence.report().detail());
                EgoXSession.say(status);
            } catch (Exception e) { status = "Automatik fehlgeschlagen: " + e.getMessage(); EgoXSession.say(status); }
            finally { running = false; }
        });
    }
}
