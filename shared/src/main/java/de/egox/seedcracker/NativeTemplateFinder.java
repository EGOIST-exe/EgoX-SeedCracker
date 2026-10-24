package de.egox.seedcracker;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Reads vanilla start templates at runtime. No template or third-party finder is redistributed. */
public final class NativeTemplateFinder {
    public record Mark(BlockPos pos, Block block) { }
    public record Variant(String type, String template, String dimension, Rotation rotation, int palette, List<Mark> marks,
                          BlockPos startOffset, AABB localBox) {
        public Mark anchor() { return marks.getFirst(); }
        public Optional<Hit> detect(BlockPos anchorPos, Function<BlockPos, Block> view) {
            BlockPos origin = anchorPos.subtract(anchor().pos());
            BlockPos start = origin.offset(startOffset);
            if ((start.getX() & 15) != 0 || (start.getZ() & 15) != 0) return Optional.empty();
            for (Mark m : marks) if (view.apply(origin.offset(m.pos())) != m.block()) return Optional.empty();
            return Optional.of(new Hit(type, Math.floorDiv(start.getX(), 16), Math.floorDiv(start.getZ(), 16),
                localBox.move(origin.getX(), origin.getY(), origin.getZ()), template));
        }
    }
    public record Hit(String type, int chunkX, int chunkZ, AABB box, String template) { }
    private record AnchorKey(Block block, int x, int z) { }
    private static final Set<String> UNSTABLE = Set.of("air", "cave_air", "void_air", "structure_block", "structure_void", "jigsaw", "water", "lava",
        "snow", "short_grass", "tall_grass", "vine", "glow_lichen", "fire", "soul_fire", "sculk_vein", "cobweb");
    private static final Map<String, String> failures = new TreeMap<>();
    private static volatile List<Variant> variants;
    private static volatile String preparation = "Finder-Vorlagen werden vorbereitet";
    private static final java.util.concurrent.ExecutorService PREPARE = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "EgoX-templates"); t.setDaemon(true); return t; });
    private static final java.util.concurrent.atomic.AtomicBoolean requested = new java.util.concurrent.atomic.AtomicBoolean();
    private static final Map<AnchorKey, List<Variant>> INDEX = new HashMap<>();
    private static final Set<Block> ANCHORS = new HashSet<>();
    private NativeTemplateFinder() { }
    public static void warmup() {
        if (variants != null || !requested.compareAndSet(false, true)) return;
        PREPARE.execute(() -> {
            try { variants(); preparation = "Finder-Vorlagen bereit"; }
            catch (Throwable failure) { preparation = "Finder-Vorlagen fehlgeschlagen: " + failure.getClass().getSimpleName(); org.slf4j.LoggerFactory.getLogger("EgoX finder").error(preparation, failure); }
        });
    }
    public static boolean ready() { return variants != null; }
    public static String preparation() { return preparation; }
    public static synchronized List<Variant> variants() {
        if (variants != null) return variants;
        var result = new ArrayList<Variant>();
        add(result, "minecraft:igloo", "igloo/top", "minecraft:overworld", new BlockPos(3, 5, 5), BlockPos.ZERO, null, Set.of());
        for (String name : List.of("with_mast", "upsidedown_full", "upsidedown_fronthalf", "upsidedown_backhalf", "sideways_full", "sideways_fronthalf", "sideways_backhalf", "rightsideup_full", "rightsideup_fronthalf", "rightsideup_backhalf"))
            for (String suffix : List.of("", "_degraded")) add(result, "minecraft:shipwreck", "shipwreck/" + name + suffix, "minecraft:overworld", new BlockPos(4, 0, 15), BlockPos.ZERO, null, Set.of());
        add(result, "minecraft:end_city", "end_city/base_floor", "minecraft:the_end", BlockPos.ZERO, new BlockPos(-7, 0, -7), null, Set.of());
        addOutpost(result);
        for (String type : NativeProfiles.all().keySet()) {
            JsonObject structure = json("worldgen/structure/" + path(type) + ".json");
            if (structure == null || !text(structure, "type").equals("minecraft:jigsaw")) continue;
            String dimension = type.contains("bastion") ? "minecraft:the_nether" : "minecraft:overworld";
            JsonObject pool = json("worldgen/template_pool/" + path(text(structure, "start_pool")) + ".json");
            if (pool == null) { failures.put(type, "Start-Pool fehlt"); continue; }
            int before = result.size();
            for (JsonElement element : pool.getAsJsonArray("elements")) {
                readElement(result, type, element.getAsJsonObject().getAsJsonObject("element"), dimension, text(structure, "start_jigsaw_name"));
            }
            if (result.size() == before && result.stream().noneMatch(v -> v.type().equals(type))) failures.put(type, "Keine ausreichend stabile Startvorlage");
        }
        for (Variant variant : result) {
            var anchor = variant.anchor();
            var key = new AnchorKey(anchor.block(), Math.floorMod(anchor.pos().getX() - variant.startOffset().getX(), 16), Math.floorMod(anchor.pos().getZ() - variant.startOffset().getZ(), 16));
            INDEX.computeIfAbsent(key, k -> new ArrayList<>()).add(variant); ANCHORS.add(anchor.block());
        }
        OutlineColors.register(NativeProfiles.all().keySet());
        variants = List.copyOf(result); return variants;
    }
    private record Joint(BlockPos pos, net.minecraft.core.Direction front, net.minecraft.core.Direction top, String name, String target, String pool) { }
    private static List<Joint> joints(String template) throws IOException {
        try (var input = NativeTemplateFinder.class.getResourceAsStream("/data/minecraft/structure/" + template + ".nbt")) {
            if (input == null) return List.of();
            var tag = NbtIo.readCompressed(input, NbtAccounter.create(64_000_000));
            var palette = tag.getListOrEmpty("palette"); var result = new ArrayList<Joint>();
            for (var block : tag.getListOrEmpty("blocks").compoundStream().toList()) {
                var state = palette.getCompoundOrEmpty(block.getIntOr("state", -1));
                if (!state.getStringOr("id", state.getStringOr("Name", "")).equals("minecraft:jigsaw")) continue;
                var properties = state.getCompoundOrEmpty("properties");
                if (properties.isEmpty()) properties = state.getCompoundOrEmpty("Properties");
                String[] axes = properties.getStringOr("orientation", "north_up").split("_");
                var pos = block.getListOrEmpty("pos"); var nbt = block.getCompoundOrEmpty("nbt");
                result.add(new Joint(new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)),
                    net.minecraft.core.Direction.valueOf(axes[0].toUpperCase(Locale.ROOT)), net.minecraft.core.Direction.valueOf(axes[1].toUpperCase(Locale.ROOT)),
                    nbt.getStringOr("name", ""), nbt.getStringOr("target", ""), nbt.getStringOr("pool", "")));
            }
            return result;
        }
    }
    /** Outpost towers are children of an empty base plate; align the actual named joints. */
    private static void addOutpost(List<Variant> result) {
        try {
            var parents = joints("pillager_outpost/base_plate"); var children = joints("pillager_outpost/watchtower");
            for (Joint parent : parents) {
                if (!parent.pool().equals("minecraft:pillager_outpost/towers")) continue;
                var patterns = new ArrayList<Variant>();
                add(patterns, "minecraft:pillager_outpost", "pillager_outpost/watchtower", "minecraft:overworld", BlockPos.ZERO, BlockPos.ZERO, parent.target(), Set.of());
                for (var variant : patterns) for (Rotation parentRotation : Rotation.values()) for (Joint child : children) {
                    if (!child.name().equals(parent.target())) continue;
                    var front = parentRotation.rotate(parent.front());
                    if (variant.rotation().rotate(child.front()) != front.getOpposite() || variant.rotation().rotate(child.top()) != parentRotation.rotate(parent.top())) continue;
                    var parentPoint = StructureTemplate.transform(parent.pos(), Mirror.NONE, parentRotation, BlockPos.ZERO).relative(front);
                    var offset = variant.startOffset().subtract(parentPoint);
                    result.add(new Variant(variant.type(), variant.template(), variant.dimension(), variant.rotation(), variant.palette(), variant.marks(), offset, variant.localBox()));
                }
            }
        } catch (IOException | RuntimeException error) { failures.put("minecraft:pillager_outpost", error.getClass().getSimpleName() + ": " + error.getMessage()); }
    }
    private static void readElement(List<Variant> result, String type, JsonObject element, String dimension, String namedAnchor) {
        if (element == null) return;
        if (text(element, "element_type").equals("minecraft:list_pool_element")) {
            // Only the first layer: subsequent layers may overwrite the first and are not independent starts.
            var elements = element.getAsJsonArray("elements");
            if (!elements.isEmpty()) readElement(result, type, elements.get(0).getAsJsonObject(), dimension, namedAnchor);
        } else if (element.has("location") && !text(element, "projection").equals("terrain_matching")) {
            var excluded = new HashSet<String>();
            JsonElement processors = element.get("processors");
            if (processors != null) {
                JsonElement definition = processors.isJsonPrimitive() ? json("worldgen/processor_list/" + path(processors.getAsString()) + ".json") : processors;
                excludeRuleInputs(definition, excluded);
            }
            add(result, type, path(text(element, "location")), dimension, BlockPos.ZERO, BlockPos.ZERO, namedAnchor.isEmpty() ? null : namedAnchor, excluded);
        }
    }
    private static void excludeRuleInputs(JsonElement value, Set<String> excluded) {
        if (value == null) return;
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(e -> excludeRuleInputs(e, excluded)); return; }
        if (!value.isJsonObject()) return;
        JsonObject object = value.getAsJsonObject();
        if (object.has("input_predicate")) collectBlockNames(object.get("input_predicate"), excluded);
        object.entrySet().forEach(e -> excludeRuleInputs(e.getValue(), excluded));
    }
    private static void collectBlockNames(JsonElement value, Set<String> names) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(e -> collectBlockNames(e, names));
        if (!value.isJsonObject()) return;
        value.getAsJsonObject().entrySet().forEach(e -> {
            if (e.getKey().equals("block") || e.getKey().equals("Name")) names.add(e.getValue().getAsString());
            else collectBlockNames(e.getValue(), names);
        });
    }
    private static void add(List<Variant> result, String type, String template, String dimension, BlockPos pivot, BlockPos baseStart, String namedAnchor, Set<String> excluded) {
        try (InputStream input = NativeTemplateFinder.class.getResourceAsStream("/data/minecraft/structure/" + template + ".nbt")) {
            if (input == null) { failures.put(type + "/" + template, "Vorlage fehlt"); return; }
            CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.create(64_000_000));
            ListTag size = tag.getListOrEmpty("size");
            int sx = size.getIntOr(0, 0), sy = size.getIntOr(1, 0), sz = size.getIntOr(2, 0);
            var palettes = new ArrayList<ListTag>();
            ListTag multi = tag.getListOrEmpty("palettes");
            if (!multi.isEmpty()) for (int i = 0; i < multi.size(); i++) palettes.add(multi.getListOrEmpty(i));
            else palettes.add(tag.getListOrEmpty("palette"));
            for (int paletteIndex = 0; paletteIndex < palettes.size(); paletteIndex++) {
            var stable = new ArrayList<Mark>(); var starts = new ArrayList<BlockPos>();
            for (var entry : tag.getListOrEmpty("blocks").compoundStream().toList()) {
                int index = entry.getIntOr("state", -1);
                if (index < 0 || index >= palettes.getFirst().size()) continue;
                var state = palettes.get(paletteIndex).getCompoundOrEmpty(index);
                String name = state.getStringOr("id", state.getStringOr("Name", ""));
                ListTag coordinates = entry.getListOrEmpty("pos");
                var pos = new BlockPos(coordinates.getIntOr(0, 0), coordinates.getIntOr(1, 0), coordinates.getIntOr(2, 0));
                if (name.equals("minecraft:jigsaw") && namedAnchor != null && entry.getCompoundOrEmpty("nbt").getStringOr("name", "").equals(namedAnchor)) starts.add(pos);
                if (excluded.contains(name) || UNSTABLE.contains(path(name)) || name.endsWith("_leaves") || name.endsWith("_sapling") || name.contains("copper") || name.contains("candle")) continue;
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(name));
                if (block == null || block == Blocks.AIR || block.defaultBlockState().canBeReplaced()) continue;
                // Igloo trapdoor may be filled with snow; End-city upper layers replace the top edge.
                if (template.equals("igloo/top") && pos.equals(new BlockPos(3, 0, 5))) continue;
                if (template.equals("end_city/base_floor") && pos.getY() >= 4) continue;
                stable.add(new Mark(pos, block));
            }
            if (stable.size() < 16 || (namedAnchor != null && starts.isEmpty())) { failures.put(type + "/" + template, "Zu wenige stabile Marker oder Startanker fehlt"); continue; }
            List<Mark> selected = select(stable, 48);
            if (namedAnchor == null) starts.add(baseStart);
            for (Rotation rotation : Rotation.values()) {
                var marks = selected.stream().map(m -> new Mark(StructureTemplate.transform(m.pos(), Mirror.NONE, rotation, pivot), m.block())).toList();
                BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotation, pivot);
                BlockPos b = StructureTemplate.transform(new BlockPos(sx - 1, sy - 1, sz - 1), Mirror.NONE, rotation, pivot);
                AABB box = new AABB(Math.min(a.getX(), b.getX()), 0, Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()) + 1, sy, Math.max(a.getZ(), b.getZ()) + 1);
                for (BlockPos start : starts) result.add(new Variant(type, template, dimension, rotation, paletteIndex, marks,
                    namedAnchor == null ? baseStart : StructureTemplate.transform(start, Mirror.NONE, rotation, BlockPos.ZERO), box));
            }
            }
        } catch (IOException | RuntimeException e) { failures.put(type + "/" + template, e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }
    private static List<Mark> select(List<Mark> all, int count) {
        var frequencies = new HashMap<Block, Integer>(); all.forEach(m -> frequencies.merge(m.block(), 1, Integer::sum));
        all.sort(Comparator.comparingInt(m -> frequencies.get(m.block())));
        var selected = new ArrayList<Mark>(); selected.add(all.removeFirst());
        while (!all.isEmpty() && selected.size() < count) {
            Mark best = null; double score = -1;
            for (Mark m : all) {
                double distance = selected.stream().mapToDouble(s -> s.pos().distSqr(m.pos())).min().orElse(0);
                double candidate = distance + 8.0 / frequencies.get(m.block());
                if (candidate > score) { score = candidate; best = m; }
            }
            selected.add(best); all.remove(best);
        }
        return List.copyOf(selected);
    }
    public static boolean anchor(Block block) { return ready() && ANCHORS.contains(block); }
    public static List<Hit> detect(BlockPos anchor, Block block, String dimension, Function<BlockPos, Block> view) {
        if (!ready()) return List.of();
        var candidates = INDEX.getOrDefault(new AnchorKey(block, anchor.getX() & 15, anchor.getZ() & 15), List.of());
        var hits = new ArrayList<Hit>();
        for (Variant variant : candidates) if (variant.dimension().equals(dimension)) variant.detect(anchor, view).ifPresent(hits::add);
        return hits;
    }
    public static Map<String, String> failures() { variants(); return Map.copyOf(failures); }
    private static JsonObject json(String resource) {
        try (InputStream input = NativeTemplateFinder.class.getResourceAsStream("/data/minecraft/" + resource)) {
            return input == null ? null : JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) { return null; }
    }
    private static String text(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : ""; }
    private static String path(String identifier) { return identifier.replace("minecraft:", ""); }
}
