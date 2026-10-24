package de.egox.seedcracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.*;

public final class EgoXConfig {
    public int workers = Math.max(1, Math.min(16, Runtime.getRuntime().availableProcessors() - 2));
    public boolean autoUpload = true;
    public boolean collectBiomes = true;
    public boolean autoCrack = true;
    public boolean autoSaltCrack = true;
    public boolean autoBiomes = false;
    public boolean autoOres = false;
    public boolean bedrockChecker = true;
    public boolean geodeChecker = true;
    public boolean dungeonChecker = true;
    public boolean hud = false;
    public HudSettings hudStyle = new HudSettings();
    public int displayRevision = ConfigMigration.DISPLAY_REVISION;
    public boolean outlines = true;
    public java.util.Set<String> disabledOutlineTypes = OutlineVisibility.defaults();
    public boolean outlineLabels = true;
    public boolean finder = true;
    public boolean specialBlocks = true;
    public int outlineRange = 512;
    public String sheetUrl = "https://docs.google.com/spreadsheets/d/1y7HEf0x_j0Dg6THsi-lTT3T_u0-PdP0Q8SikVPsVacg/edit";
    public String uploadUrl = "https://script.google.com/macros/s/AKfycbze41WkekHq3L6EJLRlsvxyX_TbfV8cIHjECf6D-0SPhJTzzabz7qUBldKIlZwqxef-/exec";
    public String uploadToken = "";
    public String biomePreset = "overworld";
    public int maxBiomeSamples = 2048;
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("egoxseedcracker");
    private static final Path FILE = DIRECTORY.resolve("config.json");
    private static EgoXConfig instance;

    public static synchronized EgoXConfig get() {
        if (instance == null) reload();
        return instance;
    }
    public static synchronized void reload() {
        try {
            Files.createDirectories(DIRECTORY);
            boolean migrated = false;
            EgoXConfig next;
            if (Files.exists(FILE)) {
                var json = JSON.fromJson(Files.readString(FILE), com.google.gson.JsonObject.class);
                if (json == null) throw new IllegalArgumentException("Empty configuration");
                migrated = ConfigMigration.update(json);
                next = JSON.fromJson(json, EgoXConfig.class);
            } else next = new EgoXConfig();
            if (next == null) throw new IllegalArgumentException("Empty configuration");
            if (next.hudStyle == null) next.hudStyle = new HudSettings();
            next.hudStyle.validate();
            if (next.disabledOutlineTypes == null || next.disabledOutlineTypes.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("disabledOutlineTypes must be an array of type IDs");
            if (next.workers < 1 || next.workers > 64 || next.maxBiomeSamples < 16 || next.maxBiomeSamples > 16384
                || next.outlineRange < 16 || next.outlineRange > 512)
                throw new IllegalArgumentException("workers: 1..64; maxBiomeSamples: 16..16384");
            if (!java.util.Set.of("overworld", "large_biomes", "amplified").contains(next.biomePreset))
                throw new IllegalArgumentException("biomePreset: overworld, large_biomes or amplified");
            instance = next;
            if (migrated || !Files.exists(FILE)) save();
        } catch (IOException e) { throw new IllegalStateException("Cannot read EgoX config", e); }
    }
    public static synchronized void save() {
        try { Files.createDirectories(DIRECTORY); Files.writeString(FILE, JSON.toJson(instance)); }
        catch (IOException e) { throw new IllegalStateException("Cannot save EgoX config", e); }
    }
}
