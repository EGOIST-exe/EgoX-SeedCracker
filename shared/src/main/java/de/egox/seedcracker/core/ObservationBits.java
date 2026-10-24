package de.egox.seedcracker.core;

import java.util.*;

/** Theoretical information from placement coordinates, not a proof of recovered seed bits. */
public final class ObservationBits {
    public record Summary(int starts, int liftingStarts, double placementBits, double liftingBits, Map<String, Double> families) { }
    private ObservationBits() { }
    public static boolean liftable(Placement p) {
        int bound = p.spacing() - p.separation();
        // Power-of-two nextInt uses scaled high bits rather than the remainder used by lifting.
        return p.spread() == Placement.Spread.LINEAR && (bound & 1) == 0 && (bound & (bound - 1)) != 0;
    }
    public static double coordinateBits(int offset, int bound, Placement.Spread spread) {
        if (offset < 0 || offset >= bound) return 0;
        if (spread == Placement.Spread.LINEAR) return Math.log(bound) / Math.log(2);
        long a = pairs(2 * offset, bound), b = pairs(2 * offset + 1, bound);
        return -Math.log((double) (a + b) / ((long) bound * bound)) / Math.log(2);
    }
    private static long pairs(int sum, int bound) { return sum < 0 || sum > 2 * bound - 2 ? 0 : sum < bound ? sum + 1L : 2L * bound - 1 - sum; }
    public static double bits(StructureObservation o) {
        var p = o.placement(); int bound = p.spacing() - p.separation();
        if (Math.floorMod(o.chunkX(), p.spacing()) >= bound || Math.floorMod(o.chunkZ(), p.spacing()) >= bound) return 0;
        return coordinateBits(Math.floorMod(o.chunkX(), p.spacing()), bound, p.spread())
            + coordinateBits(Math.floorMod(o.chunkZ(), p.spacing()), bound, p.spread());
    }
    public static Summary summarize(Collection<StructureObservation> observations) {
        var regions = new HashSet<String>(); var families = new TreeMap<String, Double>();
        double total = 0, lifting = 0; int starts = 0, liftStarts = 0;
        for (var o : observations) {
            var p = o.placement();
            // Worlds using the same seed repeat placements across dimensions; never count them twice.
            String key = p.id() + ":" + Math.floorDiv(o.chunkX(), p.spacing()) + ":" + Math.floorDiv(o.chunkZ(), p.spacing());
            if (!regions.add(key)) continue;
            double bits = bits(o); if (bits <= 0) continue;
            starts++; total += bits; families.merge(p.id(), bits, Double::sum);
            if (liftable(p)) { liftStarts++; lifting += bits; }
        }
        return new Summary(starts, liftStarts, total, lifting, Collections.unmodifiableMap(families));
    }
}
