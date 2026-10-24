package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Once the world seed is known, each mismatching placement family is searched independently. */
public final class AutoSaltCracker {
    private static final Set<String> attempted = new HashSet<>();
    private static boolean paused;
    public static void pause() { paused = true; }
    public static boolean paused() { return paused || !EgoXConfig.get().autoSaltCrack; }
    public static void resume() { paused = false; attempted.clear(); }
    public static void newSession() { paused = false; attempted.clear(); }

    public static void tick() {
        if (SessionLibrary.loading() || paused || !EgoXConfig.get().autoSaltCrack || AutoCracker.running() || PillarCracker.running() || EgoXSession.knownSeed() == null) return;
        if (EgoXSession.job != null && EgoXSession.job.snapshot().state() == SearchJob.State.RUNNING) return;
        long seed = EgoXSession.knownSeed();
        var groups = new TreeMap<String, List<StructureObservation>>();
        EgoXSession.structures().forEach(o -> groups.computeIfAbsent(o.placement().id(), k -> new ArrayList<>()).add(o));
        for (var group : groups.entrySet()) {
            var samples = group.getValue();
            if (samples.size() < 5) continue;
            String key = group.getKey() + ':' + samples.hashCode();
            if (attempted.contains(key)) continue;
            var random = new LegacyRandom();
            if (samples.stream().allMatch(o -> o.matches(seed, EgoXSession.salt(o.placement()), random))) continue;
            attempted.add(key);
            var context = EgoXSession.context();
            var job = new SearchJob("Automatischer Salt " + group.getKey(), Integer.MIN_VALUE, 1L << 32, EgoXConfig.get().workers, 256, () -> {
                var rng = new LegacyRandom();
                return salt -> { for (var o : samples) if (!o.matches(seed, salt, rng)) return false; return true; };
            });
            EgoXSession.job = job;
            EgoXSession.say("Abweichende Platzierung: " + group.getKey() + ". Suche alle 32-Bit-Salts. /egox status; Abbruch: /egox stop");
            job.completion().thenRun(() -> Minecraft.getInstance().execute(() -> {
                if (context.epoch() != EgoXSession.context().epoch()) return;
                var result = job.snapshot();
                if (result.state() == SearchJob.State.COMPLETE && result.matches().size() == 1) {
                    long salt = result.matches().getFirst();
                    EgoXSession.setSalt(group.getKey(), salt);
                    ResultDatabase.recordSalt(context, seed, group.getKey(), salt, samples.size());
                    EgoXSession.say("Salt eindeutig im gesamten int-Bereich: " + group.getKey() + " = " + salt + ". Override gesetzt und Speicherung eingeplant.");
                } else EgoXSession.say("Salt-Suche " + group.getKey() + ": " + result.state() + ", " + result.matches().size() + " Kandidaten. Weitere Starts oder anderes Platzierungsmodell noetig.");
            }));
            return;
        }
    }
}
