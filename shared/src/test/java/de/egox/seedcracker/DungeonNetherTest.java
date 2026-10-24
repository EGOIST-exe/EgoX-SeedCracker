package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonNetherTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @TempDir Path root;
    private Object nativeRoom() throws Exception {
        net.minecraft.resources.ResourceKey registry;
        try { registry = (ResourceKey)Registries.class.getField("CONFIGURED_FEATURE").get(null); }
        catch (NoSuchFieldException ignored) { registry = (ResourceKey)Registries.class.getField("FEATURE").get(null); }
        return NativeBiomes.registries().lookupOrThrow(registry).getOrThrow(ResourceKey.create(registry, Identifier.parse("minecraft:monster_room"))).value();
    }
    private DungeonObservation placeNative(Object feature, WorldgenRandom random, BlockPos origin, boolean validTerrain) throws Exception {
        var blocks = new HashMap<BlockPos, BlockState>(); var radii = new ArrayList<Integer>();
        var rng = (RandomSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{RandomSource.class}, (p, method, args) -> {
            Object value = method.invoke(random, args);
            if (method.getName().equals("nextInt") && radii.size() < 2) radii.add((int)value + 2);
            return value;
        });
        java.util.function.Function<BlockPos, BlockState> state = pos -> {
            if (!validTerrain) return Blocks.AIR.defaultBlockState();
            if (blocks.containsKey(pos)) return blocks.get(pos);
            if (radii.size() == 2 && pos.getX() == origin.getX() - radii.getFirst() - 1 && pos.getZ() == origin.getZ() && (pos.getY() == origin.getY() || pos.getY() == origin.getY() + 1)) return Blocks.CAVE_AIR.defaultBlockState();
            return Blocks.STONE.defaultBlockState();
        };
        var world = (net.minecraft.world.level.WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{net.minecraft.world.level.WorldGenLevel.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getBlockState" -> state.apply((BlockPos)args[0]);
            case "isEmptyBlock" -> state.apply((BlockPos)args[0]).isAir();
            case "getMinY" -> -64;
            case "getBlockEntity" -> {
                var pos = (BlockPos)args[0]; var current = state.apply(pos);
                yield current.is(Blocks.SPAWNER) ? new net.minecraft.world.level.block.entity.SpawnerBlockEntity(pos, current)
                    : current.is(Blocks.CHEST) ? new net.minecraft.world.level.block.entity.ChestBlockEntity(pos, current) : null;
            }
            case "ensureCanWrite" -> true;
            case "setBlock" -> { blocks.put(((BlockPos)args[0]).immutable(), (BlockState)args[1]); yield true; }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var place = feature.getClass().getMethod("place", net.minecraft.world.level.WorldGenLevel.class, net.minecraft.world.level.chunk.ChunkGenerator.class, RandomSource.class, BlockPos.class);
        assertEquals(validTerrain, place.invoke(feature, world, null, rng, origin));
        if (!validTerrain) return null;
        var result = DungeonEvidence.detect(origin, p -> state.apply(p).getBlock()).orElseThrow();
        assertEquals(1, result.layouts().size()); assertTrue(result.patternBits() > 20);
        var layout = result.layouts().getFirst(); assertEquals(radii.get(0), layout.radiusX()); assertEquals(radii.get(1), layout.radiusZ());
        var broken = new HashMap<>(blocks); broken.put(origin.below(), Blocks.AIR.defaultBlockState());
        assertTrue(DungeonEvidence.detect(origin, p -> broken.getOrDefault(p, state.apply(p)).getBlock()).isEmpty());
        return result;
    }
    @Test void nativeDungeonFloorsAndMixedPriorAttemptsKeepTheTrueSeed() throws Exception {
        var feature = nativeRoom();
        for (long seed : new long[]{123456789L, Long.MIN_VALUE}) for (var settings : NativeDungeon.settings()) {
            int cx = -17, cz = 9;
            var random = new WorldgenRandom(new XoroshiroRandomSource(0));
            long population = random.setDecorationSeed(seed, cx * 16, cz * 16); random.setFeatureSeed(population, settings.index(), settings.step());
            DungeonObservation room = null;
            for (int i = 0; i < settings.count(); i++) {
                var origin = new BlockPos(cx * 16 + random.nextInt(16), settings.minY(), cz * 16 + random.nextInt(16));
                origin = origin.atY(settings.minY() + random.nextInt(settings.maxY() - settings.minY() + 1));
                if (i % 3 == 1) continue; // A native biome filter can omit the feature entirely.
                var next = placeNative(feature, random, origin, i % 3 != 2);
                if (next != null) room = next;
            }
            assertNotNull(room); assertTrue(NativeDungeon.predicate(List.of(room), () -> false).test(seed));
            assertFalse(NativeDungeon.predicate(List.of(room), () -> false).test(seed + 1));
        }
    }
    @Test void spawnerAloneDoesNotCountAndFloorInformationMatchesTheNativeDistribution() {
        assertTrue(DungeonEvidence.detect(BlockPos.ZERO, p -> p.equals(BlockPos.ZERO) ? Blocks.SPAWNER : Blocks.STONE).isEmpty());
        var tiles = new ArrayList<Integer>(Collections.nCopies(49, 1)); tiles.set(0, 0);
        var floor = new DungeonObservation.Layout(2, 2, tiles);
        assertEquals(2 + 48 * -Math.log(.75) / Math.log(2), floor.patternBits(), 1e-10);
        assertThrows(IllegalArgumentException.class, () -> new DungeonObservation.Layout(2, 2, List.of(0)));
        assertTrue(NativeDungeon.rejects(Integer.MAX_VALUE, 7)); assertFalse(NativeDungeon.rejects(0, 7));
    }
    @Test void nativeDungeonSettingsSeparateDeepAndSurfaceAttempts() {
        var settings = NativeDungeon.settings(); assertEquals(2, settings.size());
        assertEquals(10, settings.getFirst().count()); assertEquals(0, settings.getFirst().minY()); assertEquals(319, settings.getFirst().maxY());
        assertEquals(4, settings.getLast().count()); assertEquals(-58, settings.getLast().minY()); assertEquals(-1, settings.getLast().maxY());
        assertNotEquals(settings.getFirst().index(), settings.getLast().index());
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "egox.fullRecovery", matches = "true")
    void nativeDungeonFloorFiltersAllUpper16BitsWithoutAHash() throws Exception {
        long seed = -5837105212680014280L; var settings = NativeDungeon.settings().getLast(); int cx = -7, cz = 13;
        var random = new WorldgenRandom(new XoroshiroRandomSource(0)); long population = random.setDecorationSeed(seed, cx * 16, cz * 16); random.setFeatureSeed(population, settings.index(), settings.step());
        int x = cx * 16 + random.nextInt(16), z = cz * 16 + random.nextInt(16), y = settings.minY() + random.nextInt(settings.maxY() - settings.minY() + 1);
        var room = placeNative(nativeRoom(), random, new BlockPos(x, y, z), true);
        var result = WorldSeedLifting.search(List.of(seed & LegacyRandom.MASK), 0, 65535, 2, 32,
            () -> NativeDungeon.predicate(List.of(room), () -> false), () -> false, (phase, done, total) -> {}, "Dungeon test");
        assertEquals(SearchJob.State.COMPLETE, result.state()); assertEquals(65536, result.checked()); assertEquals(List.of(seed), result.seeds());
    }
    @Test void netherBiomesUseTheirNativeGeneratorAndPreserveLegacyUpperBitAmbiguity() {
        long seed = 123456789L; var coordinates = new ArrayList<BiomeObservation>();
        for (int i = 0; i < 256; i++) coordinates.add(new BiomeObservation(i * 113 - 12000, 16, i * -71 + 5000, "", "minecraft:the_nether"));
        var samples = NativeBiomes.sample(seed, coordinates, "overworld");
        assertTrue(samples.stream().map(BiomeObservation::biome).distinct().count() >= 4);
        assertTrue(samples.stream().allMatch(s -> s.dimension().equals("minecraft:the_nether")));
        assertEquals(samples, NativeBiomes.sample(seed ^ (1L << 48), coordinates, "overworld"));
        assertEquals(samples, NativeBiomes.sample(seed, coordinates, "large_biomes"));
        assertTrue(NativeBiomes.matches(seed, samples, "overworld")); assertFalse(NativeBiomes.matches(seed + 1, samples, "overworld"));
    }
    @Test void mixedBiomeDimensionsDoNotOverwriteEachOtherAndOldDataRemainsOverworld() throws Exception {
        var identity = new LibrarySnapshot.Identity("player", "server", "1", "test"); var store = new ObservationStore(root);
        var old = com.google.gson.JsonParser.parseString("{\"quartX\":1,\"quartY\":16,\"quartZ\":2,\"biome\":\"minecraft:plains\"}");
        var overworld = new com.google.gson.Gson().fromJson(old, BiomeObservation.class); assertEquals("minecraft:overworld", overworld.dimension());
        var nether = new BiomeObservation(1, 16, 2, "minecraft:warped_forest", "minecraft:the_nether");
        store.append(identity, "BIOME", overworld); store.append(identity, "BIOME", nether);
        assertEquals(2, store.load(identity).orElseThrow().biomes().size());
        var coords = List.of(new BiomeObservation(1, 16, 2, ""), new BiomeObservation(1, 16, 2, "", "minecraft:the_nether"));
        var samples = NativeBiomes.sample(123, coords, "overworld"); assertTrue(NativeBiomes.compare(123, samples, "overworld").matches());
        var working = new LinkedHashMap<String, BiomeObservation>();
        for (String dimension : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:overworld")) for (int i = 0; i < 100; i++) {
            var sample = new BiomeObservation(i, 16, 0, dimension.equals("minecraft:overworld") ? "minecraft:plains" : "minecraft:warped_forest", dimension);
            working.put(sample.key(), sample); BiomeEvidence.trimDimensions(working, 16);
        }
        assertEquals(16, working.size()); assertEquals(8, working.values().stream().filter(s -> s.dimension().equals("minecraft:overworld")).count());
        assertEquals(8, working.values().stream().filter(s -> s.dimension().equals("minecraft:the_nether")).count());
    }
    @Test void dungeonJournalSnapshotAndRestoreRetainTheFloorAndDoNotDuplicateIt() throws Exception {
        var identity = new LibrarySnapshot.Identity("player", "server", "1", "test"); var store = new ObservationStore(root);
        var room = new DungeonObservation(-1, -12, -17, List.of(new DungeonObservation.Layout(2, 2, Collections.nCopies(49, 1))), "now");
        store.append(identity, "DUNGEON", room); var loaded = store.load(identity).orElseThrow(); assertEquals(List.of(room), loaded.dungeons());
        var event = com.google.gson.JsonParser.parseString(Files.readAllLines(store.directory(identity).resolve("observations.jsonl")).getFirst()).getAsJsonObject().getAsJsonObject("data");
        assertEquals(-1, event.get("chunkX").getAsInt()); assertEquals(-2, event.get("chunkZ").getAsInt()); assertEquals("minecraft:overworld", event.get("dimension").getAsString());
        store.save(loaded); store.append(identity, "DUNGEON", room); assertEquals(List.of(room), store.load(identity).orElseThrow().dungeons());
        DungeonEvidence.clear();
        try { DungeonEvidence.restore(loaded.dungeons()); DungeonEvidence.restore(loaded.dungeons()); assertEquals(1, DungeonEvidence.snapshot().size()); }
        finally { DungeonEvidence.clear(); }
    }
    @Test void batchDungeonDetectionsStayQuietAndDoNotCountRescansTwice() {
        // No Minecraft/chat client is initialized: an automatic chat notification would fail here.
        DungeonEvidence.clear(); long before = AutoCracker.dataRevision();
        var layout = new DungeonObservation.Layout(2, 2, Collections.nCopies(49, 1));
        try {
            for (int i=0;i<100;i++) {
                var room = new DungeonObservation(i*32, 20, -17, List.of(layout), "first");
                assertTrue(DungeonEvidence.add(room));
                assertFalse(DungeonEvidence.add(new DungeonObservation(room.x(), room.y(), room.z(), room.layouts(), "rescan")));
            }
            assertEquals(100, DungeonEvidence.snapshot().size()); assertEquals(before+100, AutoCracker.dataRevision());
            var summary = DungeonEvidence.summarize(DungeonEvidence.snapshot());
            assertEquals(100, summary.rooms()); assertEquals(layout.patternBits()*100, summary.patternBits(), 1e-8);
            assertTrue(summary.compact().contains("Muster-Bits")); assertFalse(summary.describe().contains("+0"));
            assertTrue(summary.describe().contains("Kandidatenfilter"));
        } finally { DungeonEvidence.clear(); }
    }
    @Test void dungeonSummaryKeepsSameChunkPatternsSeparateFromStructureLifting() {
        var layout = new DungeonObservation.Layout(2, 2, Collections.nCopies(49, 1));
        var rooms = List.of(new DungeonObservation(0,20,0,List.of(layout),"a"), new DungeonObservation(0,40,0,List.of(layout),"b"));
        var summary = DungeonEvidence.summarize(rooms);
        assertEquals(2, summary.rooms()); assertEquals(layout.patternBits(), summary.patternBits(), 1e-8);
        assertEquals(0, ObservationBits.summarize(List.of()).liftingBits());
    }
}
