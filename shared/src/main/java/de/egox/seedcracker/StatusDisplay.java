package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import java.util.*;

/** One presentation model shared by chat and the HUD. */
public final class StatusDisplay {
    public interface Canvas {
        int width();
        default int height() { return 240; }
        void fill(int x0, int y0, int x1, int y1, int color);
        void text(String value, int x, int y, int color, int maxWidth);
    }
    private static volatile List<String> lines = List.of();
    private static volatile double fraction;
    private static volatile GlassHud.Model hud = new GlassHud.Model("SAMMELT", "Seed ON / Salt ON", "Noch keine Beobachtungen", "Lifting-Daten", "0/44", "Theoretische Bits", 0);
    private static long refresh;
    private StatusDisplay() { }
    public static void tick() { if ((EgoXConfig.get().hud || SettingsAccess.editingHud()) && System.nanoTime() >= refresh) { refresh = System.nanoTime() + 250_000_000; update(); } }
    public static List<String> lines() { return lines; }
    public static void update() {
        var bits = ObservationBits.summarize(EgoXSession.structures());
        var next = new ArrayList<String>();
        next.add("EgoX  |  Seed " + (AutoCracker.paused() ? "OFF" : "ON") + "  Salt " + (AutoSaltCracker.paused() ? "OFF" : "ON"));
        next.add(String.format(Locale.ROOT, "%d Starts | ~%.1f Platzierungsbits | %d Biom-Samples", bits.starts(), bits.placementBits(), EgoXSession.biomes().size()));
        next.add(String.format(Locale.ROOT, "Lifting: %d/5 Starts | ~%.1f/44 Bits (theoretisch)", bits.liftingStarts(), bits.liftingBits()));
        next.add(EndPillarFinder.status());
        next.add(AutomaticBiomes.status());
        next.add(SessionLibrary.status());
        next.add(FeatureEvidence.status());
        next.add(DungeonEvidence.status());
        if (PillarCracker.running() && (EgoXSession.job == null || EgoXSession.job.snapshot().state() != SearchJob.State.RUNNING)) {
            var p = PillarCracker.PROGRESS.snapshot(); fraction = p.fraction(); next.add(p.phase()); addWork(next, p.checked(), p.total(), p.seconds());
        } else if (AutoCracker.running()) {
            var p = AutoCracker.PROGRESS.snapshot();
            fraction = p.fraction(); next.add(p.phase()); addWork(next, p.checked(), p.total(), p.seconds());
        } else if (EgoXSession.job != null && EgoXSession.job.snapshot().state() == SearchJob.State.RUNNING) {
            var p = EgoXSession.job.snapshot();
            fraction = p.total() == 0 ? 0 : (double)p.checked() / p.total();
            next.add(p.label()); addWork(next, p.checked(), p.total(), p.seconds());
        } else {
            fraction = Math.min(1, bits.liftingBits() / 44);
            Long seed = EgoXSession.knownSeed();
            next.add(seed == null ? (AutoCracker.paused() ? "Seed-Suche pausiert" : AutoCracker.status()) : "Welt-Seed: " + seed + " | Hash bestaetigt");
            next.add(seed == null ? "Fortschrittsbalken: Datenziel, noch keine Seed-Garantie" : ResultDatabase.status());
            if (EgoXSession.job != null) {
                var last = EgoXSession.job.snapshot();
                next.add("Letzte Suche: " + last.state() + " | " + last.checked() + "/" + last.total() + " | " + last.matches().size() + " Treffer");
            }
        }
        next.add("Finder: " + (EgoXConfig.get().finder ? "ON" : "OFF") + " | " + StructureScanner.pending() + " Chunks offen | Outlines " + (EgoXConfig.get().outlines ? "ON" : "OFF"));
        if (!NativeTemplateFinder.ready()) next.add(NativeTemplateFinder.preparation());
        lines = List.copyOf(next);
        hud = compactModel(bits);
    }
    private static GlassHud.Model compactModel(ObservationBits.Summary bits) {
        String modes = "Seed " + (AutoCracker.paused() ? "OFF" : "ON") + " / Salt " + (AutoSaltCracker.paused() ? "OFF" : "ON");
        String stats = String.format(Locale.ROOT, "%d Starts | ~%.1f Bits | %d Biome", bits.starts(), bits.placementBits(), EgoXSession.biomes().size());
        var job = EgoXSession.job == null ? null : EgoXSession.job.snapshot();
        if (PillarCracker.running() && (job == null || job.state() != SearchJob.State.RUNNING)) {
            var p = PillarCracker.PROGRESS.snapshot(); return activeModel(modes, stats, p.phase(), p.checked(), p.total(), p.seconds());
        }
        if (AutoCracker.running()) { var p = AutoCracker.PROGRESS.snapshot(); return activeModel(modes, stats, p.phase(), p.checked(), p.total(), p.seconds()); }
        if (job != null && job.state() == SearchJob.State.RUNNING) return activeModel(modes, stats, job.label(), job.checked(), job.total(), job.seconds());
        Long seed = EgoXSession.knownSeed();
        if (seed != null) return new GlassHud.Model("BESTAETIGT", modes, stats, "Seed " + seed, "64/64", "Hash OK | Biome " + AutomaticBiomes.report().state(), 1);
        String biomeDetail = EgoXConfig.get().autoBiomes && !AutomaticBiomes.report().state().equals("BEREIT")
            ? "Biom: " + AutomaticBiomes.report().state() + " | /egox db biomes" : "Theoretische Bits | /egox cracker status";
        var dungeons = DungeonEvidence.snapshot();
        if (!dungeons.isEmpty()) biomeDetail = DungeonEvidence.summarize(dungeons).compact();
        return new GlassHud.Model(AutoCracker.paused() ? "PAUSE" : "SAMMELT", modes, stats, "Lifting-Daten | " + bits.liftingStarts() + "/5 Starts",
            String.format(Locale.ROOT, "%.1f/44", bits.liftingBits()), biomeDetail, Math.min(1, bits.liftingBits() / 44));
    }
    public static GlassHud.Model activeModel(String modes, String stats, String phase, long checked, long total, double seconds) {
        double rate = checked / Math.max(.001, seconds);
        String label = phase.replace("Struktur-Seeds pruefen", "Struktur-Seeds").replace("RNG-Sonderfaelle pruefen", "RNG-Sonderfaelle")
            .replace("Welt-Seed-Hash pruefen", "Seed-Hash").replace("Untere Bits filtern", "Bit-Filter");
        return new GlassHud.Model("SUCHT", modes, stats, label, String.format(Locale.ROOT, "%.1f%%", total == 0 ? 0 : checked * 100.0 / total),
            compact(checked) + "/" + compact(total) + " | " + compact((long)rate) + "/s | ~" + (checked > 0 && seconds >= 1 ? duration((total - checked) / rate) : "?") + " Rest",
            total == 0 ? 0 : (double)checked / total);
    }
    private static String compact(long value) {
        return value >= 1_000_000_000 ? String.format(Locale.ROOT, "%.2fG", value / 1e9) : value >= 1_000_000 ? String.format(Locale.ROOT, "%.1fM", value / 1e6)
            : value >= 10_000 ? String.format(Locale.ROOT, "%.0fk", value / 1e3) : Long.toString(value);
    }
    private static void addWork(List<String> lines, long checked, long total, double seconds) {
        double rate = checked / Math.max(.001, seconds);
        lines.add(String.format(Locale.ROOT, "%.2f%% | %,d / %,d geprueft", total == 0 ? 0 : checked * 100.0 / total, checked, total));
        lines.add(String.format(Locale.ROOT, "%,.0f Kandidaten/s | Rest dieser Phase ~%s", rate, checked > 0 && seconds >= 1 ? duration((total - checked) / rate) : "unbekannt"));
    }
    public static String work(long checked, long total, double seconds) {
        double rate = checked / Math.max(.001, seconds);
        String eta = seconds >= 1 && checked > 0 ? duration((total - checked) / rate) : "wird berechnet";
        return String.format(Locale.ROOT, "%.2f%% | %,d/%,d | %,.0f/s | Rest ~%s", total == 0 ? 0 : checked * 100.0 / total, checked, total, rate, eta);
    }
    private static String duration(double seconds) {
        if (!Double.isFinite(seconds)) return "unbekannt";
        long value = Math.max(0, (long)Math.ceil(seconds));
        return value >= 3600 ? String.format(Locale.ROOT, "%dh %02dm", value / 3600, value % 3600 / 60)
            : value >= 60 ? String.format(Locale.ROOT, "%dm %02ds", value / 60, value % 60) : value + "s";
    }
    public static void render(Canvas canvas) {
        if (!EgoXConfig.get().hud || EgoXSession.context().server().isEmpty() || SettingsAccess.editingHud()) return;
        GlassHud.render(canvas, hud, EgoXConfig.get().hudStyle);
    }
    public static GlassHud.Model preview() {
        return EgoXSession.context().server().isEmpty()
            ? new GlassHud.Model("VORSCHAU", "Seed ON / Salt ON", "12 Starts | ~48.0 Bits | 256 Biome", "Lifting-Daten | 6/5 Starts", "48.0/44", "HUD frei verschieben", .7)
            : hud;
    }
}
