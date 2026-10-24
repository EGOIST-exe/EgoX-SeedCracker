package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import java.util.*;

public final class StructureSeedReports {
    private StructureSeedReports() { }
    private static StructureSeedInfo info(Placement p) { return StructureSeedInfo.of(p, EgoXSession.knownSeed(), EgoXSession.salts().get(p.id()), EgoXSession.structures()); }
    public static List<String> all() {
        var result = new ArrayList<String>();
        result.add("Welt-Seed (64 Bit, Hash bestaetigt): " + (EgoXSession.knownSeed() == null ? "noch nicht bekannt" : EgoXSession.knownSeed()));
        var families = new TreeMap<String, Placement>();
        EgoXSession.structures().forEach(o -> families.put(o.placement().id(), o.placement()));
        EgoXSession.salts().keySet().forEach(id -> families.put(id, NativeProfiles.get(id)));
        for (Placement p : families.values()) {
            var data = info(p);
            result.add(p.id().replace("minecraft:", "") + ": Platzierungs-Seed48=" + (data.vanillaEquivalent48() == null ? "nicht bestimmt" : data.vanillaEquivalent48())
                + "; Salt=" + data.usedSalt() + "; " + data.saltStatus());
        }
        result.add("Details: /egox db seeds minecraft:trial_chambers");
        result.add("Seed48 meint Vanilla-aequivalente Strukturplatzierung, keinen unabhaengigen Welt-Seed.");
        return result;
    }
    public static List<String> one(String type) {
        var data = info(NativeProfiles.get(type));
        return List.of("Struktur: " + type + " | Placement-Familie: " + data.placement().id(),
            "Welt-Seed: " + (data.worldSeed() == null ? "noch nicht bekannt" : data.worldSeed()),
            "Salt: " + data.usedSalt() + " | Vanilla: " + data.placement().vanillaSalt() + " | " + data.saltStatus(),
            "Passende Starts: " + data.matches() + "/" + data.starts(),
            "Vanilla-aequivalenter Platzierungs-Seed (48 Bit): " + (data.vanillaEquivalent48() == null ? "nicht bestimmt" : data.vanillaEquivalent48()),
            "(Welt-Seed + Salt) mod 2^48: " + (data.seedPlusSalt48() == null ? "nicht bestimmt" : data.seedPlusSalt48()),
            "Paper-Feature-Seeds sind unabhaengig und werden hier nicht aus Struktur-Salts abgeleitet.");
    }
}
