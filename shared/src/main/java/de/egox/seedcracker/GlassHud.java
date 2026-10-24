package de.egox.seedcracker;

/** Compact glass-style HUD; all geometry is in scaled GUI pixels. */
public final class GlassHud {
    public record Model(String state, String modes, String stats, String phase, String value, String detail, double progress) { }
    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double mx, double my) { return width > 0 && mx >= x && mx <= x + width && my >= y && my <= y + height; }
    }
    private GlassHud() { }
    public static void render(StatusDisplay.Canvas canvas, Model model) {
        render(canvas, model, new HudSettings());
    }
    public static Bounds bounds(int screenWidth, int screenHeight, HudSettings style) {
        int width = Math.min(292, screenWidth - 24);
        int height = 10 + (style.header ? 14 : 0) + (style.stats ? 12 : 0) + (style.phase ? 12 : 0) + (style.progress ? 8 : 0) + (style.detail ? 12 : 4);
        if (width < 196 || screenHeight < height + 25 || !style.any()) return new Bounds(0, 0, 0, 0);
        return new Bounds(12 + (int)Math.round((screenWidth - width - 24) * style.x),
            10 + (int)Math.round((screenHeight - height - 25) * style.y), width, height);
    }
    public static void place(HudSettings style, int screenWidth, int screenHeight, double x, double y) {
        var b = bounds(screenWidth, screenHeight, style);
        int travelX = screenWidth - b.width() - 24, travelY = screenHeight - b.height() - 25;
        if (travelX > 0) style.x = Math.clamp((x - 12) / travelX, 0, 1);
        if (travelY > 0) style.y = Math.clamp((y - 10) / travelY, 0, 1);
    }
    public static void render(StatusDisplay.Canvas canvas, Model model, HudSettings style) {
        var bounds = bounds(canvas.width(), canvas.height(), style);
        int x = bounds.x(), y = bounds.y(), width = bounds.width(), height = bounds.height(), radius = 10;
        if (width == 0) return;
        if (style.background > 0) {
        rounded(canvas, x - 2, y + 2, width + 4, height + 3, radius + 2, alpha(0x18000712, style.background));
        rounded(canvas, x - 1, y + 1, width + 2, height + 1, radius + 1, alpha(0x30000712, style.background));
        rounded(canvas, x, y, width, height, radius, alpha(0x65D2EFFF, style.background));
        rounded(canvas, x + 1, y + 1, width - 2, height - 2, radius - 1, alpha(0xE01A2735, style.background));
        // Clipped tint and a specular rim give depth without a full-screen world blur.
        for (int row = 0; row < height - 2; row++) {
            double light = 1 - (double)row / (height - 3);
            int inset = cornerInset(row, height - 2, radius - 1);
            int alpha = 10 + (int)(light * 24);
            canvas.fill(x + 1 + inset, y + 1 + row, x + width - 1 - inset, y + 2 + row, alpha((alpha << 24) | 0xC6E9FF, style.background));
        }
        canvas.fill(x + radius + 1, y + 1, x + width - radius - 1, y + 2, alpha(0x9CE7FAFF, style.background));
        canvas.fill(x + radius + 4, y + 2, x + width / 2, y + 3, alpha(0x30FFFFFF, style.background));
        }
        int row = y + 10;
        if (style.header) {
            canvas.text("EGOX", x + 11, row, 0xFFA9F0E3, 36);
            if (width >= 270) canvas.text(model.state(), x + 50, row, 0xFFDBE8F4, width - 169);
            canvas.text(model.modes(), x + width - 114, row, 0xFFD3DFEA, 104); row += 14;
        }
        if (style.stats) { canvas.text(model.stats(), x + 11, row, 0xFFF1F6FC, width - 22); row += 12; }
        if (style.phase) {
            canvas.text(model.phase(), x + 11, row, 0xFFCFDDEB, width - 77);
            canvas.text(model.value(), x + width - 61, row, 0xFFAFF1E4, 51); row += 12;
        }
        if (style.progress) {
        canvas.fill(x + 11, row, x + width - 11, row + 3, 0x6C0B1522);
        int end = x + 11 + (int)Math.round((width - 22) * Math.max(0, Math.min(1, model.progress())));
        canvas.fill(x + 11, row, end, row + 3, 0xFF8BE9D4);
        canvas.fill(x + 11, row, end, row + 1, 0xFFCAFFF2); row += 8;
        }
        if (style.detail) canvas.text(model.detail(), x + 11, row, 0xFFD3DFEA, width - 22);
    }
    private static int alpha(int color, int percent) { return (Math.min(255, (int)Math.round((color >>> 24) * percent / 88.0)) << 24) | (color & 0xFFFFFF); }
    private static int cornerInset(int row, int height, int radius) {
        int edge = Math.min(row, height - 1 - row);
        if (edge >= radius) return 0;
        double distance = radius - edge - .5;
        return (int)Math.ceil(radius - Math.sqrt(radius * radius - distance * distance));
    }
    private static void rounded(StatusDisplay.Canvas canvas, int x, int y, int width, int height, int radius, int color) {
        canvas.fill(x, y + radius, x + width, y + height - radius, color);
        for (int row = 0; row < radius; row++) {
            int inset = cornerInset(row, height, radius);
            canvas.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            canvas.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }
}
