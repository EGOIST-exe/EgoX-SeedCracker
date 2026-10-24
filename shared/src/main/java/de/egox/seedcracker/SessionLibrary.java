package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.client.Minecraft;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** All disk work is ordered on one worker; game state is copied/applied only on the client thread. */
public final class SessionLibrary {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("EgoX library");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "EgoX-library"); t.setDaemon(true); return t; });
    private static class Store { static final ObservationStore INSTANCE = new ObservationStore(EgoXConfig.DIRECTORY.resolve("library")); }
    private static volatile LibrarySnapshot.Identity identity;
    private static volatile long generation;
    private static boolean restoring;
    private static volatile boolean dirty;
    private static volatile boolean loading;
    private static boolean readFailed;
    private static long nextSave;
    private static volatile String status = "Bibliothek wartet auf einen Server";
    private SessionLibrary() { }
    public static String status() { return status; }
    public static LibrarySnapshot.Identity identity() { return identity; }
    public static boolean loading() { return loading; }
    static boolean isCurrentRestore(LibrarySnapshot.Identity target, long token) { return target.equals(identity) && token == generation; }
    public static void open(EgoXSession.Context context) {
        var client = Minecraft.getInstance();
        generation++;
        identity = new LibrarySnapshot.Identity(client.getUser().getProfileId().toString(), context.server(), Long.toString(context.hash()), version());
        readFailed = false;
        dirty = true; nextSave = System.nanoTime() + 5_000_000_000L;
        restore(true);
        event("CONNECTION", Map.of("server",context.server(),"remoteAddress",client.getConnection().getConnection().getRemoteAddress().toString(),
            "dimension",context.dimension(),"seedHash",Long.toString(context.hash()),"minecraft",version()));
    }
    public static String version() { return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString(); }
    public static void event(String kind, Object observation) {
        if (identity == null || restoring) return;
        eventFor(identity,kind,observation);
    }
    static void eventFor(LibrarySnapshot.Identity target,String kind,Object observation) {
        if(target==null)return;
        if(target.equals(identity))dirty=true;
        IO.execute(() -> {
            try { Store.INSTANCE.append(target, kind, observation); }
            catch (Exception e) { failed("Beobachtung konnte nicht gespeichert werden", e); }
        });
    }
    public static void changed() { if (!restoring && identity != null) dirty = true; }
    public static void tick() {
        if (dirty && !loading && !readFailed && identity != null && System.nanoTime() >= nextSave) {
            nextSave = System.nanoTime() + 5_000_000_000L; save(false);
        }
    }
    public static void close() { if (identity != null && !loading && !readFailed) save(false); generation++; identity = null; dirty = false; loading = false; readFailed = false; }
    public static void save(boolean announce) {
        if (identity == null) { if (announce) EgoXSession.say("Zuerst einer Welt beitreten."); return; }
        if (loading) { if (announce) EgoXSession.say("Bibliothek wird noch geladen; vorhandene Sicherung bleibt erhalten."); return; }
        if (readFailed) { if (announce) EgoXSession.say("Vorhandene Bibliothek konnte nicht gelesen werden. Erst /egox db library restore; Sicherungen werden nicht ueberschrieben."); return; }
        var snapshot = snapshot(); dirty = false;
        IO.execute(() -> {
            try { Store.INSTANCE.save(snapshot);
                if (snapshot.identity().equals(identity)) {
                    status = "Gesichert: " + snapshot.identity().server() + " | " + snapshot.structures().size() + " Starts | " + snapshot.minedOres() + " Erz-Ereignisse";
                    if (announce) EgoXSession.say(status);
                }
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> { if (snapshot.identity().equals(identity)) dirty = true; });
                failed("Bibliothek konnte nicht gesichert werden", e);
            }
        });
    }
    private static LibrarySnapshot snapshot() {
        return new LibrarySnapshot(1, identity, Instant.now().toString(), EgoXSession.structures(), EgoXSession.biomes(), EgoXSession.salts(),
            EgoXSession.knownSeed() == null ? null : Long.toString(EgoXSession.knownSeed()), FinderMarkers.snapshot(),
            BlockEvidence.ores(), BlockEvidence.bedrock(), EndPillarFinder.heights(), AutomaticBiomes.report(), BlockEvidence.minedCount(), BlockEvidence.features(), FeatureEvidence.report(), DungeonEvidence.snapshot());
    }
    public static void restore() {
        restore(false);
    }
    private static void restore(boolean initial) {
        if (identity == null) { EgoXSession.say("Zuerst einer Welt beitreten."); return; }
        if (loading && !initial) { EgoXSession.say("Wiederherstellung laeuft bereits."); return; }
        loading = true;
        var target = identity; long token = generation;
        IO.execute(() -> {
            try {
                var saved = Store.INSTANCE.load(target);
                Minecraft.getInstance().execute(() -> {
                    if (!isCurrentRestore(target, token)) return;
                    if (saved.isEmpty()) { status = "Neue Bibliothek: " + target.server(); loading = false; readFailed = false; return; }
                    restoring = true;
                    boolean success = false;
                    try {
                        var snapshot = saved.get(); EgoXSession.restore(snapshot); FinderMarkers.restore(snapshot.markers());
                        BlockEvidence.restore(snapshot.ores(), snapshot.bedrock(), snapshot.minedOres(), initial); EndPillarFinder.restore(snapshot.pillarHeights());
                        BlockEvidence.restoreFeatures(snapshot.features()); DungeonEvidence.restore(snapshot.dungeons()); FeatureEvidence.restore(snapshot.featureAnalysis());
                        AutomaticBiomes.restore(snapshot.biomeAnalysis());
                        status = "Wiederhergestellt: " + snapshot.structures().size() + " Starts | " + snapshot.biomes().size() + " Biome | " + snapshot.minedOres() + " Erz-Ereignisse";
                        EgoXSession.say(status + ". Suchlaeufe starten mit diesen Daten neu.");
                        success = true;
                    } catch (RuntimeException error) { failed("Gespeicherte Beobachtungen konnten nicht angewendet werden", error); }
                    finally { restoring = false; dirty = success; loading = false; readFailed = !success; }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> { if (isCurrentRestore(target, token)) { loading = false; readFailed = true; } });
                failed("Bibliothek konnte nicht wiederhergestellt werden", e);
            }
        });
    }
    public static void shutdown() {
        close();
        try { IO.submit(() -> {}).get(5, TimeUnit.SECONDS); }
        catch (Exception error) { LOG.error("Final library flush did not finish", error); }
    }
    public static void list() {
        String player = Minecraft.getInstance().getUser().getProfileId().toString();
        IO.execute(() -> {
            try {
                var saved = Store.INSTANCE.list(player); EgoXSession.say("Lokale Bibliothek: " + saved.size() + " Welten fuer diesen Spieler.");
                saved.stream().limit(30).forEach(s -> EgoXSession.say(s.identity().server() + " | " + s.identity().minecraft() + " | Hash " + s.identity().seedHash()
                    + " | " + s.structures().size() + " Starts | " + s.minedOres() + " Erz-Ereignisse | " + s.savedAt()));
            } catch (Exception e) { failed("Bibliotheksliste konnte nicht gelesen werden", e); }
        });
    }
    public static void openDirectory() throws java.io.IOException {
        var directory = EgoXConfig.DIRECTORY.resolve("library"); java.nio.file.Files.createDirectories(directory);
        Platform.openDirectory(directory);
    }
    private static void failed(String message, Exception error) { status = message + ": " + error.getMessage(); LOG.error(message, error); EgoXSession.say(status); }
}
