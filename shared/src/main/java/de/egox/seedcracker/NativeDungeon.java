package de.egox.seedcracker;

import com.google.gson.*;
import de.egox.seedcracker.core.*;
import net.minecraft.world.level.levelgen.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

/** Conservative modern monster-room stream check. Unknown prior attempts are overapproximated. */
public final class NativeDungeon {
    public record Settings(String id, int count, int minY, int maxY, int index, int step) { }
    private static List<Settings> settings;
    private NativeDungeon() { }
    private static JsonObject json(String path) {
        try (var input = NativeDungeon.class.getResourceAsStream("/data/minecraft/worldgen/" + path)) {
            if (input == null) throw new IllegalStateException("Native resource missing: " + path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    public static synchronized List<Settings> settings() {
        if (settings != null) return settings;
        var noise = json("noise_settings/overworld.json").getAsJsonObject("noise"); int bottom = noise.get("min_y").getAsInt(), top = bottom + noise.get("height").getAsInt() - 1;
        var result = new ArrayList<Settings>();
        for (var id : List.of("monster_room", "monster_room_deep")) {
            var placement = json("placed_feature/" + id + ".json").getAsJsonArray("placement");
            String[] expected = {"count", "in_square", "height_range", "biome"};
            if (placement.size() != 4) throw new IllegalStateException("Unsupported dungeon placement");
            for (int i = 0; i < 4; i++) if (!placement.get(i).getAsJsonObject().get("type").getAsString().equals("minecraft:" + expected[i])) throw new IllegalStateException("Unsupported dungeon modifier");
            var height = placement.get(2).getAsJsonObject().getAsJsonObject("height");
            if (!height.get("type").getAsString().equals("minecraft:uniform")) throw new IllegalStateException("Unsupported dungeon height");
            int[] order = NativeFeatureOrder.get("minecraft:" + id);
            result.add(new Settings(id, placement.get(0).getAsJsonObject().get("count").getAsInt(), anchor(height.getAsJsonObject("min_inclusive"), bottom, top), anchor(height.getAsJsonObject("max_inclusive"), bottom, top), order[0], order[1]));
        }
        return settings = List.copyOf(result);
    }
    private static int anchor(JsonObject anchor, int bottom, int top) { return anchor.has("absolute") ? anchor.get("absolute").getAsInt() : anchor.has("above_bottom") ? bottom + anchor.get("above_bottom").getAsInt() : top - anchor.get("below_top").getAsInt(); }
    public static boolean rejects(int bits, int bound) { return bits - bits % bound + bound - 1 < 0; }
    public static boolean matches(long seed, DungeonObservation room, Settings s, BooleanSupplier cancelled) {
        if (room.y() < s.minY() || room.y() > s.maxY()) return false;
        // Before a later attempt: three placement calls, two radii, <=81 floor calls,
        // <=12 chest-coordinate calls, <=4 loot-seed calls and one mob call.
        // Count all possible offsets, instead of guessing which earlier attempts succeeded.
        int maxOffset = (s.count() - 1) * 103, height = s.maxY() - s.minY() + 1;
        int[] draws = new int[maxOffset + 103];
        var nativeRandom = new WorldgenRandom(new XoroshiroRandomSource(0)); long population = nativeRandom.setDecorationSeed(seed, room.chunkX() * 16, room.chunkZ() * 16);
        var random = new XoroshiroWorldgen(population + s.index() + 10000L * s.step());
        for (int i = 0; i < draws.length; i++) {
            if (cancelled.getAsBoolean()) return true;
            draws[i] = random.bits(31);
            // A rejected bounded draw can extend the stream beyond this finite envelope.
            // Retain that candidate conservatively; never turn the bound into a false negative.
            if (rejects(draws[i], height) || rejects(draws[i], 5) || rejects(draws[i], 7)) return true;
        }
        for (int offset = 0; offset <= maxOffset; offset++) {
            if (draws[offset] >>> 27 != Math.floorMod(room.x(), 16) || draws[offset + 1] >>> 27 != Math.floorMod(room.z(), 16)
                || s.minY() + draws[offset + 2] % height != room.y()) continue;
            int rx = (draws[offset + 3] >>> 30) + 2, rz = (draws[offset + 4] >>> 30) + 2;
            for (var layout : room.layouts()) {
                if (layout.radiusX() != rx || layout.radiusZ() != rz) continue;
                boolean matching = true;
                for (int i = 0; i < layout.floor().size(); i++) if ((draws[offset + 5 + i] >>> 29 == 0 ? 0 : 1) != layout.floor().get(i)) { matching = false; break; }
                if (matching) return true;
            }
        }
        return false;
    }
    public static LongPredicate predicate(List<DungeonObservation> rooms, BooleanSupplier cancelled) {
        if (rooms.isEmpty()) throw new IllegalStateException("Intakte Overworld-Dungeons benoetigt");
        var order = settings(); var selected = rooms.stream().sorted(Comparator.comparingDouble(DungeonObservation::patternBits).reversed()).limit(8).toList();
        return seed -> {
            for (var room : selected) {
                boolean possible = false;
                for (var s : order) if (matches(seed, room, s, cancelled)) { possible = true; break; }
                if (!possible) return false;
            }
            return true;
        };
    }
}
