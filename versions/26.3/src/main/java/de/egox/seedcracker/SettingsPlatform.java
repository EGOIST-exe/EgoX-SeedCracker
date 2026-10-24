package de.egox.seedcracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class SettingsPlatform extends EgoXSettingsScreen {
    public SettingsPlatform(Screen parent, boolean editor) { super(parent, editor); }
    public static Screen current() { return Minecraft.getInstance().gui.screen(); }
    public static void show(Screen screen) { Minecraft.getInstance().gui.setScreen(screen); }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float tick) {
        if (!editor) super.extractBackground(graphics, x, y, tick);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {
        drawContents(new StatusDisplay.Canvas() {
            public int width() { return graphics.guiWidth(); }
            public int height() { return graphics.guiHeight(); }
            public void fill(int x0,int y0,int x1,int y1,int color) { graphics.fill(x0,y0,x1,y1,color); }
            public void text(String text,int tx,int ty,int color,int limit) { graphics.text(minecraft.font,minecraft.font.plainSubstrByWidth(text,limit),tx,ty,color,false); }
        });
        super.extractRenderState(graphics,x,y,tick);
    }
}
