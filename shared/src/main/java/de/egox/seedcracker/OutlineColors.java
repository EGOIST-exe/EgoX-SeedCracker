package de.egox.seedcracker;

import java.util.*;

/** Stable colors by identifier. Verification changes labels, never the structure's color. */
public final class OutlineColors {
    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static final Set<Integer> USED = new HashSet<>();
    static {
        var primary = new LinkedHashMap<String, Integer>();
        primary.put("minecraft:desert_pyramid", 0xFFFFC45C);
        primary.put("minecraft:jungle_pyramid", 0xFF60DC75);
        primary.put("minecraft:swamp_hut", 0xFFA5CB63);
        primary.put("minecraft:monument", 0xFF57DDD7);
        primary.put("minecraft:shipwreck", 0xFFD7A477);
        primary.put("minecraft:igloo", 0xFFD1EEFF);
        primary.put("minecraft:end_city", 0xFFCA8AFF);
        primary.put("minecraft:pillager_outpost", 0xFFFF8966);
        primary.put("minecraft:ancient_city", 0xFF579EDF);
        primary.put("minecraft:trial_chambers", 0xFFB3DE75);
        primary.put("minecraft:trail_ruins", 0xFFDA8BBA);
        primary.put("minecraft:bastion_remnant", 0xFFF0BC8A);
        primary.put("egox:end_pillar", 0xFFACB1FF);
        primary.put("egox:mob_spawner", 0xFFFF647F);
        primary.put("egox:trial_spawner", 0xFFFFA44F);
        primary.put("egox:vault", 0xFF6BAFFF);
        primary.put("egox:geode", 0xFFE9A9FF);
        primary.forEach((id, color) -> { COLORS.put(id, color); USED.add(color); });
    }
    private OutlineColors() { }
    public static synchronized void register(Collection<String> types) { new TreeSet<>(types).forEach(OutlineColors::color); }
    public static synchronized int color(String type) {
        Integer cached = COLORS.get(type); if (cached != null) return cached;
        long hash = 2166136261L;
        for (char c : type.toCharArray()) hash = ((hash ^ c) * 16777619L) & 0xFFFFFFFFL;
        int candidate; int attempt = 0;
        do {
            double hue = ((hash + attempt++ * 13751L) % 36000) / 6000.0;
            double saturation = .42 + ((hash >>> 8) & 255) / 255.0 * .35;
            double value = .9 + ((hash >>> 16) & 255) / 255.0 * .1;
            int sector = (int)hue; double f = hue - sector;
            double p = value * (1 - saturation), q = value * (1 - saturation * f), t = value * (1 - saturation * (1 - f));
            double[] rgb = switch (sector) {
                case 0 -> new double[]{value,t,p}; case 1 -> new double[]{q,value,p}; case 2 -> new double[]{p,value,t};
                case 3 -> new double[]{p,q,value}; case 4 -> new double[]{t,p,value}; default -> new double[]{value,p,q};
            };
            candidate = 0xFF000000 | ((int)Math.round(rgb[0]*255) << 16) | ((int)Math.round(rgb[1]*255) << 8) | (int)Math.round(rgb[2]*255);
        } while (USED.contains(candidate));
        COLORS.put(type, candidate); USED.add(candidate); return candidate;
    }
}
