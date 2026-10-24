package de.egox.seedcracker;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class OverlayPlatform {
    public static boolean hidden() { return Minecraft.getInstance().options.hideGui; }
    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("egoxseedcracker", "status"), (graphics, ticks) -> {
            var client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) return;
            StatusDisplay.render(new StatusDisplay.Canvas() {
                public int width() { return graphics.guiWidth(); }
                public int height() { return graphics.guiHeight(); }
                public void fill(int x0, int y0, int x1, int y1, int color) { graphics.fill(x0, y0, x1, y1, color); }
                public void text(String value, int x, int y, int color, int maxWidth) { graphics.drawString(client.font, client.font.plainSubstrByWidth(value, maxWidth), x, y, color, false); }
            });
        });
    }
}
