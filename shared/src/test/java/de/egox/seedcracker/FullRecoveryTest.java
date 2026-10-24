package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.placement.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Full 2^48-space integration check, enabled for release validation. */
@EnabledIfSystemProperty(named = "egox.fullRecovery", matches = "true")
class FullRecoveryTest {
    @Test void filtersUpper16FromNativeBedrockAndPreservesRemainingAmbiguity() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();long seed=-5837105212680014280L;
        var factory=NativeBiomes.surfaceRandom(seed,false).getOrCreateRandomFactory(net.minecraft.resources.Identifier.parse("minecraft:bedrock_floor"));
        var samples=new ArrayList<BlockObservation>();
        for(int i=0;i<1000&&samples.size()<128;i++) {
            int x=i*37-20000,y=-63+i%4,z=i*-29+123;
            if(factory.at(x,y,z).nextFloat()<(-59.0-y)/5.0)samples.add(new BlockObservation("minecraft:overworld","minecraft:bedrock",x,y,z,"OBSERVED_BEDROCK",""));
        }
        var result=WorldSeedLifting.search(Set.of(seed&LegacyRandom.MASK),0,65535,2,32,()->NativeBedrock.predicate(samples),()->false,(p,c,t)->{},"Bedrock");
        assertEquals(SearchJob.State.COMPLETE,result.state());assertEquals(65536,result.checked());assertTrue(result.seeds().contains(seed));
        assertTrue(result.seeds().size()>1,"This positive-only fixture intentionally leaves ambiguity");assertTrue(result.seeds().size()<32);
        assertEquals(List.of(seed),result.seeds().stream().filter(s->SeedHash.of(s)==SeedHash.of(seed)).toList());
    }
    @Test void recoversUpper16FromGeodePlacementConditionsWithoutUsingTheHash() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();long seed=-5837105212680014280L;var samples=new ArrayList<BlockObservation>();
        for(int i=0;i<5000&&samples.size()<24;i++) {
            var origin=NativeGeode.origin(seed,i*3-1000,i*-7+200);if(origin.isEmpty())continue;var p=origin.get();
            samples.add(new BlockObservation("minecraft:overworld","minecraft:budding_amethyst",p[0]+3,p[1]-2,p[2]+1,"OBSERVED_FEATURE_BLOCK",""));
        }
        assertEquals(24,samples.size());
        var result=WorldSeedLifting.search(Set.of(seed&LegacyRandom.MASK),0,65535,2,32,()->NativeGeode.predicate(samples),()->false,(p,c,t)->{},"Geode");
        assertEquals(SearchJob.State.COMPLETE,result.state());assertEquals(65536,result.checked());assertEquals(List.of(seed),result.seeds());
    }
    @Test void recoversWorldSeedUsingNativeBiomesAcrossAllUpper16BitsWithoutAHash() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        long worldSeed = -5837105212680014280L;
        var coordinates = new ArrayList<BiomeObservation>();
        for (int i = 0; i < 16; i++) coordinates.add(new BiomeObservation(i * 913 - 7000, i % 3 * 16, i * -719 + 3000, ""));
        var samples = NativeBiomes.sample(worldSeed, coordinates, "overworld");
        var first = BiomeEvidence.select(samples, 8);
        long started = System.nanoTime();
        var result = WorldSeedLifting.search(Set.of(worldSeed & LegacyRandom.MASK), 0, 65535, 2, 32,
            () -> seed -> NativeBiomes.matches(seed, first, samples, "overworld"), () -> false, (p,c,t) -> {}, "Native biomes");
        assertEquals(SearchJob.State.COMPLETE, result.state()); assertEquals(65536, result.checked());
        assertEquals(List.of(worldSeed), result.seeds());
        System.out.printf(Locale.ROOT, "Full native biome upper-16 recovery: %.3f seconds%n", (System.nanoTime() - started) / 1e9);
    }
    @Test void recoversSeedAcrossFullPillarStateSpaceWithNativeTriangularStarts() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        long worldSeed = -5837105212680014280L;
        int pillarSeed = (int)(new Random(worldSeed).nextLong() & 65535);
        var placement = new RandomSpreadStructurePlacement(20, 11, RandomSpreadType.TRIANGULAR, 10387313);
        var model = new Placement("test:end_city", 20, 11, 10387313, Placement.Spread.TRIANGULAR);
        var samples = new ArrayList<StructureObservation>(); var locations = new Random(3184);
        for (int i = 0; i < 10; i++) {
            var p = placement.getPotentialStructureChunk(worldSeed, (locations.nextInt(8000) - 4000) * 20, (locations.nextInt(8000) - 4000) * 20);
            samples.add(new StructureObservation(model, Platform.chunkX(p), Platform.chunkZ(p), "minecraft:the_end"));
        }
        var job = new SearchJob("full pillar", 0, 1L << 32, Math.min(16, Runtime.getRuntime().availableProcessors()), 256, () -> {
            var random = new LegacyRandom();
            return index -> { long seed = EndPillars.seedForIndex(pillarSeed, index); for (var o : samples) if (!o.matches(seed, model.vanillaSalt(), random)) return false; return true; };
        });
        job.completion().get(180, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(SearchJob.State.COMPLETE, job.snapshot().state()); assertEquals(1L << 32, job.snapshot().checked());
        assertEquals(List.of(worldSeed & LegacyRandom.MASK), job.snapshot().matches().stream().map(i -> EndPillars.seedForIndex(pillarSeed, i)).toList());
        System.out.printf(Locale.ROOT, "Full pillar state recovery: %.3f seconds%n", job.snapshot().seconds());
    }
    @Test void recoversUnknownSaltAcrossTheEntireSignedIntRange() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        long seed = -5837105212680014280L;
        int actualSalt = -123456789;
        var nativePlacement = new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, actualSalt);
        var model = new Placement("test:salted", 32, 8, 14357617, Placement.Spread.LINEAR);
        var locations = new Random(927584L);
        List<StructureObservation> samples = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            var chunk = nativePlacement.getPotentialStructureChunk(seed, (locations.nextInt(10000)-5000)*32, (locations.nextInt(10000)-5000)*32);
            samples.add(new StructureObservation(model, Platform.chunkX(chunk), Platform.chunkZ(chunk), "minecraft:overworld"));
        }
        var search = new SearchJob("full salt", Integer.MIN_VALUE, 1L << 32, Math.min(16,Runtime.getRuntime().availableProcessors()), 256, () -> {
            var random = new LegacyRandom();
            return salt -> { for (var o : samples) if (!o.matches(seed, salt, random)) return false; return true; };
        });
        search.completion().get(180, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(SearchJob.State.COMPLETE, search.snapshot().state());
        assertEquals(List.of((long)actualSalt), search.snapshot().matches());
        System.out.printf(Locale.ROOT, "Full signed-int salt recovery: %.3f seconds%n", search.snapshot().seconds());
    }
    @Test void recoversFullWorldSeedFromFreshNativePlacementsWithChangedSalts() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        long worldSeed = -5837105212680014280L;
        List<LiftingSearch.Constraint> observations = new ArrayList<>();
        var locations = new Random(90842379L);
        for (int i = 0; i < 7; i++) {
            int salt = 7342331 + (i % 3) * 91619;
            var nativePlacement = new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, salt);
            var chunk = nativePlacement.getPotentialStructureChunk(worldSeed, (locations.nextInt(4000) - 2000) * 32, (locations.nextInt(4000) - 2000) * 32);
            var model = new Placement("test:family" + (i % 3), 32, 8, salt, Placement.Spread.LINEAR);
            observations.add(new LiftingSearch.Constraint(new StructureObservation(model, Platform.chunkX(chunk), Platform.chunkZ(chunk), "minecraft:overworld"), salt));
        }
        List<StructureObservation> extra = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int salt = 10387313;
            var nativePlacement = new RandomSpreadStructurePlacement(32, 5, RandomSpreadType.TRIANGULAR, salt);
            var chunk = nativePlacement.getPotentialStructureChunk(worldSeed, (locations.nextInt(4000) - 2000) * 32, (locations.nextInt(4000) - 2000) * 32);
            var model = new Placement("test:monument", 32, 5, salt, Placement.Spread.TRIANGULAR);
            extra.add(new StructureObservation(model, Platform.chunkX(chunk), Platform.chunkZ(chunk), "minecraft:overworld"));
        }
        long started = System.nanoTime();
        var lower = LiftingSearch.search(observations, 0, LegacyRandom.MASK, Math.min(16, Runtime.getRuntime().availableProcessors()), () -> false, () -> {
            var random = new LegacyRandom();
            return seed -> { for (var o : extra) if (!o.matches(seed, o.placement().vanillaSalt(), random)) return false; return true; };
        });
        assertTrue(lower.contains(worldSeed & LegacyRandom.MASK));
        assertTrue(lower.size() <= 1000, "Fixture needs enough independent placements for hash lifting");
        Set<Long> worlds = new HashSet<>();
        for (long candidate : lower) for (long upper = 0; upper < 65536; upper++) {
            long seed = (upper << 48) | candidate;
            if (SeedHash.of(seed) == SeedHash.of(worldSeed)) worlds.add(seed);
        }
        assertEquals(Set.of(worldSeed), worlds);
        System.out.printf(Locale.ROOT, "Full recovery: %.3f seconds%n", (System.nanoTime() - started) / 1e9);
    }
}
