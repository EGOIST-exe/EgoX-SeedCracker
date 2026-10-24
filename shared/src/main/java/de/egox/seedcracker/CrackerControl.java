package de.egox.seedcracker;

/** Shared command actions; seed and salt workers can be paused independently. */
public final class CrackerControl {
    private CrackerControl() { }
    public static void seed(boolean enabled) {
        EgoXConfig.get().autoCrack = enabled; EgoXConfig.save();
        if (enabled) { AutoCracker.resume(); PillarCracker.reset(); } else { AutoCracker.stop(); PillarCracker.stop(); }
        EgoXSession.say("Seed-Cracker: " + (enabled ? "ON" : "OFF"));
    }
    public static void salt(boolean enabled) {
        EgoXConfig.get().autoSaltCrack = enabled; EgoXConfig.save();
        if (enabled) AutoSaltCracker.resume(); else {
            AutoSaltCracker.pause();
            if (EgoXSession.job != null && EgoXSession.job.snapshot().label().startsWith("Automatischer Salt")) EgoXSession.job.cancel();
        }
        EgoXSession.say("Salt-Cracker: " + (enabled ? "ON" : "OFF"));
    }
    public static void biomes(boolean enabled) {
        EgoXConfig.get().autoBiomes = enabled; EgoXConfig.save();
        if (enabled) AutoCracker.retry();
        EgoXSession.say("Automatische Biom-Pruefung: " + (enabled ? "ON" : "OFF"));
    }
    public static void ore(boolean enabled) {
        EgoXConfig.get().autoOres = enabled; EgoXConfig.save(); if (enabled) AutoCracker.retry();
        EgoXSession.say("Erz-Kandidatenpruefung: " + (enabled ? "ON" : "OFF") + "; Erz-Abbau wird unabhaengig davon lokal protokolliert.");
    }
    public static void bedrock(boolean enabled) {
        EgoXConfig.get().bedrockChecker = enabled; EgoXConfig.save(); if (enabled) AutoCracker.retry();
        EgoXSession.say("Bedrock-Kandidatenpruefung: " + (enabled ? "ON" : "OFF"));
    }
    public static void geode(boolean enabled) {
        EgoXConfig.get().geodeChecker=enabled;EgoXConfig.save();if(enabled)AutoCracker.retry();
        EgoXSession.say("Geoden-Kandidatenpruefung: "+(enabled?"ON":"OFF")+"; der Outline-Schalter bleibt separat.");
    }
    public static void dungeon(boolean enabled) {
        EgoXConfig.get().dungeonChecker = enabled; EgoXConfig.save(); if (enabled) AutoCracker.retry();
        EgoXSession.say("Dungeon-Musterpruefung: " + (enabled ? "ON" : "OFF") + "; Welt-Seed-Kandidatenfilter.");
    }
}
