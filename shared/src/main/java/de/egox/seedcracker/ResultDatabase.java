package de.egox.seedcracker;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Durable local history/outbox. A failed upload never discards a cracked seed. */
public final class ResultDatabase {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("EgoX database");
    private static final ScheduledExecutorService IO = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EgoX-database"); t.setDaemon(true); return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String, ResultRecord> history = new LinkedHashMap<>();
    private static final Map<String, ResultRecord> outbox = new LinkedHashMap<>();
    private static final Set<String> acknowledged = new HashSet<>();
    private static volatile String lastStatus = "Noch kein Upload";
    private static volatile int pending;
    private static final Path HISTORY = EgoXConfig.DIRECTORY.resolve("results.jsonl");
    private static final Path OUTBOX = EgoXConfig.DIRECTORY.resolve("outbox.json");
    private static final Path ACKNOWLEDGED = EgoXConfig.DIRECTORY.resolve("uploaded.json");

    public static void start() {
        IO.execute(() -> {
            try {
                if (Files.exists(HISTORY)) for (String line : Files.readAllLines(HISTORY)) {
                    if (line.isBlank()) continue;
                    ResultRecord record = EgoXConfig.JSON.fromJson(line, ResultRecord.class);
                    history.put(record.id(), record);
                }
                if (Files.exists(ACKNOWLEDGED)) {
                    String[] ids = EgoXConfig.JSON.fromJson(Files.readString(ACKNOWLEDGED), String[].class);
                    if (ids != null) acknowledged.addAll(Arrays.asList(ids));
                }
                // Reconstruct from the append-only journal: a crash between journal and outbox
                // writes must never lose a pending upload.
                for (var record : history.values()) if (!LibrarySnapshot.singleplayer(record.server()) && !acknowledged.contains(record.id())) outbox.put(record.id(), record);
                pending = outbox.size();
            } catch (Exception e) { lastStatus = "Lokale DB konnte nicht gelesen werden"; LOG.error(lastStatus, e); }
        });
        IO.scheduleWithFixedDelay(ResultDatabase::flush, 10, 60, TimeUnit.SECONDS);
    }
    public static String id(String server, String dimension, long seed, String version) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (server + "\n" + dimension + "\n" + seed + "\n" + version).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static void record(ResultRecord record) {
        IO.execute(() -> {
            try {
                if (history.containsKey(record.id())) return;
                Files.createDirectories(HISTORY.getParent());
                // One compact JSON object per line; pretty printing would break JSONL replay.
                Files.writeString(HISTORY, new com.google.gson.Gson().toJson(record) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                history.put(record.id(), record);
                EgoXSession.say("Bestaetigten Welt-Seed lokal gespeichert.");
                if (!LibrarySnapshot.singleplayer(record.server())) {
                    outbox.put(record.id(), record);
                    saveOutbox();
                    flush();
                }
            } catch (Exception e) { lastStatus = "Speichern fehlgeschlagen: " + e.getClass().getSimpleName(); LOG.error(lastStatus, e); }
        });
    }
    private static void saveOutbox() throws java.io.IOException {
        Path temp = OUTBOX.resolveSibling("outbox.json.tmp");
        Files.writeString(temp, EgoXConfig.JSON.toJson(outbox.values()));
        try { Files.move(temp, OUTBOX, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, OUTBOX, StandardCopyOption.REPLACE_EXISTING); }
        pending = outbox.size();
    }
    public static void retry() { IO.execute(ResultDatabase::flush); }
    public static void recordSalt(EgoXSession.Context context, long seed, String type, long salt, int samples) {
        IO.execute(() -> {
            var result = new LinkedHashMap<String, Object>();
            result.put("server", context.server()); result.put("dimension", context.dimension());
            result.put("worldSeed", Long.toString(seed)); result.put("type", type); result.put("salt", Long.toString(salt));
            result.put("samples", samples); result.put("createdAt", java.time.Instant.now().toString());
            try {
                Files.writeString(EgoXConfig.DIRECTORY.resolve("salt-results.jsonl"), new com.google.gson.Gson().toJson(result) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (java.io.IOException e) { lastStatus = "Salt konnte nicht gespeichert werden"; LOG.error(lastStatus, e); EgoXSession.say(lastStatus); }
        });
    }
    private static void flush() {
        var config = EgoXConfig.get();
        if (!config.autoUpload || config.uploadUrl.isBlank() || outbox.isEmpty()) return;
        try {
            URI endpoint = URI.create(config.uploadUrl);
            if (!"https".equals(endpoint.getScheme()) || !"script.google.com".equals(endpoint.getHost())
                || !endpoint.getPath().startsWith("/macros/s/") || !endpoint.getPath().endsWith("/exec"))
                throw new IllegalArgumentException("Apps-Script-URL muss https://script.google.com/macros/s/.../exec sein");
            for (var record : new ArrayList<>(outbox.values())) {
                var payload = new com.google.gson.JsonObject();
                payload.addProperty("token", config.uploadToken);
                payload.add("record", EgoXConfig.JSON.toJsonTree(record));
                HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json; charset=utf-8").header("User-Agent", "EgoX-SeedCracker/0.6.1")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
                var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                var body = JsonParser.parseString(response.body()).getAsJsonObject();
                if (response.statusCode() != 200 || !body.has("ok") || !body.get("ok").getAsBoolean()
                    || !body.has("id") || !body.get("id").getAsString().equals(record.id()))
                    throw new java.io.IOException("Endpoint hat den Datensatz nicht bestaetigt");
                acknowledged.add(record.id());
                atomicJson(ACKNOWLEDGED, acknowledged);
                outbox.remove(record.id());
                saveOutbox();
                lastStatus = "Upload erfolgreich";
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); lastStatus = "Upload unterbrochen"; }
        catch (Exception e) { lastStatus = "Upload fehlgeschlagen; wird erneut versucht (" + e.getClass().getSimpleName() + ")"; LOG.warn(lastStatus); }
    }
    public static String status() { return lastStatus + "; ausstehend: " + pending; }
    public static void restore(EgoXSession.Context context) {
        var addresses=new HashSet<String>();addresses.add(LibrarySnapshot.address(context.server()));
        if(LibrarySnapshot.singleplayer(context.server()))addresses.add("singleplayer");
        var client=net.minecraft.client.Minecraft.getInstance();
        if(client.getConnection()!=null)addresses.add(LibrarySnapshot.address(client.getConnection().getConnection().getRemoteAddress().toString()));
        IO.execute(() -> {
            if (context.hash() == null) return;
            for (var record : history.values()) {
                if (!addresses.contains(LibrarySnapshot.address(record.server())) || !record.dimension().equals(context.dimension())) continue;
                long seed = Long.parseLong(record.worldSeed());
                if (de.egox.seedcracker.core.SeedHash.of(seed) == context.hash() && EgoXSession.context().epoch() == context.epoch()) {
                    EgoXSession.acceptWorldSeed(seed, "LOCAL_DATABASE"); return;
                }
            }
        });
    }
    private static void atomicJson(Path target, Object value) throws java.io.IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, EgoXConfig.JSON.toJson(value));
        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
