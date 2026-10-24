package de.egox.seedcracker;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.egox.seedcracker.core.Placement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import java.util.*;

/** Reads this Minecraft version's own bootstrap data, including newly added structures. */
public final class NativeProfiles {
    private static Map<String, Placement> profiles;
    public static synchronized Map<String, Placement> all() {
        if (profiles != null) return profiles;
        Map<String, Placement> result = new TreeMap<>();
        NativeBiomes.registries().lookupOrThrow(Registries.STRUCTURE_SET).listElements().forEach(holder -> {
            var set = holder.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement p)) return;
            JsonObject json = RandomSpreadStructurePlacement.CODEC.codec().encodeStart(NativeBiomes.registries().createSerializationContext(JsonOps.INSTANCE), p).getOrThrow().getAsJsonObject();
            int salt = json.has("salt") ? json.get("salt").getAsInt() : 0;
            String setId = holder.key().identifier().toString();
            Placement profile = new Placement(setId, p.spacing(), p.separation(), salt,
                p.spreadType() == RandomSpreadType.TRIANGULAR ? Placement.Spread.TRIANGULAR : Placement.Spread.LINEAR);
            result.put(setId, profile);
            set.structures().forEach(entry -> entry.structure().unwrapKey().ifPresent(key -> result.put(key.identifier().toString(), profile)));
        });
        profiles = Collections.unmodifiableMap(result);
        return profiles;
    }
    public static Placement get(String name) {
        Placement result = all().get(name.contains(":") ? name : "minecraft:" + name);
        if (result == null) throw new IllegalArgumentException("Unbekannte Random-Spread-Struktur: " + name);
        return result;
    }
}
