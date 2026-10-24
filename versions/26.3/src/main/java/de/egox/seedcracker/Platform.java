package de.egox.seedcracker;

import java.net.URI;
public final class Platform {
    public static void openDirectory(java.nio.file.Path directory) { com.mojang.blaze3d.Blaze3D.openUri(directory.toUri()); }
    public static int chunkX(net.minecraft.world.level.ChunkPos pos) { return pos.x(); }
    public static int chunkZ(net.minecraft.world.level.ChunkPos pos) { return pos.z(); }
    public static void message(String text) {
        net.minecraft.client.Minecraft.getInstance().gui.chatListener().handleSystemMessage(net.minecraft.network.chat.Component.literal(text), false);
    }
    public static void openDiscord() { com.mojang.blaze3d.Blaze3D.openUri(URI.create("https://discord.gg/FFuMpJg6yj")); }
    public static void openSheet(String url) {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !"docs.google.com".equals(uri.getHost()) || !uri.getPath().startsWith("/spreadsheets/d/"))
            throw new IllegalArgumentException("Bitte sheetUrl in config/egoxseedcracker/config.json eintragen");
        com.mojang.blaze3d.Blaze3D.openUri(uri);
    }
}
