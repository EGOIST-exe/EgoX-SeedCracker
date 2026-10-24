package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ObservabilityTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void triangularInformationMatchesEnumeratedDrawProbabilities() {
        for (int bound : new int[]{1, 2, 9, 24, 60}) {
            int[] counts = new int[bound];
            for (int a = 0; a < bound; a++) for (int b = 0; b < bound; b++) counts[(a + b) / 2]++;
            double totalProbability = 0;
            for (int i = 0; i < bound; i++) {
                double probability = (double)counts[i] / (bound * bound);
                assertEquals(-Math.log(probability) / Math.log(2), ObservationBits.coordinateBits(i, bound, Placement.Spread.TRIANGULAR), 1e-12);
                totalProbability += Math.pow(2, -ObservationBits.coordinateBits(i, bound, Placement.Spread.TRIANGULAR));
            }
            assertEquals(1, totalProbability, 1e-12);
        }
    }
    @Test void duplicateRegionsAndDimensionCopiesDoNotInflateBits() {
        var profile = new Placement("test:a", 32, 8, 1, Placement.Spread.LINEAR);
        var result = ObservationBits.summarize(List.of(new StructureObservation(profile, -32, 64, "a"), new StructureObservation(profile, -31, 64, "a"), new StructureObservation(profile, -32, 64, "b")));
        assertEquals(1, result.starts()); assertEquals(1, result.liftingStarts());
        assertEquals(2 * Math.log(24) / Math.log(2), result.liftingBits(), 1e-12);
        assertFalse(ObservationBits.liftable(new Placement("test:b", 32, 16, 0, Placement.Spread.LINEAR)));
    }
    @Test void progressIsMonotonicWithinEachPhaseAndResetsOnPhaseChange() {
        var progress = new CrackProgress();
        progress.update("A", 20, 100); progress.update("A", 10, 100);
        assertEquals(20, progress.snapshot().checked()); assertEquals(.2, progress.snapshot().fraction());
        progress.update("B", 0, 50); assertEquals(0, progress.snapshot().checked());
        progress.update("B", 99, 50); assertEquals(1, progress.snapshot().fraction());
    }
    @Test void pillarInverseCoversTheCorrectWorldSeedStateForSignedSeeds() {
        var random = new Random(9001);
        for (int i = 0; i < 10000; i++) {
            long seed = random.nextLong(), state = EndPillars.stateAfterLong(seed);
            int pillarSeed = (int)(new Random(seed).nextLong() & 65535);
            assertEquals(pillarSeed, (state >>> 16) & 65535);
            long index = ((state >>> 32) << 16) | (state & 65535);
            assertEquals(seed & LegacyRandom.MASK, EndPillars.seedForIndex(pillarSeed, index));
        }
    }
    @Test void monumentArchesRecoverNativeStartChunksInAllOrientations() throws Exception {
        var method = net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding.class.getDeclaredMethod("generateEntranceArchs",
            net.minecraft.world.level.WorldGenLevel.class, net.minecraft.util.RandomSource.class, net.minecraft.world.level.levelgen.structure.BoundingBox.class);
        method.setAccessible(true);
        var project = net.minecraft.world.level.levelgen.structure.StructurePiece.class.getDeclaredMethod("getWorldPos", int.class, int.class, int.class); project.setAccessible(true);
        for (var direction : List.of(net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST)) {
            int startX = -160, startZ = -304;
            var piece = new net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding(net.minecraft.util.RandomSource.create(143), startX - 29, startZ - 29, direction);
            var blocks = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
            var world = (net.minecraft.world.level.WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{net.minecraft.world.level.WorldGenLevel.class}, (proxy, call, args) -> {
                return switch (call.getName()) {
                    case "getBlockState" -> blocks.getOrDefault(args[0], Blocks.WATER.defaultBlockState());
                    case "getFluidState" -> blocks.getOrDefault(args[0], Blocks.WATER.defaultBlockState()).getFluidState();
                    case "scheduleTick" -> null;
                    case "setBlock" -> { blocks.put(((BlockPos)args[0]).immutable(), (net.minecraft.world.level.block.state.BlockState)args[1]); yield true; }
                    case "getSeaLevel" -> 63;
                    case "getMinY" -> -64;
                    case "ensureCanWrite" -> true;
                    default -> throw new UnsupportedOperationException(call.getName());
                };
            });
            method.invoke(piece, world, net.minecraft.util.RandomSource.create(1), net.minecraft.world.level.levelgen.structure.BoundingBox.infinite());
            BlockPos anchor = (BlockPos)project.invoke(piece, 26, 5, 5);
            var result = MonumentFinder.detect(anchor, pos -> blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()).getBlock()).orElseThrow();
            assertEquals(-10, result.chunkX()); assertEquals(-19, result.chunkZ());
        }
    }
    @Test void pillarPermutationMatchesNativeMinecraftAndFindsItsSeed() throws Exception {
        Class<?> loader;
        try { loader = Class.forName("net.minecraft.world.level.levelgen.feature.EndSpikeFeature$SpikeCacheLoader"); }
        catch (ClassNotFoundException ignored) { loader = Class.forName("net.minecraft.world.level.levelgen.feature.SpikeFeature$SpikeCacheLoader"); }
        var constructor = loader.getDeclaredConstructor(); constructor.setAccessible(true);
        var load = loader.getDeclaredMethod("load", Long.class); load.setAccessible(true);
        for (int seed : new int[]{0, 1, 4321, 65535}) {
            var spikes = (List<?>)load.invoke(constructor.newInstance(), (long)seed);
            int[] order = EndPillars.order(seed), heights = new int[10];
            for (int i = 0; i < 10; i++) {
                heights[i] = (int)spikes.get(i).getClass().getMethod("getHeight").invoke(spikes.get(i));
                assertEquals(76 + order[i] * 3, heights[i]);
            }
            assertTrue(EndPillars.candidates(heights).contains(seed));
        }
    }
    @Test void commandTreeHasConsistentTogglesAndDataCommands() {
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
        EgoXCommands.register(dispatcher);
        for (String command : List.of("egox cracker seed ON", "egox cracker seed OFF", "egox cracker salt on", "egox cracker salt off",
                "egox cracker status", "egox cracker seed structures 0 100", "egox cracker seed lift 12345", "egox cracker seed biomes search 0 100", "egox cracker salt crack minecraft:desert_pyramid 12345 -100 100", "egox cracker salt set minecraft:desert_pyramid 12345",
                "egox db bits", "egox db seeds", "egox db seeds minecraft:trial_chambers", "egox db data", "egox db export", "egox db upload OFF", "egox render outlines ON", "egox render outlines off", "egox render outlines list",
                "egox render outlines minecraft:shipwreck OFF", "egox render outlines shipwreck ON", "egox render outlines egox:geode ON", "egox render outlines mob_spawner off",
                "egox render hud OFF", "egox render labels ON", "egox render features OFF", "egox render range 512", "egox finder list", "egox finder rescan",
                "egox cracker biomes ON", "egox cracker biomes OFF", "egox cracker biomes status", "egox db biomes",
                "egox cracker seed biomes ON", "egox cracker biomes preset large_biomes", "egox cracker biomes preset overworld", "egox cracker biomes preset amplified",
                "egox cracker ore ON", "egox cracker ore OFF", "egox cracker ore status", "egox cracker ore search -100 100", "egox cracker ore feature -100 100",
                "egox cracker bedrock ON", "egox cracker bedrock OFF", "egox cracker bedrock search -100 100", "egox cracker bedrock status",
                "egox cracker geode ON", "egox cracker geode OFF", "egox cracker geode status", "egox cracker geode search -100 100",
                "egox cracker dungeon ON", "egox cracker dungeon OFF", "egox cracker dungeon status", "egox cracker dungeon search -100 100",
                "egox db ores", "egox db library", "egox db library list", "egox db library open", "egox db library save", "egox db library restore", "egox settings", "egox gui")) {
            var parsed = dispatcher.parse(command, null);
            assertTrue(parsed.getExceptions().isEmpty(), command); assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
        assertTrue(dispatcher.parse("egox render range 10000", null).getReader().canRead());
    }
    @Test void outlineInjectionHasAnUnambiguousNativeTargetAndCollector() throws Exception {
        Class<?> target; String method, collector;
        try {
            target = Class.forName("net.minecraft.client.renderer.extract.LevelExtractor", false, getClass().getClassLoader());
            method = "extract"; collector = "collectPerFrameMainThreadGizmos";
        } catch (ClassNotFoundException ignored) {
            target = Class.forName("net.minecraft.client.renderer.LevelRenderer", false, getClass().getClassLoader());
            method = "renderLevel"; collector = "collectPerFrameGizmos";
        }
        String methodName = method;
        assertEquals(1, Arrays.stream(target.getDeclaredMethods()).filter(m -> m.getName().equals(methodName)).count());
        assertEquals(net.minecraft.gizmos.Gizmos.TemporaryCollection.class, target.getMethod(collector).getReturnType());
        try (var input = getClass().getResourceAsStream("/egoxseedcracker.mixins.json")) {
            String config = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(config.contains("StructureOutlineMixin"));
        }
    }
    @Test void hudStaysWithinScreenAndProgressIsClamped() {
        for (int screenWidth : new int[]{240, 480, 960}) {
            var rectangles = new ArrayList<int[]>(); var strings = new ArrayList<String>();
            var canvas = new StatusDisplay.Canvas() {
                public int width() { return screenWidth; }
                public void fill(int x0, int y0, int x1, int y1, int color) { rectangles.add(new int[]{x0,y0,x1,y1}); }
                public void text(String value, int x, int y, int color, int maxWidth) { assertTrue(x + maxWidth <= screenWidth); strings.add(value); }
            };
            GlassHud.render(canvas, new GlassHud.Model("SUCHT", "Seed ON / Salt ON", "8 Starts | ~49.8 Bits", "Phase 2/4", "50%", "Rest ~10s", 2));
            assertTrue(strings.contains("Phase 2/4")); assertTrue(strings.contains("Rest ~10s"));
            assertTrue(rectangles.stream().allMatch(r -> r[0] >= 0 && r[2] <= screenWidth && r[0] <= r[2] && r[1] <= r[3]));
            int[] background = rectangles.get(rectangles.size() - 3), progress = rectangles.get(rectangles.size() - 2);
            assertEquals(background[2], progress[2]);
            int minX = rectangles.stream().mapToInt(r -> r[0]).min().orElseThrow(), maxX = rectangles.stream().mapToInt(r -> r[2]).max().orElseThrow();
            assertEquals(screenWidth, minX + maxX, "HUD must be horizontally centered");
            assertTrue(rectangles.stream().allMatch(r -> r[3] <= 84), "HUD must stay compact at the top");
        }
    }
    @Test void displayMigrationIsOnceOnlyAndPreservesDatabaseSettings() {
        var json = com.google.gson.JsonParser.parseString("{\"hud\":true,\"outlineRange\":192,\"autoUpload\":false,\"uploadUrl\":\"keep-me\"}").getAsJsonObject();
        assertTrue(ConfigMigration.update(json)); assertFalse(json.get("hud").getAsBoolean()); assertEquals(512,json.get("outlineRange").getAsInt());
        assertFalse(json.get("autoUpload").getAsBoolean()); assertEquals("keep-me",json.get("uploadUrl").getAsString());
        json.addProperty("hud",true); json.addProperty("outlineRange",128);
        assertFalse(ConfigMigration.update(json)); assertTrue(json.get("hud").getAsBoolean()); assertEquals(128,json.get("outlineRange").getAsInt());
    }
    @Test void toggleCompletionsHaveNoCaseDuplicates() throws Exception {
        var argument = new OnOffArgument();
        var suggestions = argument.listSuggestions(null,new com.mojang.brigadier.suggestion.SuggestionsBuilder("",0)).get().getList();
        assertEquals(2,suggestions.size()); assertEquals(Set.of("ON","OFF"),suggestions.stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).collect(java.util.stream.Collectors.toSet()));
        assertTrue(argument.parse(new com.mojang.brigadier.StringReader("on"))); assertFalse(argument.parse(new com.mojang.brigadier.StringReader("off")));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->argument.parse(new com.mojang.brigadier.StringReader("yes")));
    }
    @Test void outlineDefaultsDisableOnlyGeodesAndKeepIndependentTypeChoices() {
        var disabled = OutlineVisibility.defaults();
        assertEquals(Set.of("egox:geode"), disabled);
        for (String type : OutlineVisibility.types()) assertEquals(!type.equals("egox:geode"), OutlineVisibility.enabled(disabled, type), type);
        assertEquals("egox:geode", OutlineVisibility.resolve("geode"));
        assertEquals("egox:mob_spawner", OutlineVisibility.resolve("mob_spawner"));
        assertEquals("minecraft:shipwreck", OutlineVisibility.resolve("shipwreck"));
        assertThrows(IllegalArgumentException.class, () -> OutlineVisibility.resolve("missing_type"));
        OutlineVisibility.set(disabled, "minecraft:shipwreck", false);
        OutlineVisibility.set(disabled, "egox:geode", true);
        assertFalse(OutlineVisibility.enabled(disabled, "minecraft:shipwreck"));
        assertTrue(OutlineVisibility.enabled(disabled, "egox:geode"));
        assertTrue(OutlineVisibility.enabled(disabled, "minecraft:trial_chambers"));
        assertFalse(OutlineVisibility.visible(disabled, false, "egox:mob_spawner"));
        assertTrue(OutlineVisibility.visible(disabled, false, "egox:end_pillar"));
        assertTrue(OutlineVisibility.visible(disabled, false, "minecraft:trial_chambers"));
        String saved = new com.google.gson.Gson().toJson(disabled);
        Set<String> restored = new com.google.gson.Gson().fromJson(saved, new com.google.gson.reflect.TypeToken<Set<String>>() {}.getType());
        assertEquals(disabled, restored);
    }
    @Test void outlineMigrationPreservesExistingHudRangeAndLaterOutlineChoices() {
        var json = com.google.gson.JsonParser.parseString("{\"displayRevision\":1,\"hud\":true,\"outlineRange\":128,\"outlines\":false}").getAsJsonObject();
        assertTrue(ConfigMigration.update(json));
        assertTrue(json.get("hud").getAsBoolean()); assertEquals(128, json.get("outlineRange").getAsInt());
        assertFalse(json.get("outlines").getAsBoolean());
        assertEquals("egox:geode", json.getAsJsonArray("disabledOutlineTypes").get(0).getAsString());
        json.getAsJsonArray("disabledOutlineTypes").remove(0);
        assertFalse(ConfigMigration.update(json));
        assertTrue(json.getAsJsonArray("disabledOutlineTypes").isEmpty());
    }
    @Test void hiddenGeodesCannotConsumeTheVisibleMarkerLimitOrDeleteObservations() {
        var markers = new ArrayList<FinderMarkers.Marker>();
        String dimension = "minecraft:overworld";
        for (int i = 0; i < 600; i++) markers.add(new FinderMarkers.Marker("egox:geode", 0, 0, dimension, new net.minecraft.world.phys.AABB(0,0,0,1,1,1), "Geode", null));
        var shipwreck = new FinderMarkers.Marker("minecraft:shipwreck", 10, 0, dimension, new net.minecraft.world.phys.AABB(160,0,0,180,15,30), "Wrack", null);
        markers.add(shipwreck);
        var visible = FinderMarkers.selectVisible(markers, dimension, net.minecraft.world.phys.Vec3.ZERO, 512, OutlineVisibility.defaults(), true);
        assertEquals(List.of(shipwreck), visible);
        assertEquals(601, markers.size(), "Filtering must not remove observations");
        assertEquals(512, FinderMarkers.selectVisible(markers, dimension, net.minecraft.world.phys.Vec3.ZERO, 512, Set.of(), true).size());
        var disabled = OutlineVisibility.defaults(); disabled.add("minecraft:shipwreck");
        assertTrue(FinderMarkers.selectVisible(markers, dimension, net.minecraft.world.phys.Vec3.ZERO, 512, disabled, true).isEmpty());
        assertEquals(List.of(shipwreck), FinderMarkers.selectVisible(markers, dimension, net.minecraft.world.phys.Vec3.ZERO, 512, Set.of(), false));
    }
    @Test void outlinePaletteIsUniqueAcrossAllKnownTypesAndSpecialBlocks() {
        var types = new TreeSet<>(NativeProfiles.all().keySet());
        types.addAll(Set.of("egox:mob_spawner","egox:trial_spawner","egox:vault","egox:geode","egox:end_pillar"));
        OutlineColors.register(types);
        var colors = new HashSet<Integer>();
        for (String type : types) { int color = OutlineColors.color(type); assertTrue(colors.add(color),type); assertEquals(color,OutlineColors.color(type)); assertEquals(255,color >>> 24); }
    }
    @Test void specialMarkersPreserveMultipleSpawnerPositionsInTheSameChunk() {
        FinderMarkers.clear();
        try {
            var first = new BlockPos(-1,20,-1); var second = new BlockPos(-1,30,-1);
            FinderMarkers.addBlock("egox:mob_spawner",first,"A"); FinderMarkers.addBlock("egox:mob_spawner",second,"B");
            assertEquals(2,FinderMarkers.observations().size());
            assertEquals(Set.of(first,second),FinderMarkers.observations().stream().map(FinderMarkers.Marker::blockPos).collect(java.util.stream.Collectors.toSet()));
            assertEquals("egox:mob_spawner",SpecialBlockFinder.type(Blocks.SPAWNER));
            assertEquals("egox:trial_spawner",SpecialBlockFinder.type(Blocks.TRIAL_SPAWNER));
            assertEquals("egox:vault",SpecialBlockFinder.type(Blocks.VAULT));
            assertEquals("egox:geode",SpecialBlockFinder.type(Blocks.BUDDING_AMETHYST));
            assertEquals("",SpecialBlockFinder.type(Blocks.COBBLESTONE));
        } finally { FinderMarkers.clear(); }
    }
    @Test void effectiveStructureSeedReproducesNativeChangedSaltPlacements() {
        long seed = -5837105212680014280L; int salt = -123456789;
        var p = NativeProfiles.get("minecraft:trial_chambers");
        var placement = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(p.spacing(),p.separation(),net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR,salt);
        var vanilla = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(p.spacing(),p.separation(),net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR,p.vanillaSalt());
        var samples = new ArrayList<StructureObservation>();
        for (int region : new int[]{-100,-5,0,9,100,300}) {
            var chunk = placement.getPotentialStructureChunk(seed,region*p.spacing(),(region-7)*p.spacing());
            samples.add(new StructureObservation(p,Platform.chunkX(chunk),Platform.chunkZ(chunk),"minecraft:overworld"));
        }
        var info = StructureSeedInfo.of(p,seed,(long)salt,samples);
        assertEquals(samples.size(),info.matches()); assertNotNull(info.vanillaEquivalent48());
        assertNotEquals(seed & LegacyRandom.MASK,info.vanillaEquivalent48().longValue());
        for (var sample : samples) {
            var chunk = vanilla.getPotentialStructureChunk(info.vanillaEquivalent48(),sample.chunkX(),sample.chunkZ());
            assertEquals(sample.chunkX(),Platform.chunkX(chunk)); assertEquals(sample.chunkZ(),Platform.chunkZ(chunk));
        }
        assertNull(StructureSeedInfo.of(p,seed,null,samples).vanillaEquivalent48(),"An unknown changed salt must not be presented as a solved per-structure seed");
        assertNull(StructureSeedInfo.of(p,null,(long)salt,samples).vanillaEquivalent48());
    }
    @Test void nativeCatalogIncludesMoreThanTheOriginalThreeDetectors() {
        var catalog = NativeTemplateFinder.variants();
        var types = new TreeSet<String>(); catalog.forEach(v -> types.add(v.type()));
        System.out.println("Template finder types: " + types + "; " + catalog.size() + " rotated variants");
        System.out.println("Unavailable templates: " + NativeTemplateFinder.failures());
        assertTrue(types.containsAll(Set.of("minecraft:igloo", "minecraft:shipwreck", "minecraft:end_city", "minecraft:trial_chambers", "minecraft:village_plains", "minecraft:bastion_remnant", "minecraft:ancient_city", "minecraft:pillager_outpost")), types.toString());
        assertTrue(catalog.stream().allMatch(v -> v.marks().size() >= 16));
    }
    @Test void shipwreckCatalogCoversEveryNativeOceanAndBeachedTemplate() throws Exception {
        var expected = new HashSet<String>();
        for (var field : net.minecraft.world.level.levelgen.structure.structures.ShipwreckPieces.class.getDeclaredFields()) {
            if (!field.getType().isArray() || field.getType().getComponentType().isPrimitive()) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Object[] array) for (Object id : array) {
                String name = id.toString();
                if (name.startsWith("minecraft:shipwreck/")) expected.add(name.substring("minecraft:".length()));
            }
        }
        assertEquals(20, expected.size(), "Native ocean/beached template inventory changed");
        var variants = NativeTemplateFinder.variants().stream().filter(v -> v.type().equals("minecraft:shipwreck")).toList();
        assertEquals(expected, variants.stream().map(NativeTemplateFinder.Variant::template).collect(java.util.stream.Collectors.toSet()));
        for (String template : expected) assertEquals(Set.of(Rotation.values()), variants.stream().filter(v -> v.template().equals(template)).map(NativeTemplateFinder.Variant::rotation).collect(java.util.stream.Collectors.toSet()), template);
    }
    @Test void detectorsMatchNativeRotatedTemplatesAtNegativeCoordinatesAndRejectDamage() throws Exception {
        var tested = new HashSet<String>();
        for (var variant : NativeTemplateFinder.variants()) {
            // Every template, wood palette, rotation and joint-derived start offset.
            String key = variant.type() + ':' + variant.template() + ':' + variant.palette() + ':' + variant.rotation() + ':' + variant.startOffset();
            if (!tested.add(key)) continue;
            try (var input = NativeTemplateFinder.class.getResourceAsStream("/data/minecraft/structure/" + variant.template() + ".nbt")) {
                var tag = NbtIo.readCompressed(input, NbtAccounter.create(64_000_000));
                var palettes = tag.getListOrEmpty("palettes");
                if (!palettes.isEmpty()) { tag.put("palette", palettes.getListOrEmpty(variant.palette())); tag.remove("palettes"); }
                var nativeTemplate = new StructureTemplate(); nativeTemplate.load(BuiltInRegistries.BLOCK, tag);
                BlockPos pivot = variant.type().equals("minecraft:igloo") ? new BlockPos(3, 5, 5) : variant.type().equals("minecraft:shipwreck") ? new BlockPos(4, 0, 15) : BlockPos.ZERO;
                var settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(variant.rotation()).setRotationPivot(pivot);
                BlockPos start = new BlockPos(-160, 64, -304), origin = start.subtract(variant.startOffset());
                var blocks = new HashMap<BlockPos, Block>();
                for (var block : variant.marks().stream().map(NativeTemplateFinder.Mark::block).distinct().toList())
                    nativeTemplate.filterBlocks(origin, settings, block).forEach(info -> blocks.put(info.pos(), info.state().getBlock()));
                BlockPos anchor = origin.offset(variant.anchor().pos());
                var hit = variant.detect(anchor, p -> blocks.getOrDefault(p, Blocks.AIR)).orElseThrow(() -> new AssertionError(key));
                assertEquals(-10, hit.chunkX(), key); assertEquals(-19, hit.chunkZ(), key);
                if (variant.type().equals("minecraft:shipwreck")) assertTrue(NativeTemplateFinder.detect(anchor, blocks.get(anchor), variant.dimension(), p -> blocks.getOrDefault(p, Blocks.AIR)).stream()
                    .anyMatch(h -> h.type().equals("minecraft:shipwreck") && h.chunkX() == -10 && h.chunkZ() == -19), "Indexed shipwreck detection: " + key);
                assertTrue(hit.box().contains(anchor.getX() + .5, anchor.getY() + .5, anchor.getZ() + .5), key);
                blocks.remove(origin.offset(variant.marks().getLast().pos()));
                assertTrue(variant.detect(anchor, p -> blocks.getOrDefault(p, Blocks.AIR)).isEmpty(), key);
            }
        }
        assertTrue(tested.size() >= 28);
    }
}
