package de.egox.seedcracker;

import de.egox.seedcracker.core.EndPillars;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.concurrent.*;

/** Samples intact central pillars, including their bedrock tops and obsidian cross sections. */
public final class EndPillarFinder {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "EgoX-pillars"); t.setDaemon(true); return t; });
    private static int[] heights = empty();
    private static volatile List<Integer> candidates = List.of();
    private static long generation;
    private static int ticks;
    private EndPillarFinder() { }
    private static int[] empty() { int[] values = new int[10]; Arrays.fill(values, -1); return values; }
    public static void clear() { generation++; heights = empty(); candidates = List.of(); }
    public static List<Integer> candidates() { return candidates; }
    public static int[] heights() { return heights.clone(); }
    public static void restore(int[] saved) {
        if (saved == null || saved.length != 10) return;
        for (int i = 0; i < 10; i++) if (heights[i] < 0 && saved[i] >= 76 && saved[i] <= 103 && (saved[i] - 76) % 3 == 0) heights[i] = saved[i];
        if (count() < 6) return;
        int[] samples = heights.clone(); long token = ++generation;
        WORKER.execute(() -> { var result = EndPillars.candidates(samples); Minecraft.getInstance().execute(() -> { if (token == generation) candidates = result; }); });
    }
    public static int count() { int count = 0; for (int height : heights) if (height >= 0) count++; return count; }
    public static double bits() { int size = candidates.size(); return size == 0 ? 0 : Math.max(0, 16 - Math.log(size) / Math.log(2)); }
    public static String status() { return String.format(Locale.ROOT, "End-Saeulen: %d/10 | %d Kandidaten | ~%.1f/16 Bits", count(), candidates.size(), bits()); }
    public static void tick(Minecraft client) {
        if (++ticks % 20 != 0 || !EgoXConfig.get().finder || client.level == null || !client.level.dimension().equals(Level.END)) return;
        boolean changed = false;
        for (int i = 0; i < 10; i++) {
            int x = (int)Math.floor(42 * Math.cos(2 * (-Math.PI + Math.PI / 10 * i)));
            int z = (int)Math.floor(42 * Math.sin(2 * (-Math.PI + Math.PI / 10 * i)));
            if (!client.level.hasChunkAt(new BlockPos(x - 6, 80, z - 6)) || !client.level.hasChunkAt(new BlockPos(x + 6, 80, z + 6))) continue;
            int found = -1;
            for (int height = 76; height <= 103; height += 3) {
                if (!client.level.getBlockState(new BlockPos(x, height, z)).is(Blocks.BEDROCK)) continue;
                int radius = 2 + (height - 76) / 9;
                boolean valid = true;
                for (int[] offset : new int[][]{{0,0},{radius,0},{-radius,0},{0,radius},{0,-radius}})
                    if (!client.level.getBlockState(new BlockPos(x + offset[0], height - 3, z + offset[1])).is(Blocks.OBSIDIAN)) valid = false;
                if (valid) { found = height; FinderMarkers.add("egox:end_pillar", Math.floorDiv(x, 16), Math.floorDiv(z, 16), new AABB(x - radius, 65, z - radius, x + radius + 1, height + 1, z + radius + 1), "End-Saeule " + (i + 1) + " | Hoehe " + height); break; }
            }
            if (found != heights[i]) { heights[i] = found; changed = true; }
        }
        if (changed) {
            SessionLibrary.event("PILLARS", heights.clone());
            candidates = List.of(); long token = ++generation;
            if (count() < 6) return;
            int[] samples = heights.clone();
            WORKER.execute(() -> {
                var result = EndPillars.candidates(samples);
                client.execute(() -> { if (token == generation) { candidates = result; EgoXSession.say(status()); } });
            });
        }
    }
}
