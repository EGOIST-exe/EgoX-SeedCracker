package de.egox.seedcracker.core;

import java.util.*;

/** A per-family effective placement seed is not another 64-bit world seed. */
public record StructureSeedInfo(Placement placement, Long worldSeed, long usedSalt, boolean overridden, int matches, int starts,
                                Long vanillaEquivalent48, Long seedPlusSalt48) {
    public static StructureSeedInfo of(Placement placement, Long worldSeed, Long override, Collection<StructureObservation> observations) {
        long salt = override == null ? placement.vanillaSalt() : override;
        var samples = observations.stream().filter(o -> o.placement().id().equals(placement.id())).toList();
        var random = new LegacyRandom();
        int matches = worldSeed == null ? 0 : (int)samples.stream().filter(o -> o.matches(worldSeed, salt, random)).count();
        boolean usable = worldSeed != null && (override != null || (!samples.isEmpty() && matches == samples.size()));
        return new StructureSeedInfo(placement, worldSeed, salt, override != null, matches, samples.size(),
            usable ? (worldSeed + salt - placement.vanillaSalt()) & LegacyRandom.MASK : null,
            usable ? (worldSeed + salt) & LegacyRandom.MASK : null);
    }
    public String saltStatus() {
        if (worldSeed == null) return overridden ? "Override, Welt-Seed fehlt" : "unbekannt (Vanilla-Annahme)";
        if (starts > 0 && matches != starts) return "ABWEICHUNG: Salt/Start/Generator pruefen";
        return overridden ? "Override (gesetzt/ermittelt)" : starts == 0 ? "unbekannt (keine Beobachtungen)" : "Vanilla-Annahme passt zu Beobachtungen";
    }
}
