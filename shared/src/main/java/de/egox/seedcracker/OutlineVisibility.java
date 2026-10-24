package de.egox.seedcracker;

import java.util.*;

/** Display preferences are independent of the observations used by the cracker. */
public final class OutlineVisibility {
    public static final String GEODE = "egox:geode";
    private static final Set<String> SPECIAL = Set.of("egox:mob_spawner", "egox:trial_spawner", "egox:vault", GEODE, "egox:end_pillar");
    private OutlineVisibility() { }
    public static Set<String> defaults() { return new TreeSet<>(Set.of(GEODE)); }
    public static Set<String> types() {
        var types = new TreeSet<>(SPECIAL);
        StructureSignatures.SIGNATURES.forEach(s -> types.add(s.type()));
        types.add("minecraft:monument");
        NativeTemplateFinder.variants().forEach(v -> types.add(v.type()));
        return Collections.unmodifiableSet(types);
    }
    public static String resolve(String name) {
        String id = name.contains(":") ? name : SPECIAL.contains("egox:" + name) ? "egox:" + name : "minecraft:" + name;
        if (!types().contains(id)) throw new IllegalArgumentException("Unbekannter Outline-Typ: " + name + ". /egox render outlines list");
        return id;
    }
    public static boolean enabled(Set<String> disabled, String type) { return !disabled.contains(type); }
    public static boolean visible(Set<String> disabled, boolean features, String type) {
        return enabled(disabled, type) && (features || !SPECIAL.contains(type) || type.equals("egox:end_pillar"));
    }
    public static void set(Set<String> disabled, String type, boolean enabled) {
        if (enabled) disabled.remove(type); else disabled.add(type);
    }
}
