package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.*;
import net.minecraft.world.level.levelgen.structure.placement.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftLogicTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void seedHashMatchesMinecraftForAllSignedEdgeCases() {
        for (long seed : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 384905238495082349L})
            assertEquals(BiomeManager.obfuscateSeed(seed), SeedHash.of(seed));
    }
    @Test void placementMatchesNativeMinecraftAtNegativeAndPositiveCoordinates() {
        var seeds = new Random(741);
        for (Placement.Spread spread : Placement.Spread.values()) for (int i = 0; i < 1000; i++) {
            long seed = seeds.nextLong(); int salt = seeds.nextInt(); int x = seeds.nextInt(2000000) - 1000000, z = seeds.nextInt(2000000) - 1000000;
            var nativePlacement = new RandomSpreadStructurePlacement(32, 8,
                spread == Placement.Spread.LINEAR ? RandomSpreadType.LINEAR : RandomSpreadType.TRIANGULAR, salt);
            var chunk = nativePlacement.getPotentialStructureChunk(seed, x, z);
            var own = new Placement("test:structure", 32, 8, salt, spread);
            assertTrue(own.matches(seed, salt, Platform.chunkX(chunk), Platform.chunkZ(chunk), new LegacyRandom()));
            assertFalse(own.matches(seed, salt, Math.floorDiv(x, 32) * 32 + 31, Platform.chunkZ(chunk), new LegacyRandom()));
        }
    }
    @Test void bootstrapProfilesAreReadFromThisMinecraftVersion() {
        var pyramid = NativeProfiles.get("desert_pyramid");
        assertEquals(32, pyramid.spacing()); assertEquals(8, pyramid.separation()); assertEquals(14357617, pyramid.vanillaSalt());
        assertEquals(Placement.Spread.TRIANGULAR, NativeProfiles.get("monument").spread());
    }
    @Test void signatureCoordinatesMatchNativeStructurePiecesInEveryOrientation() throws Exception {
        var project = StructurePiece.class.getDeclaredMethod("getWorldPos", int.class, int.class, int.class);
        project.setAccessible(true);
        for (var signature : StructureSignatures.SIGNATURES) {
            Set<net.minecraft.core.Direction> seen = new HashSet<>();
            for (int seed = 0; seed < 100; seed++) {
                StructurePiece piece = switch (signature.type()) {
                    case "minecraft:desert_pyramid" -> new DesertPyramidPiece(RandomSource.create(seed * 341873128712L), -160, 304);
                    case "minecraft:swamp_hut" -> new SwampHutPiece(RandomSource.create(seed * 341873128712L), -160, 304);
                    default -> new JungleTemplePiece(RandomSource.create(seed * 341873128712L), -160, 304);
                };
                seen.add(piece.getOrientation());
                for (var marker : signature.markers()) {
                    BlockPos expected = (BlockPos)project.invoke(piece, marker.x(), marker.y(), marker.z());
                    BlockPos actual = new BlockPos(-160, piece.getBoundingBox().minY(), 304).offset(StructureSignatures.offset(signature, marker, piece.getOrientation()));
                    assertEquals(expected, actual);
                }
            }
            assertEquals(4, seen.size());
        }
    }
    @Test void intactSignaturesRecoverExactStartAndRejectDamagedPatterns() {
        for (var s : StructureSignatures.SIGNATURES) for (var facing : List.of(net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST)) {
            var origin = new BlockPos(-160, 64, 304);
            Map<BlockPos, net.minecraft.world.level.block.Block> blocks = new HashMap<>();
            for (var m : s.markers()) blocks.put(origin.offset(StructureSignatures.offset(s, m, facing)), m.block());
            var anchor = origin.offset(StructureSignatures.offset(s, s.markers().getFirst(), facing));
            var result = StructureSignatures.detect(anchor, s.markers().getFirst().block(), p -> blocks.getOrDefault(p, net.minecraft.world.level.block.Blocks.AIR));
            assertEquals(new StructureSignatures.Start(s.type(), -10, 19), result.orElseThrow());
            blocks.remove(origin.offset(StructureSignatures.offset(s, s.markers().getLast(), facing)));
            assertTrue(StructureSignatures.detect(anchor, s.markers().getFirst().block(), p -> blocks.getOrDefault(p, net.minecraft.world.level.block.Blocks.AIR)).isEmpty());
        }
    }
    @Test void paperFeatureSeedReplayFindsIndependentSeed() throws Exception {
        long featureSeed = 4321;
        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(new net.minecraft.world.level.levelgen.XoroshiroRandomSource(0));
        List<FeatureTrace.Sample> samples = new ArrayList<>();
        for (int x : new int[]{-160, 320, 2048}) {
            long population = random.setDecorationSeed(featureSeed, x, -512);
            random.setFeatureSeed(population, 3, 6);
            List<FeatureTrace.Draw> draws = new ArrayList<>();
            for (int bound : new int[]{16, 16, 384, 100, 256}) draws.add(new FeatureTrace.Draw(bound, random.nextInt(bound)));
            samples.add(new FeatureTrace.Sample(x, -512, 3, 6, draws));
        }
        var trace = new FeatureTrace("minecraft:test", FeatureTrace.Mode.PAPER_SEED, 9999999, samples); trace.validate();
        var job = new SearchJob("feature", 0, 10000, 4, 10, trace::predicate);
        job.completion().get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(List.of(featureSeed), job.snapshot().matches());
    }
    @Test void everyCommandStartsWithEgoxAndDiscordIsPresent() {
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
        EgoXCommands.register(dispatcher);
        assertEquals(List.of("egox"), dispatcher.getRoot().getChildren().stream().map(com.mojang.brigadier.tree.CommandNode::getName).toList());
        assertNotNull(dispatcher.getRoot().getChild("egox").getChild("discord").getCommand());
        assertTrue(dispatcher.parse("egox discord", null).getExceptions().isEmpty());
    }
    @Test void nativeBiomeOracleWorksForEveryPresetAndRejectsOtherSeeds() {
        var coordinates = List.of(new BiomeObservation(0,16,0,""), new BiomeObservation(-1000,0,500,""),
            new BiomeObservation(3200,32,-4500,""), new BiomeObservation(20319,-8,6142,""));
        for (String preset : List.of("overworld", "large_biomes", "amplified")) {
            var samples = NativeBiomes.sample(123456789L, coordinates, preset);
            assertTrue(NativeBiomes.matches(123456789L, samples, preset));
            assertFalse(NativeBiomes.matches(-92182377812L, samples, preset));
            assertTrue(NativeBiomes.compare(123456789L, samples, preset).matches());
            assertFalse(NativeBiomes.compare(-92182377812L, samples, preset).matches());
            assertTrue(samples.stream().allMatch(sample -> sample.biome().startsWith("minecraft:")));
        }
    }
}
