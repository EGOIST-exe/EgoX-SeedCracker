package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class EgoXSession {
    public record Context(long epoch, String server, String dimension, Long hash) { }
    private static volatile Context context = new Context(0, "", "", null);
    private static final AtomicLong EPOCH = new AtomicLong();
    private static final Set<StructureObservation> structures = ConcurrentHashMap.newKeySet();
    private static final LinkedHashMap<String, BiomeObservation> biomes = new LinkedHashMap<>();
    private static final Map<String, Long> salts = new ConcurrentHashMap<>();
    private static volatile Long knownWorldSeed;
    public static volatile SearchJob job;

    public static void connect(long hash) {
        Minecraft client = Minecraft.getInstance();
        String server = client.getConnection().getConnection().isMemoryConnection()
            ? "singleplayer:" + (client.getSingleplayerServer() == null ? "unknown" : client.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize())
            : client.getCurrentServer() == null ? client.getConnection().getConnection().getRemoteAddress().toString() : client.getCurrentServer().ip;
        String dimension = client.level.dimension().identifier().toString();
        Context previous = context;
        if (server.equals(previous.server()) && dimension.equals(previous.dimension()) && Objects.equals(hash, previous.hash())) return;
        if (server.equals(previous.server()) && Objects.equals(hash, previous.hash())) {
            if (job != null) job.cancel();
            AutoCracker.stop();
            context = new Context(EPOCH.incrementAndGet(), server, dimension, hash);
            SessionLibrary.event("DIMENSION", context);
            StructureScanner.clear();
            AutoCracker.newSession(); AutoSaltCracker.newSession(); AutoCracker.observationsChanged();
            return;
        }
        clear();
        context = new Context(EPOCH.incrementAndGet(), server, dimension, hash);
        AutoCracker.newSession();
        AutoSaltCracker.newSession();
        SessionLibrary.open(context);
        ResultDatabase.restore(context);
    }
    public static synchronized void clear() {
        SessionLibrary.close();
        if (job != null) job.cancel();
        AutoCracker.stop();
        AutoSaltCracker.pause();
        PillarCracker.reset(); EndPillarFinder.clear();
        structures.clear(); biomes.clear(); salts.clear(); knownWorldSeed = null;
        AutomaticBiomes.reset();
        BlockEvidence.clear();
        DungeonEvidence.clear();
        FeatureEvidence.reset();
        FinderMarkers.clear(); StructureScanner.clear();
        context = new Context(EPOCH.incrementAndGet(), "", "", null);
    }
    public static Context context() { return context; }
    public static Long knownSeed() { return knownWorldSeed; }
    public static List<StructureObservation> structures() {
        return structures.stream().sorted(Comparator.comparing((StructureObservation s) -> s.placement().id())
            .thenComparingInt(StructureObservation::chunkX).thenComparingInt(StructureObservation::chunkZ)).toList();
    }
    public static synchronized List<BiomeObservation> biomes() {
        // Different biomes first: rejecting wrong candidates early saves native-noise evaluations.
        var counts = new HashMap<String, Integer>();
        biomes.values().forEach(b -> counts.merge(b.biome(), 1, Integer::sum));
        return biomes.values().stream().sorted(Comparator.comparingInt(b -> counts.get(b.biome()))).toList();
    }
    public static boolean addStructure(String id, int chunkX, int chunkZ) {
        if (context.server().isEmpty()) throw new IllegalStateException("Zuerst einer Welt beitreten");
        Placement profile = NativeProfiles.get(id);
        int bound = profile.spacing() - profile.separation();
        if (Math.floorMod(chunkX, profile.spacing()) >= bound || Math.floorMod(chunkZ, profile.spacing()) >= bound)
            throw new IllegalArgumentException("Start liegt ausserhalb des moeglichen Platzierungsbereichs fuer " + id);
        for (var old : structures) {
            if (old.placement().id().equals(profile.id()) && Math.floorDiv(old.chunkX(), profile.spacing()) == Math.floorDiv(chunkX, profile.spacing())
                && Math.floorDiv(old.chunkZ(), profile.spacing()) == Math.floorDiv(chunkZ, profile.spacing())) {
                if (old.chunkX() != chunkX || old.chunkZ() != chunkZ) {
                    SessionLibrary.event("STRUCTURE_CONFLICT",new StructureObservation(profile,chunkX,chunkZ,context.dimension()));
                    say("Widerspruechliche Starts in derselben Strukturregion: " + id + ". Beobachtungen pruefen.");
                }
                return false;
            }
        }
        var observation = new StructureObservation(profile, chunkX, chunkZ, context.dimension());
        if (structures.add(observation)) { SessionLibrary.event("STRUCTURE_START", observation); AutoCracker.observationsChanged(); return true; }
        return false;
    }
    public static synchronized void observeChunk(Level world, int chunkX, int chunkZ) {
        if (!EgoXConfig.get().collectBiomes || !world.dimension().equals(Level.OVERWORLD) && !world.dimension().equals(Level.NETHER)) return;
        for (int y : new int[]{0, 16, 32}) {
            int x = chunkX * 4 + 2, z = chunkZ * 4 + 2;
            var holder = world.getNoiseBiome(x, y, z);
            holder.unwrapKey().ifPresent(key -> {
                String id = key.identifier().toString();
                if (!id.equals("minecraft:the_void")) {
                    var observation = new BiomeObservation(x, y, z, id, world.dimension().identifier().toString());
                    if (biomes.putIfAbsent(observation.key(), observation) == null) SessionLibrary.event("BIOME", observation);
                    BiomeEvidence.trimDimensions(biomes, EgoXConfig.get().maxBiomeSamples);
                }
            });
        }
    }
    public static long salt(Placement p) { return salts.getOrDefault(p.id(), (long)p.vanillaSalt()); }
    public record SaltSetting(String type, long salt) { }
    public static void setSalt(String id, long salt) {
        String type = NativeProfiles.get(id).id(); salts.put(type, salt); SessionLibrary.event("SALT", new SaltSetting(type, salt)); AutoCracker.observationsChanged();
    }
    public static synchronized void restore(LibrarySnapshot snapshot) {
        for (var observation : snapshot.structures()) {
            try { if (NativeProfiles.get(observation.placement().id()).equals(observation.placement())) structures.add(observation); }
            catch (IllegalArgumentException ignored) { }
        }
        snapshot.biomes().forEach(b -> biomes.putIfAbsent(b.key(), b));
        BiomeEvidence.trimDimensions(biomes, EgoXConfig.get().maxBiomeSamples);
        snapshot.salts().forEach((key, salt) -> { if (salt >= Integer.MIN_VALUE && salt <= Integer.MAX_VALUE) salts.putIfAbsent(key, salt); });
        if (knownWorldSeed == null && snapshot.worldSeed() != null) {
            long seed = Long.parseLong(snapshot.worldSeed());
            if (context.hash() != null && SeedHash.of(seed) == context.hash()) knownWorldSeed = seed;
        }
        AutoCracker.observationsChanged();
    }
    public static Map<String, Long> salts() { return Map.copyOf(salts); }
    public static void say(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) Platform.message("[EgoX] " + message);
        });
    }
    public static void acceptWorldSeed(long seed, String source) {
        long epoch = context.epoch();
        Minecraft.getInstance().execute(() -> {
            Context ctx = context;
            if (ctx.epoch() != epoch) return;
            if (ctx.server().isEmpty() || Objects.equals(knownWorldSeed, seed)) return;
            if (ctx.hash() != null && SeedHash.of(seed) != ctx.hash()) {
                say("Kandidat " + seed + " verworfen: stimmt nicht mit dem Server-Seed-Hash ueberein.");
                return;
            }
            if (ctx.hash() == null) { say("Kandidat " + seed + ": kein Seed-Hash zur Bestaetigung vorhanden."); return; }
            knownWorldSeed = seed;
            SessionLibrary.event("WORLD_SEED", Long.toString(seed));
            Map<String, String> saltStrings = new TreeMap<>();
            salts.forEach((key, value) -> saltStrings.put(key, Long.toString(value)));
            String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
            ResultDatabase.record(new ResultRecord(ResultDatabase.id(ctx.server(), ctx.dimension(), seed, version), Instant.now().toString(),
                ctx.server(), ctx.dimension(), version, Long.toString(seed), Long.toString(seed & LegacyRandom.MASK),
                Long.toString(ctx.hash()), "HASH_VERIFIED", biomes().size(), structures.size(), saltStrings, source));
            say("Welt-Seed bestaetigt: " + seed + " (Server-Hash stimmt). Speicherung eingeplant.");
        });
    }
}
