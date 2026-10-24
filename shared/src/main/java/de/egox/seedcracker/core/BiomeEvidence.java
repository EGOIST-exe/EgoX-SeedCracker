package de.egox.seedcracker.core;

import java.util.*;
import java.util.function.Function;

/** Diverse early-rejection samples, followed by validation against the complete snapshot. */
public final class BiomeEvidence {
    public record Comparison(int matching, int total) { public boolean matches() { return total > 0 && matching == total; } }
    private BiomeEvidence() { }
    /** Evict the oldest sample from the most represented dimension, retaining both generators. */
    public static void trimDimensions(LinkedHashMap<String, BiomeObservation> samples, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Positive biome limit required");
        if (samples.size() <= limit) return;
        var counts = new HashMap<String, Integer>(); samples.values().forEach(s -> counts.merge(s.dimension(), 1, Integer::sum));
        while (samples.size() > limit) {
            int most = counts.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
            var iterator = samples.entrySet().iterator();
            while (iterator.hasNext()) {
                var sample = iterator.next().getValue();
                if (counts.get(sample.dimension()) == most) { iterator.remove(); counts.merge(sample.dimension(), -1, Integer::sum); break; }
            }
        }
    }
    public static boolean sufficient(List<BiomeObservation> samples) {
        return samples.size() >= 8 && samples.stream().map(BiomeObservation::biome).distinct().count() >= 2;
    }
    public static Comparison compare(List<BiomeObservation> samples, Function<BiomeObservation, String> resolver) {
        int matching = 0;
        for (var sample : samples) if (sample.biome().equals(resolver.apply(sample))) matching++;
        return new Comparison(matching, samples.size());
    }
    public static boolean matches(List<BiomeObservation> first, List<BiomeObservation> all, Function<BiomeObservation, String> resolver) {
        for (var sample : first) if (!sample.biome().equals(resolver.apply(sample))) return false;
        for (var sample : all) if (!sample.biome().equals(resolver.apply(sample))) return false;
        return true;
    }
    public static List<BiomeObservation> select(List<BiomeObservation> samples, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Sample limit must be positive");
        var counts = new HashMap<String, Integer>(); samples.forEach(s -> counts.merge(s.biome(), 1, Integer::sum));
        var remaining = new ArrayList<>(new LinkedHashSet<>(samples));
        remaining.sort(Comparator.comparingInt((BiomeObservation s) -> counts.get(s.biome()))
            .thenComparing(BiomeObservation::biome).thenComparingInt(BiomeObservation::quartX)
            .thenComparingInt(BiomeObservation::quartY).thenComparingInt(BiomeObservation::quartZ));
        var selected = new ArrayList<BiomeObservation>(); var biomes = new HashSet<String>();
        for (var sample : remaining) if (selected.size() < limit && biomes.add(sample.biome())) selected.add(sample);
        remaining.removeAll(selected);
        while (selected.size() < limit && !remaining.isEmpty()) {
            BiomeObservation best = null; double bestDistance = -1;
            for (var candidate : remaining) {
                double distance = selected.stream().mapToDouble(s -> distanceSquared(s, candidate)).min().orElse(Double.MAX_VALUE);
                if (distance > bestDistance) { bestDistance = distance; best = candidate; }
            }
            selected.add(best); remaining.remove(best);
        }
        return List.copyOf(selected);
    }
    private static double distanceSquared(BiomeObservation a, BiomeObservation b) {
        if (!a.dimension().equals(b.dimension())) return Double.MAX_VALUE;
        double dx = (double)a.quartX() - b.quartX(), dy = (double)a.quartY() - b.quartY(), dz = (double)a.quartZ() - b.quartZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
