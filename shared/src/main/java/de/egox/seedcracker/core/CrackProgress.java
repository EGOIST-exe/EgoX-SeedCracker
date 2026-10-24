package de.egox.seedcracker.core;

/** Counts real work within a named phase; a percentage never pretends to measure unknown work. */
public final class CrackProgress {
    public record Snapshot(String phase, long checked, long total, double seconds) {
        public double fraction() { return total == 0 ? 0 : Math.min(1, (double) checked / total); }
        public double rate() { return seconds <= 0 ? 0 : checked / seconds; }
        public double remainingSeconds() { return checked == 0 || seconds < 1 ? Double.NaN : (total - checked) / rate(); }
    }
    private String phase = "Bereit";
    private long checked, total, started = System.nanoTime();
    public synchronized void update(String nextPhase, long done, long count) {
        if (!phase.equals(nextPhase)) { phase = nextPhase; checked = 0; started = System.nanoTime(); }
        total = Math.max(0, count);
        checked = Math.min(total, Math.max(checked, done));
    }
    public synchronized Snapshot snapshot() { return new Snapshot(phase, checked, total, (System.nanoTime() - started) / 1e9); }
    public synchronized void reset() { phase = "Bereit"; checked = total = 0; started = System.nanoTime(); }
}
