package de.egox.seedcracker;

import com.google.gson.JsonObject;

/** Apply the new display defaults once, preserving subsequent user choices and upload settings. */
public final class ConfigMigration {
    public static final int DISPLAY_REVISION = 4;
    private ConfigMigration() { }
    public static boolean update(JsonObject json) {
        int revision = json.has("displayRevision") ? json.get("displayRevision").getAsInt() : 0;
        if (revision >= DISPLAY_REVISION) return false;
        if (revision < 1) {
            json.addProperty("hud", false);
            json.addProperty("outlineRange", 512);
        }
        if (!json.has("disabledOutlineTypes")) {
            var disabled = new com.google.gson.JsonArray();
            disabled.add(OutlineVisibility.GEODE);
            json.add("disabledOutlineTypes", disabled);
        }
        if (revision < 3) {
            json.addProperty("autoBiomes", false);
            json.addProperty("autoOres", false);
        }
        if (revision < 4) {
            json.addProperty("dungeonChecker", false);
            if (!json.has("disabledCrackingStructures")) {
                var disabled = new com.google.gson.JsonArray(); disabled.add("minecraft:trial_chambers");
                json.add("disabledCrackingStructures", disabled);
            }
        }
        json.addProperty("displayRevision", DISPLAY_REVISION);
        return true;
    }
}
