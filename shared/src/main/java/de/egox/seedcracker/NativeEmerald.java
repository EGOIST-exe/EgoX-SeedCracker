package de.egox.seedcracker;

import com.google.gson.*;
import de.egox.seedcracker.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

/** Conservative emerald population-input checker for the installed vanilla version. Other ores remain raw observations. */
public final class NativeEmerald {
    private static EmeraldReplay.Settings settings;
    private NativeEmerald() { }
    public static synchronized EmeraldReplay.Settings settings() {
        if (settings != null) return settings;
        var placed = json("placed_feature/ore_emerald.json");
        JsonObject feature;
        try { feature = json("feature/ore_emerald.json"); }
        catch (IllegalStateException e) { feature = json("configured_feature/ore_emerald.json").getAsJsonObject("config"); }
        if (feature.get("size").getAsInt() != 3 || feature.get("discard_chance_on_air_exposure").getAsDouble() != 0)
            throw new IllegalStateException("Emerald replay needs native size 3 and zero air discard");
        var modifiers = placed.getAsJsonArray("placement");
        if (modifiers.size() != 4 || !modifiers.get(0).getAsJsonObject().get("type").getAsString().equals("minecraft:count")
            || !modifiers.get(1).getAsJsonObject().get("type").getAsString().equals("minecraft:in_square")
            || !modifiers.get(2).getAsJsonObject().get("type").getAsString().equals("minecraft:height_range")
            || !modifiers.get(3).getAsJsonObject().get("type").getAsString().equals("minecraft:biome")) throw new IllegalStateException("Unsupported emerald placement pipeline");
        var height = modifiers.get(2).getAsJsonObject().getAsJsonObject("height");
        if (!height.get("type").getAsString().equals("minecraft:trapezoid")) throw new IllegalStateException("Unsupported emerald height sampler");
        int[] order = NativeFeatureOrder.get("minecraft:ore_emerald"); int index=order[0],step=order[1];
        settings = new EmeraldReplay.Settings(modifiers.get(0).getAsJsonObject().get("count").getAsInt(),
            height.getAsJsonObject("min_inclusive").get("absolute").getAsInt(), height.getAsJsonObject("max_inclusive").get("absolute").getAsInt(),
            height.has("plateau") ? height.get("plateau").getAsInt() : 0, index, step);
        return settings;
    }
    private static JsonObject json(String path) {
        try (var input = NativeEmerald.class.getResourceAsStream("/data/minecraft/worldgen/" + path)) {
            if (input == null) throw new IllegalStateException("Native resource missing: " + path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static LongPredicate predicate(List<BlockObservation> observations, BooleanSupplier cancelled) {
        var supported = observations.stream().filter(EmeraldReplay::supports).sorted(Comparator.comparingInt(BlockObservation::y)).limit(64).toList();
        if (supported.isEmpty()) throw new IllegalStateException("Keine serverseitig beobachteten Smaragderz-Abbauten vorhanden");
        var settings = settings();
        return candidate -> {
            boolean[] possible = new boolean[supported.size()];
            var chunks = new LinkedHashSet<Long>();
            for (var o : supported) for (int x = Math.floorDiv(o.x() - 2, 16); x <= Math.floorDiv(o.x() + 2, 16); x++)
                for (int z = Math.floorDiv(o.z() - 2, 16); z <= Math.floorDiv(o.z() + 2, 16); z++) chunks.add(((long)x << 32) | (z & 0xFFFFFFFFL));
            var random = new WorldgenRandom(new XoroshiroRandomSource(0));
            for (long chunk : chunks) {
                if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                int x = (int)(chunk >> 32), z = (int)chunk;
                long population = random.setDecorationSeed(candidate, x * 16, z * 16);
                long featureSeed = population + settings.index() + 10000L * settings.step();
                var indices=new ArrayList<Integer>();var local=new ArrayList<BlockObservation>();
                for(int i=0;i<supported.size();i++){var o=supported.get(i);if(o.x()>=x*16-2&&o.x()<=x*16+17&&o.z()>=z*16-2&&o.z()<=z*16+17){indices.add(i);local.add(o);}}
                var matches = EmeraldReplay.possible(featureSeed, x, z, settings, local, cancelled);
                for (int i = 0; i < local.size(); i++) possible[indices.get(i)] |= matches[i];
            }
            for (boolean value : possible) if (!value) return false;
            return true;
        };
    }
}
