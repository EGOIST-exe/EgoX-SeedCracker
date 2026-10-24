package de.egox.seedcracker.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Necessary conditions only: unknown biome/terrain skips are explicitly branched, never invented as an exact trace. */
public final class EmeraldReplay {
    public record Settings(int attempts, int minY, int maxY, int plateau, int index, int step) { }
    private EmeraldReplay() { }
    public static boolean supports(BlockObservation ore) {
        return ore.dimension().equals("minecraft:overworld") && (ore.block().equals("minecraft:emerald_ore") || ore.block().equals("minecraft:deepslate_emerald_ore"))
            && ore.source().equals("SERVER_BLOCK_CHANGE");
    }
    public static boolean nearOrigin(BlockObservation ore, int x, int y, int z) {
        // A deliberately generous enclosing box for native size-3 ore geometry.
        return Math.abs((long)ore.x() - x) <= 2 && Math.abs((long)ore.z() - z) <= 2 && ore.y() >= y - 4 && ore.y() <= y + 1;
    }
    public static boolean[] possible(long featureSeed, int chunkX, int chunkZ, Settings settings, List<BlockObservation> ores, BooleanSupplier cancelled) {
        var frontier = new HashSet<XoroshiroWorldgen.State>(); frontier.add(new XoroshiroWorldgen(featureSeed).state());
        boolean[] possible = new boolean[ores.size()];
        int range = settings.maxY() - settings.minY(), low = (range - settings.plateau()) / 2, high = range - low;
        for (int attempt = 0; attempt < settings.attempts() && !cancelled.getAsBoolean(); attempt++) {
            var next = new HashSet<XoroshiroWorldgen.State>();
            for (var state : frontier) {
                var random = new XoroshiroWorldgen(state);
                int x = chunkX * 16 + random.nextInt(16), z = chunkZ * 16 + random.nextInt(16);
                int y = settings.minY() + (settings.plateau() >= range ? random.nextInt(range + 1) : random.nextInt(high + 1) + random.nextInt(low + 1));
                for (int i = 0; i < ores.size(); i++) if (nearOrigin(ores.get(i), x, y, z)) possible[i] = true;
                next.add(random.state()); // Biome filter skips the feature entirely.
                random.nextFloat(); random.nextInt(3); random.nextInt(3);
                next.add(random.state()); // Above the terrain: no ore spheres are generated.
                for (int i = 0; i < 3; i++) random.nextDouble();
                next.add(random.state()); // Complete size-3 geometry; air-exposure discard is zero.
            }
            frontier = next;
            boolean all = true; for (boolean value : possible) all &= value;
            if (all) break;
        }
        return possible;
    }
}
