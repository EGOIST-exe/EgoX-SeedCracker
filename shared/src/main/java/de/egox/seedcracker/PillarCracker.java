package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import java.util.*;
import java.util.concurrent.*;

/** Full 32-bit remaining-state search enables triangular and odd-bound placements too. */
public final class PillarCracker {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "EgoX-pillar-hash"); t.setDaemon(true); return t; });
    public static final CrackProgress PROGRESS = new CrackProgress();
    private static volatile boolean running, stopped;
    private static String attempted = "";
    private PillarCracker() { }
    public static boolean running() { return running; }
    public static void stop() { stopped = true; if (EgoXSession.job != null && EgoXSession.job.snapshot().label().equals("Seed: End-Saeulen + Strukturen")) EgoXSession.job.cancel(); }
    public static void reset() { attempted = ""; stop(); }
    public static void tick() {
        if (SessionLibrary.loading() || running || AutoBedrockCracker.running() || AutoCracker.running() || AutoCracker.paused() || EgoXSession.knownSeed() != null || EgoXSession.context().hash() == null) return;
        if (EgoXSession.job != null && EgoXSession.job.snapshot().state() == SearchJob.State.RUNNING) return;
        var pillars = EndPillarFinder.candidates(); var samples = EgoXSession.crackingStructures();
        if (pillars.isEmpty() || pillars.size() > 4 || samples.size() < 5 || ObservationBits.summarize(samples).placementBits() < 32) return;
        // Prefer the cheaper lifting path when it has enough data, but permit this fallback after it failed.
        String key = EgoXSession.context().epoch() + ":" + pillars + ':' + samples.hashCode() + ':' + EgoXSession.salts().hashCode();
        if (key.equals(attempted)) return;
        attempted = key; stopped = false; running = true;
        var context = EgoXSession.context();
        long dataRevision = AutoCracker.dataRevision();
        var biomeSamples = EgoXSession.biomes();
        String biomePreset = EgoXConfig.get().biomePreset;
        var salts = new HashMap<Placement, Long>(); samples.forEach(o -> salts.put(o.placement(), EgoXSession.salt(o.placement())));
        long count = (long)pillars.size() << 32;
        var job = new SearchJob("Seed: End-Saeulen + Strukturen", 0, count, EgoXConfig.get().workers, 256, () -> {
            var random = new LegacyRandom();
            return index -> {
                long seed = EndPillars.seedForIndex(pillars.get((int)(index >>> 32)), index & 0xFFFFFFFFL);
                for (var sample : samples) if (!sample.matches(seed, salts.get(sample.placement()), random)) return false;
                return true;
            };
        });
        EgoXSession.job = job;
        EgoXSession.say("End-Suche gestartet: " + count + " Zustaende. Fortschritt im HUD oder /egox cracker status.");
        job.completion().thenRunAsync(() -> {
            try {
                var result = job.snapshot();
                if (context.epoch() != EgoXSession.context().epoch()) return;
                if (result.state() != SearchJob.State.COMPLETE || stopped) { EgoXSession.say("End-Suche: " + result.state() + ". Bei zu vielen Kandidaten weitere Strukturen erfassen."); return; }
                var lower = result.matches().stream().map(index -> EndPillars.seedForIndex(pillars.get((int)(index >>> 32)), index & 0xFFFFFFFFL)).toList();
                PROGRESS.reset();
                var lifted = WorldSeedLifting.search(lower, 0, 65535, EgoXConfig.get().workers, 256,
                    () -> seed -> SeedHash.of(seed) == context.hash(), () -> stopped || context.epoch() != EgoXSession.context().epoch(),
                    PROGRESS::update, "End-Suche: Welt-Seed-Hash");
                if (stopped || context.epoch() != EgoXSession.context().epoch()) return;
                if (lifted.state() != SearchJob.State.COMPLETE) throw new IllegalStateException("Hash-Suche " + lifted.state() + ": " + lifted.error());
                if (lifted.seeds().size() == 1) {
                    long seed = lifted.seeds().getFirst();
                    FeatureEvidence.verify(seed, () -> stopped || context.epoch() != EgoXSession.context().epoch());
                    AutomaticBiomes.verify(seed, biomeSamples, biomePreset, () -> stopped || context.epoch() != EgoXSession.context().epoch());
                    if (!stopped && context.epoch() == EgoXSession.context().epoch()) EgoXSession.acceptWorldSeed(seed, "END_PILLARS_STRUCTURES_HASH");
                } else {
                    EgoXSession.say("End-Suche: " + lifted.seeds().size() + " Hash-Treffer; pruefe vorhandene Kandidaten anhand der Biome.");
                    if (FeatureEvidence.ready() && !EgoXConfig.get().autoBiomes) FeatureEvidence.fallback(lower, BlockEvidence.bedrock(), BlockEvidence.ores(), BlockEvidence.features(),
                        EgoXConfig.get().bedrockChecker, EgoXConfig.get().autoOres, EgoXConfig.get().geodeChecker, DungeonEvidence.snapshot(), EgoXConfig.get().dungeonChecker, context.hash(), EgoXConfig.get().workers,
                        () -> stopped || context.epoch() != EgoXSession.context().epoch() || dataRevision != AutoCracker.dataRevision(), PROGRESS);
                    AutomaticBiomes.fallback(lower, biomeSamples, biomePreset, context.hash(), EgoXConfig.get().workers,
                        () -> stopped || context.epoch() != EgoXSession.context().epoch() || dataRevision != AutoCracker.dataRevision(), PROGRESS);
                }
            } catch (Exception error) { EgoXSession.say("End-Suche fehlgeschlagen: " + error.getMessage()); }
            finally { running = false; }
        }, WORKER);
    }
}
