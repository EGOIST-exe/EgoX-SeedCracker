package de.egox.seedcracker;

import de.egox.seedcracker.core.StructureObservation;
import java.util.*;

/** Evidence selection is separate from outline visibility and durable raw observations. */
public final class CrackingStructures {
    private CrackingStructures() { }
    public static Set<String> defaults() { return new HashSet<>(Set.of("minecraft:trial_chambers")); }
    public static List<String> types() { return NativeProfiles.all().values().stream().map(p -> p.id()).distinct().sorted().toList(); }
    public static String resolve(String id) { return NativeProfiles.get(id.contains(":") ? id : "minecraft:" + id).id(); }
    public static boolean enabled(String id) { return !EgoXConfig.get().disabledCrackingStructures.contains(resolve(id)); }
    public static List<StructureObservation> filter(Collection<StructureObservation> observations, Set<String> disabled) {
        return observations.stream().filter(o -> !disabled.contains(o.placement().id())).toList();
    }
    public static void set(String id, boolean enabled) {
        String canonical = resolve(id); var config = EgoXConfig.get();
        if (enabled) config.disabledCrackingStructures.remove(canonical); else config.disabledCrackingStructures.add(canonical);
        EgoXConfig.save();
        // Cancel searches that captured the old evidence selection before allowing another run.
        if (EgoXSession.job != null) EgoXSession.job.cancel();
        AutoCracker.stop(); PillarCracker.stop(); AutoSaltCracker.pause();
        AutoCracker.observationsChanged();
        if (config.autoCrack) AutoCracker.resume();
        if (config.autoSaltCrack) AutoSaltCracker.resume();
    }
}
