package de.egox.seedcracker;

import de.egox.seedcracker.core.BiomeObservation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import java.util.List;

public final class NativeBiomes {
    private static class Vanilla { static final HolderLookup.Provider DATA = VanillaRegistries.createWorldLookup(); }
    public static HolderLookup.Provider registries() { return Vanilla.DATA; }
    public static RandomState surfaceRandom(long seed, boolean nether) {
        var data = registries();
        var settings = data.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(nether ? NoiseGeneratorSettings.NETHER : NoiseGeneratorSettings.OVERWORLD).value();
        return RandomState.create(data.lookupOrThrow(Registries.NOISE), seed, settings);
    }
    public static boolean matches(long seed, List<BiomeObservation> observations, String preset) {
        var resolver = resolver(seed, preset);
        for (BiomeObservation sample : observations) if (!sample.biome().equals(resolver.apply(sample))) return false;
        return true;
    }
    public static de.egox.seedcracker.core.BiomeEvidence.Comparison compare(long seed, List<BiomeObservation> observations, String preset) {
        return de.egox.seedcracker.core.BiomeEvidence.compare(observations, resolver(seed, preset));
    }
    public static boolean matches(long seed, List<BiomeObservation> first, List<BiomeObservation> all, String preset) {
        return de.egox.seedcracker.core.BiomeEvidence.matches(first, all, resolver(seed, preset));
    }
    public static List<BiomeObservation> sample(long seed, List<BiomeObservation> coordinates, String preset) {
        var resolver = resolver(seed, preset);
        return coordinates.stream().map(p -> new BiomeObservation(p.quartX(), p.quartY(), p.quartZ(), resolver.apply(p), p.dimension())).toList();
    }
    private static java.util.function.Function<BiomeObservation, String> resolver(long seed, String preset) {
        var resolvers = new java.util.HashMap<String, java.util.function.Function<BiomeObservation, String>>();
        return sample -> resolvers.computeIfAbsent(sample.dimension(), dimension -> createResolver(seed, preset, dimension)).apply(sample);
    }
    private static java.util.function.Function<BiomeObservation, String> createResolver(long seed, String preset, String dimension) {
        var data = registries();
        boolean nether = dimension.equals("minecraft:the_nether");
        var settingsKey = nether ? NoiseGeneratorSettings.NETHER : switch (preset) {
            case "large_biomes" -> NoiseGeneratorSettings.LARGE_BIOMES;
            case "amplified" -> NoiseGeneratorSettings.AMPLIFIED;
            default -> NoiseGeneratorSettings.OVERWORLD;
        };
        var settings = data.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settingsKey).value();
        var random = RandomState.create(data.lookupOrThrow(Registries.NOISE), seed, settings);
        var sampler = random.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
        var source = MultiNoiseBiomeSource.createFromPreset(data.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
            .getOrThrow(nether ? MultiNoiseBiomeSourceParameterLists.NETHER : MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var resolver = source.createResolver(sampler);
        return sample -> resolver.getNoiseBiome(sample.quartX(), sample.quartY(), sample.quartZ())
            .unwrapKey().orElseThrow().identifier().toString();
    }
}
