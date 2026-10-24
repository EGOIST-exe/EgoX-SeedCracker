package de.egox.seedcracker;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Portable per-player database: append-only raw events and atomic restart snapshots. */
public final class ObservationStore {
    private static final Gson JSON = new Gson();
    private final Path root;
    public ObservationStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public static String key(LibrarySnapshot.Identity identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJson(identity).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Path directory(LibrarySnapshot.Identity identity) { return root.resolve(key(identity)); }
    private void identityFile(Path directory,LibrarySnapshot.Identity identity) throws IOException {
        Path target=directory.resolve("identity.json");if(Files.exists(target))return;
        Path temporary=directory.resolve("identity.json.tmp");Files.writeString(temporary,JSON.toJson(identity));
        try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException error){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
    }
    public void append(LibrarySnapshot.Identity identity, String kind, Object data) throws IOException {
        Path directory = directory(identity); Files.createDirectories(directory);
        identityFile(directory,identity);
        var event = new JsonObject(); event.addProperty("schema", 1); event.addProperty("kind", kind);
        event.addProperty("time", java.time.Instant.now().toString()); event.add("data", JSON.toJsonTree(data));
        Path journal = directory.resolve("observations.jsonl");
        if (Files.exists(journal) && Files.size(journal) > 0) {
            try (var channel = java.nio.channels.FileChannel.open(journal, StandardOpenOption.READ)) {
                var last = java.nio.ByteBuffer.allocate(1); channel.position(channel.size() - 1); channel.read(last);
                if (last.array()[0] != '\n') Files.writeString(journal, "\n", StandardOpenOption.APPEND);
            }
        }
        Files.writeString(journal, JSON.toJson(event) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    public void save(LibrarySnapshot snapshot) throws IOException {
        Path directory = directory(snapshot.identity()); Files.createDirectories(directory);
        identityFile(directory,snapshot.identity());
        Path target = directory.resolve("session.json"), temporary = directory.resolve("session.json.tmp");
        var json = JSON.toJsonTree(snapshot).getAsJsonObject();
        Path journal = directory.resolve("observations.jsonl");
        if (Files.exists(journal)) try (var channel = java.nio.channels.FileChannel.open(journal, StandardOpenOption.WRITE)) { channel.force(true); }
        json.addProperty("journalOffset", Files.exists(journal) ? Files.size(journal) : 0);
        byte[] bytes = JSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
        if (Files.exists(target)) Files.copy(target, directory.resolve("session.previous.json"), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    public Optional<LibrarySnapshot> load(LibrarySnapshot.Identity identity) throws IOException {
        Path directory = directory(identity);
        IOException failure = null;
        for (String file : List.of("session.json", "session.previous.json")) {
            Path path = directory.resolve(file); if (!Files.exists(path)) continue;
            try {
                if (Files.size(path) > 64_000_000) throw new IOException("Snapshot exceeds 64 MB");
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                var snapshot = JSON.fromJson(json, LibrarySnapshot.class);
                if (snapshot == null || snapshot.schema() != 1 || !identity.equals(snapshot.identity()) || snapshot.structures() == null
                    || snapshot.biomes() == null || snapshot.salts() == null || snapshot.markers() == null || snapshot.ores() == null || snapshot.bedrock() == null)
                    throw new IOException("Invalid or incompatible snapshot");
                validate(snapshot);
                if (snapshot.worldSeed() != null) Long.parseLong(snapshot.worldSeed());
                return Optional.of(replay(snapshot, directory.resolve("observations.jsonl"), json.has("journalOffset") ? json.get("journalOffset").getAsLong() : 0));
            } catch (RuntimeException | IOException e) { failure = new IOException("Invalid library snapshot: " + file, e); }
        }
        Path journal = directory.resolve("observations.jsonl");
        if (Files.exists(journal)) return Optional.of(replay(new LibrarySnapshot(1, identity, java.time.Instant.EPOCH.toString(), List.of(), List.of(), Map.of(), null,
            List.of(), List.of(), List.of(), null, null, 0, List.of(), null), journal, 0));
        if (failure != null) throw failure;
        return Optional.empty();
    }
    private LibrarySnapshot replay(LibrarySnapshot saved, Path journal, long offset) throws IOException {
        if (!Files.exists(journal)) return saved;
        if (offset < 0 || offset > Files.size(journal)) throw new IOException("Journal was truncated before the checkpoint");
        var structures = new LinkedHashSet<>(saved.structures());
        var biomes = new LinkedHashMap<String, de.egox.seedcracker.core.BiomeObservation>();
        saved.biomes().forEach(b -> biomes.put(b.key(), b));
        var salts = new HashMap<>(saved.salts());
        var ores = new LinkedHashMap<String, de.egox.seedcracker.core.BlockObservation>();
        var bedrock = new LinkedHashMap<String, de.egox.seedcracker.core.BlockObservation>();
        var features = new LinkedHashMap<String, de.egox.seedcracker.core.BlockObservation>();
        if (saved.features() != null) saved.features().forEach(o -> features.put(blockKey(o), o));
        saved.ores().forEach(o -> ores.put(blockKey(o), o)); saved.bedrock().forEach(o -> bedrock.put(blockKey(o), o));
        var markers = new LinkedHashMap<String, LibrarySnapshot.Marker>(); saved.markers().forEach(m -> markers.put(markerKey(m), m));
        long mined = saved.minedOres(); String seed = saved.worldSeed(); int[] pillars = saved.pillarHeights();
        var featureAnalysis = saved.featureAnalysis();
        var dungeons = new LinkedHashMap<String, de.egox.seedcracker.core.DungeonObservation>();
        if (saved.dungeons() != null) saved.dungeons().forEach(d -> dungeons.put(d.key(), d));
        try (var input = Files.newInputStream(journal)) {
            input.skipNBytes(offset);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        var event = JsonParser.parseString(line).getAsJsonObject(); var data = event.get("data");
                        if (data == null || data.isJsonNull()) continue;
                        switch (event.get("kind").getAsString()) {
                            case "STRUCTURE_START" -> { var s=JSON.fromJson(data,de.egox.seedcracker.core.StructureObservation.class); if (validStructure(s)) structures.add(s); }
                            case "BIOME" -> { var b = JSON.fromJson(data, de.egox.seedcracker.core.BiomeObservation.class); biomes.put(b.key(), b); de.egox.seedcracker.core.BiomeEvidence.trimDimensions(biomes, Math.max(2048, saved.biomes().size())); }
                            case "SALT" -> { var s = JSON.fromJson(data, EgoXSession.SaltSetting.class); if (s.type()!=null && s.salt()>=Integer.MIN_VALUE && s.salt()<=Integer.MAX_VALUE) salts.put(s.type(), s.salt()); }
                            case "ORE_MINED", "ORE_SERVER_RESULT" -> { var o = JSON.fromJson(data, de.egox.seedcracker.core.BlockObservation.class); ores.put(blockKey(o), o); trim(ores, 4096); if (event.get("kind").getAsString().equals("ORE_MINED")) mined++; }
                            case "BEDROCK" -> { var o = JSON.fromJson(data, de.egox.seedcracker.core.BlockObservation.class); bedrock.put(blockKey(o), o); trim(bedrock, 2048); }
                            case "FEATURE_BLOCK" -> { var o = JSON.fromJson(data, de.egox.seedcracker.core.BlockObservation.class); features.put(blockKey(o), o); trim(features, 8192); }
                            case "DUNGEON" -> { var d = JSON.fromJson(data, de.egox.seedcracker.core.DungeonObservation.class); dungeons.put(d.key(), d); trim(dungeons, 1024); }
                            case "FEATURE_ANALYSIS" -> featureAnalysis = JSON.fromJson(data, FeatureEvidence.Report.class);
                            case "FEATURE_CHECK" -> featureAnalysis = JSON.fromJson(data.getAsJsonObject().get("report"), FeatureEvidence.Report.class);
                            case "MARKER" -> { var m = JSON.fromJson(data, LibrarySnapshot.Marker.class); markers.put(markerKey(m), m); trim(markers, 4096); }
                            case "PILLARS" -> pillars = JSON.fromJson(data, int[].class);
                            case "WORLD_SEED" -> { String candidate = data.getAsString(); Long.parseLong(candidate); seed = candidate; }
                            default -> { }
                        }
                    } catch (RuntimeException ignored) { /* A torn final event must not discard preceding observations. */ }
                }
            }
        }
        return new LibrarySnapshot(1, saved.identity(), saved.savedAt(), List.copyOf(structures), List.copyOf(biomes.values()), Map.copyOf(salts), seed,
            List.copyOf(markers.values()), List.copyOf(ores.values()), List.copyOf(bedrock.values()), pillars, saved.biomeAnalysis(), mined, List.copyOf(features.values()), featureAnalysis, List.copyOf(dungeons.values()));
    }
    private static String blockKey(de.egox.seedcracker.core.BlockObservation b) { return b.dimension() + ":" + b.x() + ":" + b.y() + ":" + b.z(); }
    private static String markerKey(LibrarySnapshot.Marker m) {
        if(m.type().equals("egox:geode"))return m.dimension()+":geode-zone:"+(int)Math.floor((m.minX()+m.maxX())/64)+":"+(int)Math.floor((m.minY()+m.maxY())/64)+":"+(int)Math.floor((m.minZ()+m.maxZ())/64);
        return m.dimension() + ":" + m.type() + ":" + (m.blockX() == null ? m.chunkX() + ":" + m.chunkZ() : m.blockX() + ":" + m.blockY() + ":" + m.blockZ());
    }
    private static void trim(Map<?, ?> map, int limit) { while (map.size() > limit) map.remove(map.keySet().iterator().next()); }
    private static boolean validStructure(de.egox.seedcracker.core.StructureObservation s) {
        return s!=null && s.dimension()!=null && s.placement()!=null && s.placement().id()!=null && s.placement().spread()!=null
            && s.placement().spacing()>0 && s.placement().separation()>=0 && s.placement().separation()<s.placement().spacing();
    }
    private static void validate(LibrarySnapshot s) throws IOException {
        if (s.dungeons() != null && s.dungeons().stream().anyMatch(Objects::isNull)) throw new IOException("Invalid dungeon observation");
        if (s.savedAt()==null || s.minedOres()<0 || s.structures().stream().anyMatch(o -> !validStructure(o))
            || s.biomes().stream().anyMatch(b -> b==null||b.biome()==null) || s.salts().entrySet().stream().anyMatch(e -> e.getKey()==null||e.getValue()==null)) throw new IOException("Invalid observations in snapshot");
        for (var blocks:List.of(s.ores(),s.bedrock(),s.features()==null?List.<de.egox.seedcracker.core.BlockObservation>of():s.features()))
            if (blocks.stream().anyMatch(b -> b==null||b.dimension()==null||b.block()==null||b.source()==null||b.time()==null)) throw new IOException("Invalid block observation");
        for(var m:s.markers()) if(m==null||m.type()==null||m.dimension()==null||m.source()==null||!Double.isFinite(m.minX())||!Double.isFinite(m.minY())||!Double.isFinite(m.minZ())
            ||!Double.isFinite(m.maxX())||!Double.isFinite(m.maxY())||!Double.isFinite(m.maxZ())||m.minX()>m.maxX()||m.minY()>m.maxY()||m.minZ()>m.maxZ()
            ||m.blockX()!=null&&(m.blockY()==null||m.blockZ()==null)) throw new IOException("Invalid marker geometry");
        if(s.biomeAnalysis()!=null&&(s.biomeAnalysis().state()==null||s.biomeAnalysis().candidates()==null||s.biomeAnalysis().detail()==null)) throw new IOException("Invalid biome report");
        if(s.featureAnalysis()!=null&&(s.featureAnalysis().state()==null||s.featureAnalysis().candidates()==null||s.featureAnalysis().detail()==null)) throw new IOException("Invalid feature report");
    }
    public List<LibrarySnapshot> list(String player) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        var snapshots = new ArrayList<LibrarySnapshot>();
        try (var directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                Path file = directory.resolve("session.json");
                if (!Files.isRegularFile(file)) {
                    Path metadata=directory.resolve("identity.json");
                    if(Files.isRegularFile(metadata))try{
                        var id=JSON.fromJson(Files.readString(metadata),LibrarySnapshot.Identity.class);
                        if(id!=null&&player.equals(id.player())&&directory.getFileName().toString().equals(key(id)))load(id).ifPresent(snapshots::add);
                    }catch(RuntimeException|IOException ignored){}
                    continue;
                }
                if(Files.size(file)>64_000_000)continue;
                try {
                    var snapshot = JSON.fromJson(Files.readString(file), LibrarySnapshot.class);
                    if (snapshot != null && snapshot.identity() != null && snapshot.savedAt()!=null && snapshot.structures()!=null && player.equals(snapshot.identity().player())) snapshots.add(snapshot);
                } catch (RuntimeException ignored) { }
            }
        }
        snapshots.sort(Comparator.comparing(LibrarySnapshot::savedAt).reversed()); return List.copyOf(snapshots);
    }
}
