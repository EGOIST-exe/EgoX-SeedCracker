package de.egox.seedcracker;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.biome.*;
import java.util.*;

/** The installed vanilla biome source determines decoration indices; never hardcode them across versions. */
public final class NativeFeatureOrder {
    private static List<FeatureSorter.StepFeatureData> steps;
    private NativeFeatureOrder() { }
    public static synchronized int[] get(String id) {
        var data=NativeBiomes.registries();
        if(steps==null) {
            var source=MultiNoiseBiomeSource.createFromPreset(data.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            steps=FeatureSorter.buildFeaturesPerStep(List.copyOf(source.possibleBiomes()), holder->holder.value().getGenerationSettings().features(),true);
        }
        var feature=data.lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE,Identifier.parse(id))).value();
        for(int i=0;i<steps.size();i++){int index=steps.get(i).features().indexOf(feature);if(index>=0)return new int[]{index,i};}
        throw new IllegalArgumentException("Native feature index not found: "+id);
    }
}
