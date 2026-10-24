package de.egox.seedcracker.core;

import java.util.*;

/** An observed intact monster-room floor; natural origin is a model assumption. */
public record DungeonObservation(int x, int y, int z, List<Layout> layouts, String time, int chunkX, int chunkZ, String dimension) {
    public DungeonObservation(int x, int y, int z, List<Layout> layouts, String time) { this(x, y, z, layouts, time, Math.floorDiv(x, 16), Math.floorDiv(z, 16), "minecraft:overworld"); }
    public record Layout(int radiusX, int radiusZ, List<Integer> floor) {
        public Layout {
            if (radiusX < 2 || radiusX > 3 || radiusZ < 2 || radiusZ > 3 || floor == null
                || floor.size() != (2 * radiusX + 3) * (2 * radiusZ + 3)
                || floor.stream().anyMatch(v -> v == null || v < 0 || v > 1)) throw new IllegalArgumentException("Invalid dungeon floor");
            floor = List.copyOf(floor);
        }
        public double patternBits() { return floor.stream().mapToDouble(v -> v == 0 ? 2 : -Math.log(.75) / Math.log(2)).sum(); }
    }
    public DungeonObservation {
        if (layouts == null || layouts.isEmpty() || layouts.size() > 4 || time == null) throw new IllegalArgumentException("Invalid dungeon observation");
        layouts = List.copyOf(layouts);
        chunkX = Math.floorDiv(x, 16); chunkZ = Math.floorDiv(z, 16);
        if (dimension == null) dimension = "minecraft:overworld";
        if (!dimension.equals("minecraft:overworld")) throw new IllegalArgumentException("Dungeons require Overworld");
    }
    public String key() { return x + ":" + y + ":" + z; }
    public double patternBits() { return layouts.stream().mapToDouble(Layout::patternBits).min().orElse(0); }
}
