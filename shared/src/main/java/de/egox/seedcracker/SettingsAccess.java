package de.egox.seedcracker;

/** Client commands must open their screen after ChatScreen has closed itself. */
public final class SettingsAccess {
    private static boolean requested;
    private SettingsAccess() { }
    public static void requestOpen() { requested = true; }
    public static boolean editingHud() { return SettingsPlatform.current() instanceof EgoXSettingsScreen screen && screen.editingHud(); }
    public static void tick() {
        if (!requested) return;
        requested = false;
        SettingsPlatform.show(new SettingsPlatform(null, false));
    }
}
