package de.egox.seedcracker;

/** Portable HUD preferences, independent of the running client and GUI scale. */
public final class HudSettings {
    public double x = .5, y = 0;
    public int background = 88;
    public boolean header = true, stats = true, phase = true, progress = true, detail = true;
    public boolean any() { return header || stats || phase || progress || detail; }
    public void validate() {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1 || background < 0 || background > 100)
            throw new IllegalArgumentException("HUD: position 0..1; background 0..100");
    }
    public void resetPosition() { x = .5; y = 0; }
    public HudSettings copy() {
        var next = new HudSettings(); next.x=x; next.y=y; next.background=background;
        next.header=header; next.stats=stats; next.phase=phase; next.progress=progress; next.detail=detail; return next;
    }
}
