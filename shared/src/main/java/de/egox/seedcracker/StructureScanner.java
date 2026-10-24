package de.egox.seedcracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Resumable main-thread scans of loaded sections, including neighbours of newly received chunks. */
public final class StructureScanner {
    private static final LinkedHashSet<ChunkPos> QUEUE = new LinkedHashSet<>();
    private static final Set<String> FOUND = new HashSet<>();
    private static final LinkedHashMap<ChunkPos, Set<ChunkPos>> WAITING = new LinkedHashMap<>();
    private static Cursor cursor;
    private static String dimension = "";
    private static class Cursor { ChunkPos pos; int section, block; Cursor(ChunkPos pos) { this.pos = pos; } }
    public static void enqueue(ChunkPos pos) {
        var dependent = WAITING.remove(pos);
        if (dependent != null) QUEUE.addAll(dependent);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) QUEUE.add(new ChunkPos(Platform.chunkX(pos) + x, Platform.chunkZ(pos) + z));
        while (QUEUE.size() > 4096) QUEUE.remove(QUEUE.iterator().next());
    }
    public static int pending() { return QUEUE.size() + (cursor == null ? 0 : 1); }
    public static void clear() { QUEUE.clear(); FOUND.clear(); WAITING.clear(); cursor = null; dimension = ""; }
    public static int rescan() {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return 0;
        int radius = Math.min(32, client.options.getEffectiveRenderDistance());
        int cx = Math.floorDiv(client.player.getBlockX(), 16), cz = Math.floorDiv(client.player.getBlockZ(), 16), count = 0;
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) if (client.level.hasChunk(x, z)) { QUEUE.add(new ChunkPos(x, z)); count++; }
        return count;
    }
    public static List<String> describe() {
        var automatic = new TreeSet<String>();
        StructureSignatures.SIGNATURES.forEach(s -> automatic.add(s.type()));
        automatic.add("minecraft:monument");
        NativeTemplateFinder.variants().forEach(v -> automatic.add(v.type()));
        var result = new ArrayList<String>();
        result.add("AUTO: " + String.join(", ", automatic));
        result.add("MARKIERUNGEN: Spawner, Trial-Spawner, Vaults und Geoden. Intakte Overworld-Dungeon-Boeden liefern separate Muster-Bits; beliebige Spawner nicht.");
        var manual = new TreeSet<>(NativeProfiles.all().keySet()); manual.removeAll(automatic);
        manual.removeIf(id -> NativeProfiles.all().get(id).id().equals(id));
        result.add("MANUELL (exakter Start): " + String.join(", ", manual));
        result.add("Nicht alle Strukturen verwenden Random-Spread: z.B. Strongholds und Mineshafts benoetigen eigene Solver.");
        var failures = NativeTemplateFinder.failures();
        if (!failures.isEmpty()) result.add(failures.size() + " Vorlagen-Hinweise: Details mit /egox db export.");
        return result;
    }
    public static void tick(Minecraft client) {
        if (client.level == null || !EgoXConfig.get().finder || EgoXSession.context().server().isEmpty()) return;
        String current = EgoXSession.context().dimension();
        if (!dimension.equals(current)) { cursor = null; FOUND.clear(); dimension = current; rescan(); }
        if (!NativeTemplateFinder.ready()) { NativeTemplateFinder.warmup(); return; }
        long until = System.nanoTime() + 2_000_000L;
        while (System.nanoTime() < until) {
            if (cursor == null) {
                if (QUEUE.isEmpty()) return;
                ChunkPos next = QUEUE.iterator().next(); QUEUE.remove(next); cursor = new Cursor(next);
            }
            int cx = Platform.chunkX(cursor.pos), cz = Platform.chunkZ(cursor.pos);
            if (!client.level.hasChunk(cx, cz)) { cursor = null; continue; }
            var chunk = client.level.getChunk(cx, cz); var sections = chunk.getSections();
            if (cursor.section == 0 && cursor.block == 0) SpecialBlockFinder.scanChunk(chunk);
            if (cursor.section >= sections.length) { cursor = null; continue; }
            var section = sections[cursor.section];
            if (cursor.block == 0 && !section.maybeHas(s -> legacyAnchor(s.getBlock()) || NativeTemplateFinder.anchor(s.getBlock()))) { cursor.section++; continue; }
            int baseY = chunk.getMinY() + cursor.section * 16;
            for (int batch = 0; batch < 64 && cursor.block < 4096; batch++, cursor.block++) {
                int x = cursor.block & 15, z = (cursor.block >>> 4) & 15, y = cursor.block >>> 8;
                Block block = section.getBlockState(x, y, z).getBlock();
                if (!legacyAnchor(block) && !NativeTemplateFinder.anchor(block)) continue;
                var anchor = new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z);
                SpecialBlockFinder.found(anchor, block);
                java.util.function.Function<BlockPos, Block> view = point -> {
                    if (client.level.hasChunkAt(point)) return client.level.getBlockState(point).getBlock();
                    ChunkPos missing = new ChunkPos(Math.floorDiv(point.getX(), 16), Math.floorDiv(point.getZ(), 16));
                    WAITING.computeIfAbsent(missing, k -> new HashSet<>()).add(new ChunkPos(cx, cz));
                    while (WAITING.size() > 4096) WAITING.remove(WAITING.keySet().iterator().next());
                    return Blocks.AIR;
                };
                if (current.equals("minecraft:overworld") && legacyAnchor(block)) StructureSignatures.detectHit(anchor, block, view).ifPresent(hit ->
                    found(hit.start().type(), hit.start().chunkX(), hit.start().chunkZ(), hit.box(), "Blocksignatur"));
                if (current.equals("minecraft:overworld") && block == Blocks.SEA_LANTERN) MonumentFinder.detect(anchor, view).ifPresent(hit -> found(hit.type(), hit.chunkX(), hit.chunkZ(), hit.box(), hit.template()));
                for (var hit : NativeTemplateFinder.detect(anchor, block, current, view)) found(hit.type(), hit.chunkX(), hit.chunkZ(), hit.box(), hit.template());
            }
            if (cursor.block >= 4096) { cursor.block = 0; cursor.section++; }
        }
    }
    private static boolean legacyAnchor(Block block) { return block == Blocks.TNT || block == Blocks.CAULDRON || block == Blocks.DISPENSER || block == Blocks.SEA_LANTERN || !SpecialBlockFinder.type(block).isEmpty(); }
    private static void found(String type, int x, int z, net.minecraft.world.phys.AABB box, String source) {
        var p = NativeProfiles.get(type); int bound = p.spacing() - p.separation();
        String key = type + ':' + x + ':' + z;
        if (FOUND.contains(key)) return;
        boolean nominal = Math.floorMod(x, p.spacing()) < bound && Math.floorMod(z, p.spacing()) < bound;
        SessionLibrary.event("STRUCTURE_DETECTION", Map.of("type",type,"chunkX",x,"chunkZ",z,"dimension",EgoXSession.context().dimension(),"source",source,"nominalPlacement",p,"insideNominalRegion",nominal));
        FOUND.add(key); FinderMarkers.add(type, x, z, box, source);
        if (!nominal) { EgoXSession.say("Erkannt: " + type + " | Chunk " + x + " " + z + " | ausserhalb Vanilla-Regionregel; als Rohdaten gespeichert."); return; }
        if (EgoXSession.addStructure(type, x, z)) EgoXSession.say("Erkannt: " + type.replace("minecraft:", "") + " | Chunk " + x + " " + z);
    }
}
