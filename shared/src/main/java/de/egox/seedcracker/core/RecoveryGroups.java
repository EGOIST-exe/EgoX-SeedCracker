package de.egox.seedcracker.core;

import java.util.*;

/** Retry individual placement families so a different family's unknown salt cannot veto a valid seed. */
public final class RecoveryGroups {
    private RecoveryGroups() { }
    public static List<List<StructureObservation>> plan(List<StructureObservation> all) {
        var result = new ArrayList<List<StructureObservation>>();
        if (eligible(all)) result.add(List.copyOf(all));
        var groups = new TreeMap<String, List<StructureObservation>>();
        all.forEach(o -> groups.computeIfAbsent(o.placement().id(), k -> new ArrayList<>()).add(o));
        if (groups.size() > 1) groups.values().stream().filter(RecoveryGroups::eligible).forEach(g -> result.add(List.copyOf(g)));
        return List.copyOf(result);
    }
    private static boolean eligible(List<StructureObservation> observations) {
        var bits = ObservationBits.summarize(observations);
        return bits.liftingStarts() >= 5 && bits.liftingBits() >= 44;
    }
}
