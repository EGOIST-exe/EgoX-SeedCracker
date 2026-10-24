package de.egox.seedcracker;

import de.egox.seedcracker.core.BlockObservation;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.util.function.LongPredicate;

/** Vanilla transitional bedrock layers only; flat guaranteed layers carry no filter condition. */
public final class NativeBedrock {
    private NativeBedrock() { }
    public static net.minecraft.world.level.levelgen.PositionalRandomFactory factory(long seed, boolean nether, boolean roof) {
        net.minecraft.util.RandomSource root = nether ? new net.minecraft.world.level.levelgen.LegacyRandomSource(seed) : new net.minecraft.world.level.levelgen.XoroshiroRandomSource(seed);
        return root.forkPositional().fromHashOf(Identifier.parse(roof ? "minecraft:bedrock_roof" : "minecraft:bedrock_floor")).forkPositional();
    }
    public static boolean layer(String dimension, int y) {
        return dimension.equals("minecraft:overworld") && y >= -63 && y <= -60
            || dimension.equals("minecraft:the_nether") && (y >= 1 && y <= 4 || y >= 123 && y <= 126);
    }
    public static boolean expected(long seed, BlockObservation sample) {
        if (!layer(sample.dimension(), sample.y())) throw new IllegalArgumentException("Not a supported transitional bedrock layer");
        boolean nether = sample.dimension().equals("minecraft:the_nether"), roof = nether && sample.y() >= 123;
        int bottom = nether ? 0 : -64;
        var factory = factory(seed, nether, roof);
        float draw = factory.at(sample.x(), sample.y(), sample.z()).nextFloat();
        return roof ? !(draw < (127.0 - sample.y()) / 5.0) : draw < (bottom + 5.0 - sample.y()) / 5.0;
    }
    public static LongPredicate predicate(List<BlockObservation> samples) {
        var supported = samples.stream().filter(s -> s.block().equals("minecraft:bedrock") && layer(s.dimension(), s.y())).toList();
        if (supported.isEmpty()) throw new IllegalStateException("Noch keine Bedrock-Uebergangsschichten beobachtet");
        return seed -> {
            var factories = new HashMap<String, net.minecraft.world.level.levelgen.PositionalRandomFactory>();
            for (var sample : supported) {
                boolean nether = sample.dimension().equals("minecraft:the_nether"), roof = nether && sample.y() >= 123;
                String key = sample.dimension() + (roof ? ":roof" : ":floor");
                var factory = factories.computeIfAbsent(key, k -> factory(seed, nether, roof));
                float draw = factory.at(sample.x(), sample.y(), sample.z()).nextFloat();
                boolean bedrock = roof ? !(draw < (127.0 - sample.y()) / 5.0) : draw < ((nether ? 5.0 : -59.0) - sample.y()) / 5.0;
                if (!bedrock) return false;
            }
            return true;
        };
    }
}
