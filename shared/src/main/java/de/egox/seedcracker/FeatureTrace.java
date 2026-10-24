package de.egox.seedcracker;

import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import java.util.List;

/** Exact replay input for known feature RNG calls. Does not infer hidden RNG calls from arbitrary blocks. */
public record FeatureTrace(String feature, Mode mode, long worldSeed, List<Sample> samples) {
    public enum Mode { PAPER_SEED, DECORATOR_SALT }
    public record Draw(int bound, int value) { }
    public record Sample(int blockX, int blockZ, int index, int step, List<Draw> draws) { }
    public void validate() {
        if (feature == null || !feature.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || mode == null || samples == null || samples.size() < 2 || samples.size() > 256)
            throw new IllegalArgumentException("Feature-Trace braucht Feature-ID, Modus und 2..256 Samples");
        for (Sample sample : samples) {
            if ((sample.blockX() & 15) != 0 || (sample.blockZ() & 15) != 0 || sample.index() < 0 || sample.step() < 0
                || sample.draws() == null || sample.draws().isEmpty() || sample.draws().size() > 1024)
                throw new IllegalArgumentException("Samples brauchen Chunk-Blockursprung, Index, Schritt und RNG-Aufrufe");
            for (Draw draw : sample.draws()) if (draw.bound() < 1 || draw.value() < 0 || draw.value() >= draw.bound())
                throw new IllegalArgumentException("Ungueltiger nextInt(bound)-Wert");
        }
    }
    public java.util.function.LongPredicate predicate() {
        var random = new WorldgenRandom(new XoroshiroRandomSource(0));
        return candidate -> {
            for (Sample sample : samples) {
                long population = random.setDecorationSeed(mode == Mode.PAPER_SEED ? candidate : worldSeed, sample.blockX(), sample.blockZ());
                if (mode == Mode.PAPER_SEED) random.setFeatureSeed(population, sample.index(), sample.step());
                else random.setSeed(population + candidate);
                for (Draw draw : sample.draws()) if (random.nextInt(draw.bound()) != draw.value()) return false;
            }
            return true;
        };
    }
}
