package de.egox.seedcracker;

/** Pointer offset is retained so grabbing the HUD never makes it jump to the cursor. */
public final class HudDragController {
    private final HudSettings style;
    private boolean dragging;
    private double offsetX, offsetY;
    public HudDragController(HudSettings style) { this.style = style; }
    public boolean begin(double x, double y, int width, int height) {
        var b = GlassHud.bounds(width, height, style);
        dragging = b.contains(x, y); offsetX = x - b.x(); offsetY = y - b.y(); return dragging;
    }
    public boolean move(double x, double y, int width, int height) {
        if (!dragging) return false;
        GlassHud.place(style, width, height, x - offsetX, y - offsetY); return true;
    }
    public boolean end() { boolean previous = dragging; dragging = false; return previous; }
    public void nudge(int x, int y, int width, int height) {
        var b = GlassHud.bounds(width, height, style); if (b.width() > 0) GlassHud.place(style, width, height, b.x() + x, b.y() + y);
    }
}
