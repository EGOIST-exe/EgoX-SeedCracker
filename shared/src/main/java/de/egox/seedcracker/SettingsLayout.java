package de.egox.seedcracker;

/** Pagination leaves a fixed, keyboard-reachable footer at different GUI scales. */
public record SettingsLayout(int left, int width, int column, int rows) {
    public static SettingsLayout of(int width, int height) {
        int panel = Math.min(520, Math.max(120, width - 24));
        return new SettingsLayout((width - panel) / 2, panel, (panel - 8) / 2, Math.max(1, (height - 160) / 26));
    }
    public int capacity() { return rows * 2; }
    public int x(int index) { return left + index % 2 * (column + 8); }
    public int y(int index) { return 72 + index / 2 * 26; }
}
