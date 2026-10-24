package de.egox.seedcracker;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded only through Mod Menu's optional entrypoint. */
public final class EgoXModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() { return parent -> new SettingsPlatform(parent, false); }
}
