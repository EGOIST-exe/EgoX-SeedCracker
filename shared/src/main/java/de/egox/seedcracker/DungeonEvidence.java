package de.egox.seedcracker;

import de.egox.seedcracker.core.DungeonObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Only a complete plausible monster-room floor is admitted; arbitrary spawners remain markers. */
public final class DungeonEvidence {
    public record Summary(int rooms, double patternBits) {
        public String compact() { return String.format(Locale.ROOT, "%d Dungeons | ~%.1f Muster-Bits", rooms, patternBits); }
        public String describe() { return compact() + " (theoretisch; Welt-Seed-Kandidatenfilter)"; }
    }
    private static final Map<String, DungeonObservation> ROOMS = new LinkedHashMap<>();
    private DungeonEvidence() { }
    public static Optional<DungeonObservation> detect(BlockPos origin, Function<BlockPos, Block> blocks) {
        if (blocks.apply(origin) != Blocks.SPAWNER) return Optional.empty();
        var layouts = new ArrayList<DungeonObservation.Layout>();
        for (int rx = 2; rx <= 3; rx++) for (int rz = 2; rz <= 3; rz++) {
            var floor = new ArrayList<Integer>(); boolean valid = true; int openings = 0;
            for (int dx = -rx - 1; dx <= rx + 1 && valid; dx++) for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                var pos = origin.offset(dx, -1, dz); var tile = blocks.apply(pos);
                if (tile != Blocks.COBBLESTONE && tile != Blocks.MOSSY_COBBLESTONE) { valid = false; break; }
                var support = blocks.apply(pos.below());
                if (support == null || !support.defaultBlockState().isSolid()) { valid = false; break; }
                floor.add(tile == Blocks.COBBLESTONE ? 0 : 1);
                if (Math.abs(dx) == rx + 1 || Math.abs(dz) == rz + 1) {
                    var lower = blocks.apply(origin.offset(dx, 0, dz)); var upper = blocks.apply(origin.offset(dx, 1, dz));
                    if (lower == null || upper == null) { valid = false; break; }
                    if (lower.defaultBlockState().isAir() && upper.defaultBlockState().isAir()) openings++;
                    else if (lower != Blocks.COBBLESTONE || upper != Blocks.COBBLESTONE) { valid = false; break; }
                } else if (dx != 0 || dz != 0) {
                    var inside = blocks.apply(origin.offset(dx, 0, dz));
                    if (inside == null || !inside.defaultBlockState().isAir() && inside != Blocks.CHEST) { valid = false; break; }
                }
            }
            if (valid && openings >= 1 && openings <= 5) layouts.add(new DungeonObservation.Layout(rx, rz, floor));
        }
        return layouts.isEmpty() ? Optional.empty() : Optional.of(new DungeonObservation(origin.getX(), origin.getY(), origin.getZ(), layouts, Instant.now().toString()));
    }
    public static void observe(BlockPos pos) {
        if (!EgoXConfig.get().dungeonChecker) return;
        if (!EgoXSession.context().dimension().equals("minecraft:overworld")) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        detect(pos, p -> level.hasChunkAt(p) ? level.getBlockState(p).getBlock() : null).ifPresent(DungeonEvidence::add);
    }
    static synchronized boolean add(DungeonObservation room) {
        var old = ROOMS.put(room.key(), room);
        while (ROOMS.size() > 1024) ROOMS.remove(ROOMS.keySet().iterator().next());
        if (old != null && old.layouts().equals(room.layouts())) return false;
        SessionLibrary.event("DUNGEON", room);
        return true;
    }
    public static synchronized List<DungeonObservation> snapshot() { return List.copyOf(ROOMS.values()); }
    public static synchronized void clear() { ROOMS.clear(); }
    public static synchronized void restore(List<DungeonObservation> rooms) {
        if (rooms != null) rooms.forEach(r -> ROOMS.putIfAbsent(r.key(), r));
        while (ROOMS.size() > 1024) ROOMS.remove(ROOMS.keySet().iterator().next());
    }
    public static Summary summarize(Collection<DungeonObservation> rooms) {
        var chunks = new HashMap<String, Double>();
        rooms.forEach(r -> chunks.merge(r.chunkX() + ":" + r.chunkZ(), r.patternBits(), Math::max));
        return new Summary(rooms.size(), chunks.values().stream().mapToDouble(Double::doubleValue).sum());
    }
    public static String status() { return summarize(snapshot()).describe(); }
}
