package de.egox.seedcracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import de.egox.seedcracker.core.*;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import java.util.function.Consumer;

import static de.egox.seedcracker.CommandBuilders.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.LongArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.BoolArgumentType.*;

public final class EgoXCommands {
    private EgoXCommands() { }
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("egox")
            .executes(c -> run(() -> say("/egox settings | cracker seed|salt ON|OFF | cracker status | db bits|data|export | render outlines|hud|labels ON|OFF | finder list | discord")))
            .then(literal("discord").executes(c -> run(Platform::openDiscord)))
            .then(literal("settings").executes(c -> run(SettingsAccess::requestOpen)))
            .then(literal("gui").executes(c -> run(SettingsAccess::requestOpen)))
            .then(literal("status").executes(c -> run(EgoXCommands::status)))
            .then(literal("cracker")
                .executes(c -> run(EgoXCommands::status))
                .then(literal("structures")
                    .then(literal("list").executes(c -> run(() -> CrackingStructures.types().forEach(id->say(id+": "+(CrackingStructures.enabled(id)?"ON":"OFF"))))))
                    .then(argument("type",new StructureIdArgument()).suggests((c,b)->{CrackingStructures.types().forEach(b::suggest);return b.buildFuture();})
                        .then(argument("state",new OnOffArgument()).executes(c->run(()->{
                            String id=CrackingStructures.resolve(getString(c,"type"));boolean enabled=c.getArgument("state",Boolean.class);
                            CrackingStructures.set(id,enabled);say("Cracken mit "+id+": "+(enabled?"ON":"OFF")+"; Rohdaten und Outlines bleiben erhalten.");
                        })))))
                .then(toggle("nether",CrackerControl::netherBedrock)
                    .then(literal("status").executes(c->run(AutoBedrockCracker::describe)))
                    .then(literal("retry").executes(c->run(AutoBedrockCracker::retry))))
                .then(toggle("seed", CrackerControl::seed).then(literal("status").executes(c -> run(EgoXCommands::status))))
                .then(toggle("salt", CrackerControl::salt).then(literal("status").executes(c -> run(EgoXCommands::status))))
                .then(toggle("dungeon", CrackerControl::dungeon)
                    .then(literal("status").executes(c -> run(FeatureEvidence::describe)))
                    .then(literal("search").then(argument("min", longArg()).then(argument("max", longArg()).executes(c -> run(() -> crackDungeons(c)))))))
                .then(toggle("ore", CrackerControl::ore)
                    .then(literal("status").executes(c -> run(FeatureEvidence::describe)))
                    .then(literal("search").then(argument("min", longArg()).then(argument("max", longArg()).executes(c -> run(() -> crackEvidence(c, true, false))))))
                    .then(literal("feature").then(argument("min", longArg()).then(argument("max", longArg()).executes(c -> run(() -> crackEvidence(c, true, true)))))))
                .then(toggle("bedrock", CrackerControl::bedrock)
                    .then(literal("status").executes(c -> run(()->{FeatureEvidence.describe();AutoBedrockCracker.describe();})))
                    .then(literal("search").then(argument("min", longArg()).then(argument("max", longArg()).executes(c -> run(() -> crackEvidence(c, false, false)))))))
                .then(toggle("geode",CrackerControl::geode)
                    .then(literal("status").executes(c->run(FeatureEvidence::describe)))
                    .then(literal("search").then(argument("min",longArg()).then(argument("max",longArg()).executes(c->run(()->crackGeodes(c)))))))
                .then(literal("status").executes(c -> run(EgoXCommands::status))))
            .then(literal("render")
                .then(toggle("outlines", enabled -> { EgoXConfig.get().outlines = enabled; EgoXConfig.save(); say("Outlines: " + (enabled ? "ON" : "OFF")); })
                    .then(literal("list").executes(c -> run(EgoXCommands::outlineTypes)))
                    .then(argument("type", new StructureIdArgument()).suggests((c, b) -> {
                        String remaining = b.getRemaining();
                        OutlineVisibility.types().stream().filter(type -> type.startsWith(remaining) || type.substring(type.indexOf(':') + 1).startsWith(remaining)).forEach(b::suggest);
                        return b.buildFuture();
                    }).then(argument("enabled", new OnOffArgument()).executes(c -> run(() -> {
                        String type = OutlineVisibility.resolve(getString(c, "type"));
                        boolean enabled = c.getArgument("enabled", Boolean.class);
                        OutlineVisibility.set(EgoXConfig.get().disabledOutlineTypes, type, enabled);
                        EgoXConfig.save();
                        if (enabled && type.equals(OutlineVisibility.GEODE)) StructureScanner.rescan();
                        say("Outline " + type + ": " + (enabled ? "ON" : "OFF"));
                    })))))
                .then(toggle("hud", enabled -> { EgoXConfig.get().hud = enabled; EgoXConfig.save(); say("HUD: " + enabled); }))
                .then(toggle("labels", enabled -> { EgoXConfig.get().outlineLabels = enabled; EgoXConfig.save(); say("Outline-Beschriftung: " + enabled); }))
                .then(toggle("features", enabled -> { EgoXConfig.get().specialBlocks = enabled; EgoXConfig.save(); if (enabled) StructureScanner.rescan(); say("Spawner/Geoden-Markierungen: " + (enabled ? "ON" : "OFF")); }))
                .then(literal("range").then(argument("blocks", integer(16, 512)).executes(c -> run(() -> {
                    EgoXConfig.get().outlineRange = getInteger(c, "blocks"); EgoXConfig.save(); say("Outline-Reichweite gespeichert.");
                })))))
            .then(toggle("finder", enabled -> { EgoXConfig.get().finder = enabled; EgoXConfig.save(); say("Finder: " + enabled); })
                .then(literal("list").executes(c -> run(() -> StructureScanner.describe().forEach(EgoXCommands::say))))
                .then(literal("rescan").executes(c -> run(() -> say("Geladene Chunks eingeplant: " + StructureScanner.rescan())))))
            .then(literal("stop").executes(c -> run(() -> { if (EgoXSession.job != null) EgoXSession.job.cancel(); AutoCracker.stop(); PillarCracker.stop(); AutoBedrockCracker.stop(); AutoSaltCracker.pause(); say("Suchabbruch angefordert. Weiter mit /egox resume."); })))
            .then(literal("resume").executes(c -> run(() -> { CrackerControl.seed(true); CrackerControl.salt(true); })))
            .then(literal("reload").executes(c -> run(() -> { EgoXConfig.reload(); say("Konfiguration geladen."); })))
            .then(literal("export").executes(c -> run(EgoXCommands::export)))
            .then(literal("observe").then(argument("type", new StructureIdArgument()).suggests((c, b) -> {
                NativeProfiles.all().keySet().forEach(b::suggest); return b.buildFuture();
            }).then(argument("chunkX", integer(-1875000, 1875000)).then(argument("chunkZ", integer(-1875000, 1875000)).executes(c -> run(() -> {
                EgoXSession.addStructure(getString(c, "type"), getInteger(c, "chunkX"), getInteger(c, "chunkZ")); say("Strukturstart gespeichert (Chunk-Koordinaten).");
            }))))))
            .then(literal("diagnose").then(argument("worldSeed", longArg()).executes(c -> run(() -> diagnose(getLong(c, "worldSeed"))))))
            .then(literal("salt")
                .then(literal("set").then(argument("type", new StructureIdArgument()).then(argument("salt", longArg(Integer.MIN_VALUE, Integer.MAX_VALUE)).executes(c -> run(() -> {
                    ensureIdle(); EgoXSession.setSalt(getString(c, "type"), getLong(c, "salt")); say("Salt fuer diese Sitzung gesetzt. Struktur-Suche verwendet den Override.");
                })))))
                .then(literal("crack").then(argument("type", new StructureIdArgument()).then(argument("worldSeed", longArg())
                    .then(argument("min", longArg(Integer.MIN_VALUE, Integer.MAX_VALUE)).then(argument("max", longArg(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        .executes(c -> run(() -> crackSalt(c)))))))))
            .then(literal("crack")
                .then(literal("structures").then(argument("min", longArg(0, LegacyRandom.MASK)).then(argument("max", longArg(0, LegacyRandom.MASK)).executes(c -> run(() -> crackStructures(c))))))
                .then(literal("lift").then(argument("structureSeed", longArg(0, LegacyRandom.MASK)).executes(c -> run(() -> lift(getLong(c, "structureSeed")))))))
            .then(toggle("biomes", CrackerControl::biomes)
                .then(literal("status").executes(c -> run(() -> AutomaticBiomes.describe().forEach(EgoXCommands::say))))
                .then(literal("preset")
                    .then(biomePreset("overworld")).then(biomePreset("large_biomes")).then(biomePreset("amplified")))
                .then(literal("test").then(argument("worldSeed", longArg()).executes(c -> run(() -> testBiomes(getLong(c, "worldSeed"))))))
                .then(literal("search").then(argument("min", longArg()).then(argument("max", longArg()).executes(c -> run(() -> biomeSearch(c)))))))
            .then(literal("feature").then(literal("crack").then(argument("file", word()).then(argument("min", longArg()).then(argument("max", longArg())
                .executes(c -> run(() -> crackFeature(c))))))))
            .then(literal("db")
                .executes(c -> run(() -> Platform.openSheet(EgoXConfig.get().sheetUrl)))
                .then(literal("status").executes(c -> run(() -> say(ResultDatabase.status() + (EgoXConfig.get().uploadUrl.isBlank() ? "; Apps-Script-Endpunkt fehlt" : "")))))
                .then(literal("bits").executes(c -> run(EgoXCommands::bits)))
                .then(literal("biomes").executes(c -> run(() -> AutomaticBiomes.describe().forEach(EgoXCommands::say))))
                .then(literal("ores").executes(c -> run(BlockEvidence::describe)))
                .then(literal("library")
                    .executes(c -> run(() -> say(SessionLibrary.status())))
                    .then(literal("list").executes(c -> run(SessionLibrary::list)))
                    .then(literal("open").executes(c -> run(SessionLibrary::openDirectory)))
                    .then(literal("save").executes(c -> run(() -> SessionLibrary.save(true))))
                    .then(literal("restore").executes(c -> run(() -> { ensureIdle(); SessionLibrary.restore(); }))))
                .then(literal("seeds").executes(c -> run(() -> StructureSeedReports.all().forEach(EgoXCommands::say)))
                    .then(argument("type", new StructureIdArgument()).suggests((c, b) -> { NativeProfiles.all().keySet().forEach(b::suggest); return b.buildFuture(); })
                        .executes(c -> run(() -> StructureSeedReports.one(getString(c, "type")).forEach(EgoXCommands::say)))))
                .then(literal("data").executes(c -> run(EgoXCommands::data)))
                .then(literal("export").executes(c -> run(EgoXCommands::export)))
                .then(toggle("upload", enabled -> { EgoXConfig.get().autoUpload = enabled; EgoXConfig.save(); say("DB-Upload: " + enabled); }))
                .then(literal("retry").executes(c -> run(() -> { ResultDatabase.retry(); say("Upload-Versuch eingeplant."); })))
                .then(literal("auto").then(argument("enabled", bool()).executes(c -> run(() -> { EgoXConfig.get().autoUpload = getBool(c, "enabled"); EgoXConfig.save(); say("Auto-Upload: " + EgoXConfig.get().autoUpload); }))))
            )
        );
        var root = dispatcher.getRoot().getChild("egox");
        var seed = root.getChild("cracker").getChild("seed");
        root.getChild("crack").getChildren().forEach(seed::addChild);
        seed.addChild(root.getChild("biomes")); seed.addChild(root.getChild("diagnose"));
        root.getChild("cracker").addChild(root.getChild("biomes"));
        var salt = root.getChild("cracker").getChild("salt");
        root.getChild("salt").getChildren().forEach(salt::addChild);
        salt.addChild(root.getChild("feature"));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, Consumer<Boolean> action) {
        return literal(name).then(argument("state", new OnOffArgument()).executes(c -> run(() -> action.accept(c.getArgument("state", Boolean.class)))));
    }
    private static int run(Checked action) {
        try { action.run(); return 1; }
        catch (Exception e) { say("Fehler: " + e.getMessage()); return 0; }
    }
    private interface Checked { void run() throws Exception; }
    private static void say(String text) { EgoXSession.say(text); }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> biomePreset(String preset) {
        return literal(preset).executes(c -> run(() -> {
            ensureIdle(); EgoXConfig.get().biomePreset = preset; EgoXConfig.save(); AutoCracker.retry();
            say("Biom-Preset: " + preset);
        }));
    }
    private static void outlineTypes() {
        var config = EgoXConfig.get();
        say("Outlines gesamt: " + (config.outlines ? "ON" : "OFF") + " | Zusatzmarkierungen: " + (config.specialBlocks ? "ON" : "OFF"));
        OutlineVisibility.types().forEach(type -> say(type + ": " + (OutlineVisibility.enabled(config.disabledOutlineTypes, type) ? "ON" : "OFF")));
        say("/egox render outlines <Typ> ON|OFF. Ausblenden erhaelt die Struktur-Beobachtungen fuer den Cracker.");
    }
    private static void ensureIdle() {
        if (EgoXSession.job != null && EgoXSession.job.snapshot().state() == SearchJob.State.RUNNING)
            throw new IllegalStateException("Eine Suche laeuft bereits. /egox stop");
        if (AutoCracker.running() || AutoBedrockCracker.running() || PillarCracker.running()) throw new IllegalStateException("Automatische Suche laeuft. Zuerst /egox stop und Abschluss abwarten.");
    }
    private static void launch(String label, long start, long count, Supplier<LongPredicate> factory, java.util.function.LongUnaryOperator decode, boolean worldSeeds) {
        ensureIdle();
        long epoch = EgoXSession.context().epoch();
        SearchJob job = new SearchJob(label, start, count, EgoXConfig.get().workers, 256, factory);
        var libraryTarget=SessionLibrary.identity();
        EgoXSession.job = job;
        say(label + " gestartet: " + count + " Kandidaten. Fortschritt: /egox status; Abbruch: /egox stop");
        job.completion().thenRun(() -> {
            var snapshot = job.snapshot();
            SessionLibrary.eventFor(libraryTarget,"SEARCH_RESULT",Map.of("label",snapshot.label(),"state",snapshot.state().name(),"checked",snapshot.checked(),"total",snapshot.total(),
                "matches",snapshot.matches().stream().mapToLong(decode::applyAsLong).mapToObj(Long::toString).toList(),"error",snapshot.error()));
            if (epoch != EgoXSession.context().epoch()) return;
            say(label + ": " + snapshot.state() + "; " + snapshot.checked() + "/" + snapshot.total() + " geprueft; " + snapshot.matches().size() + " Treffer.");
            snapshot.matches().stream().limit(12).mapToLong(decode::applyAsLong).forEach(value -> say("Kandidat: " + value));
            if (!snapshot.error().isEmpty()) say(snapshot.error());
            if (worldSeeds && snapshot.state() == SearchJob.State.COMPLETE && snapshot.matches().size() == 1)
                EgoXSession.acceptWorldSeed(decode.applyAsLong(snapshot.matches().getFirst()), label);
        });
    }
    private static long count(long min, long max) {
        if (max < min) throw new IllegalArgumentException("max muss >= min sein");
        return Math.addExact(Math.subtractExact(max, min), 1);
    }
    private static void status() {
        StatusDisplay.update(); StatusDisplay.lines().forEach(EgoXCommands::say);
        say("Seed: /egox cracker seed ON|OFF; Salt: /egox cracker salt ON|OFF");
        say(ResultDatabase.status());
    }
    private static void bits() {
        var bits = ObservationBits.summarize(EgoXSession.crackingStructures());
        say(String.format(Locale.ROOT, "~%.2f Platzierungsbits; ~%.2f Lifting-Bits aus %d Starts. Ziel: >=44 Lifting-Bits und >=5 geeignete Starts.", bits.placementBits(), bits.liftingBits(), bits.starts()));
        bits.families().forEach((type, value) -> say(String.format(Locale.ROOT, "%s: ~%.2f Bits", type, value)));
        say(DungeonEvidence.status());
        say("Dungeon-Muster-Bits sind bedingte Bodenmuster-Information und werden nicht auf das 44-Bit-Strukturziel addiert.");
        say("Theoretische Information, keine bereits geloesten Seed-Bits. Abhaengigkeiten und abweichende Salts koennen sie entwerten.");
        say(EndPillarFinder.status());
        say("Durch Hash bestaetigte Welt-Seed-Bits: " + (EgoXSession.knownSeed() == null ? "0/64" : "64/64"));
    }
    private static void data() {
        say("Strukturstarts: " + EgoXSession.structures().size() + "; Biom-Samples: " + EgoXSession.biomes().size() + "; Welt-Seed: " + EgoXSession.knownSeed());
        EgoXSession.structures().stream().limit(40).forEach(o -> say(o.placement().id() + " | Chunk " + o.chunkX() + " " + o.chunkZ() + " | " + o.dimension()));
        if (EgoXSession.structures().size() > 40) say("Weitere Starts mit /egox db export exportieren.");
    }
    private static void diagnose(long seed) {
        if (EgoXSession.context().hash() != null) say("Server-Hash: " + (SeedHash.of(seed) == EgoXSession.context().hash() ? "PASS" : "FAIL"));
        var groups = new TreeMap<String, List<StructureObservation>>();
        EgoXSession.structures().forEach(o -> groups.computeIfAbsent(o.placement().id(), k -> new ArrayList<>()).add(o));
        var random = new LegacyRandom();
        groups.forEach((id, observations) -> {
            long matches = observations.stream().filter(o -> o.matches(seed, EgoXSession.salt(o.placement()), random)).count();
            say(id + ": " + matches + "/" + observations.size() + " passend. " + (matches == observations.size() ? "" : "Moeglich: anderer Salt, Spacing, Generator, alte Chunks oder falscher Start."));
        });
        if (groups.isEmpty()) say("Noch keine Strukturstarts beobachtet.");
    }
    private static List<StructureObservation> observations(String type) {
        Placement profile = NativeProfiles.get(type);
        var samples = EgoXSession.crackingStructures().stream().filter(o -> o.placement().id().equals(profile.id())).toList();
        long regions = samples.stream().map(o -> Math.floorDiv(o.chunkX(), profile.spacing()) + ":" + Math.floorDiv(o.chunkZ(), profile.spacing())).distinct().count();
        if (regions < 3) throw new IllegalArgumentException("Mindestens 3 exakte Strukturstarts desselben Placement-Typs in verschiedenen Regionen noetig");
        return samples;
    }
    private static void crackSalt(CommandContext<FabricClientCommandSource> c) {
        var samples = observations(getString(c, "type"));
        long seed = getLong(c, "worldSeed"), min = getLong(c, "min"), max = getLong(c, "max");
        launch("Salt " + samples.getFirst().placement().id(), min, count(min, max), () -> {
            var random = new LegacyRandom();
            return salt -> { for (var o : samples) if (!o.matches(seed, salt, random)) return false; return true; };
        }, x -> x, false);
    }
    private static void crackStructures(CommandContext<FabricClientCommandSource> c) {
        var samples = EgoXSession.crackingStructures();
        if (samples.size() < 3) throw new IllegalArgumentException("Mindestens 3 Strukturstarts noetig");
        var saltSnapshot = new HashMap<Placement, Long>();
        samples.forEach(o -> saltSnapshot.put(o.placement(), EgoXSession.salt(o.placement())));
        long min = getLong(c, "min"), max = getLong(c, "max");
        launch("Struktur-Seed-Bereich", min, count(min, max), () -> {
            var random = new LegacyRandom();
            return seed -> { for (var o : samples) if (!o.matches(seed, saltSnapshot.get(o.placement()), random)) return false; return true; };
        }, x -> x, false);
    }
    private static List<BiomeObservation> requireBiomes() {
        var samples = EgoXSession.biomes();
        if (samples.size() < 8 || samples.stream().map(BiomeObservation::biome).distinct().count() < 2)
            throw new IllegalArgumentException("Mindestens 8 Biom-Samples mit 2 verschiedenen Biomen noetig. Weitere Chunks erkunden.");
        return samples;
    }
    private static void lift(long lower48) {
        Long hash = EgoXSession.context().hash();
        if (hash == null) throw new IllegalArgumentException("Kein Server-Seed-Hash vorhanden");
        launch("Welt-Seed aus 48-Bit-Kandidat", 0, 65536, () -> upper -> SeedHash.of((upper << 48) | lower48) == hash,
            upper -> (upper << 48) | lower48, true);
    }
    private static void testBiomes(long seed) {
        var samples = requireBiomes();
        String preset = EgoXConfig.get().biomePreset;
        launch("Native Biom-Pruefung", seed, 1, () -> value -> NativeBiomes.matches(value, samples, preset), x -> x, true);
    }
    private static void crackEvidence(CommandContext<FabricClientCommandSource> c, boolean ore, boolean paper) {
        if (ore && !EgoXConfig.get().autoOres) throw new IllegalStateException("Zuerst /egox cracker ore ON");
        if (!ore && !EgoXConfig.get().bedrockChecker) throw new IllegalStateException("Zuerst /egox cracker bedrock ON");
        long min = getLong(c, "min"), max = getLong(c, "max"), size = count(min, max);
        if (size > (ore ? 10000 : 1000000)) throw new IllegalArgumentException("Bereich zu gross: Erz maximal 10.000, Bedrock maximal 1.000.000 Kandidaten pro Job");
        var ores = BlockEvidence.ores(); var bedrock = BlockEvidence.bedrock();
        if (ore && ores.stream().noneMatch(EmeraldReplay::supports)) throw new IllegalStateException("Serverseitig beobachtete Smaragderz-Entfernungen benoetigt");
        if (!ore && bedrock.isEmpty()) throw new IllegalStateException("Bedrock-Uebergangsschichten laden");
        Long hash = paper ? null : EgoXSession.context().hash();
        long epoch = EgoXSession.context().epoch();
        say(ore ? "Konservatives Smaragderz-Replay: Treffer sind moegliche Population-Eingaenge, kein exakter Generierungsbeweis." : "Vanilla-Bedrock-Modell: individuelle Uebergangsschichten.");
        launch(paper ? "Erz: Feature-Population" : ore ? "Seed: Smaragderz" : "Seed: Bedrock", min, size, () -> {
            var predicate = ore ? NativeEmerald.predicate(ores, () -> epoch != EgoXSession.context().epoch()) : NativeBedrock.predicate(bedrock);
            return seed -> (hash == null || SeedHash.of(seed) == hash) && predicate.test(seed);
        }, value -> value, !paper);
    }
    private static void crackDungeons(CommandContext<FabricClientCommandSource> c) {
        if (!EgoXConfig.get().dungeonChecker) throw new IllegalStateException("Zuerst /egox cracker dungeon ON");
        var rooms = DungeonEvidence.snapshot(); if (rooms.isEmpty()) throw new IllegalStateException("Zuerst intakte Overworld-Dungeons laden");
        long min = getLong(c, "min"), size = count(min, getLong(c, "max"));
        if (size > 1_000_000) throw new IllegalArgumentException("Maximal 1.000.000 Kandidaten pro Job");
        long epoch = EgoXSession.context().epoch(); Long hash = EgoXSession.context().hash();
        launch("Seed: Dungeon-Bodenmuster", min, size, () -> {
            var check = NativeDungeon.predicate(rooms, () -> epoch != EgoXSession.context().epoch());
            return seed -> (hash == null || SeedHash.of(seed) == hash) && check.test(seed);
        }, value -> value, true);
    }
    private static void crackGeodes(CommandContext<FabricClientCommandSource> c) {
        if(!EgoXConfig.get().geodeChecker)throw new IllegalStateException("Zuerst /egox cracker geode ON");
        long min=getLong(c,"min"),max=getLong(c,"max"),size=count(min,max);if(size>1000000)throw new IllegalArgumentException("Maximal 1.000.000 Kandidaten pro Job");
        var observations=BlockEvidence.features();if(observations.stream().noneMatch(NativeGeode::supports))throw new IllegalStateException("Zuerst Amethystdrusen laden");
        Long hash=EgoXSession.context().hash();
        launch("Seed: Geoden-Platzierung",min,size,()->{var check=NativeGeode.predicate(observations);return seed->(hash==null||SeedHash.of(seed)==hash)&&check.test(seed);},value->value,true);
    }
    private static void biomeSearch(CommandContext<FabricClientCommandSource> c) {
        var samples = requireBiomes();
        long min = getLong(c, "min"), max = getLong(c, "max"), size = count(min, max);
        if (size > 1_000_000) throw new IllegalArgumentException("Native Biom-Suche maximal 1.000.000 Seeds pro Job; 2^64 Vollsuche ist nicht praktikabel");
        String preset = EgoXConfig.get().biomePreset;
        Long hash = EgoXSession.context().hash();
        launch("Welt-Seed-Bereich (strukturunabhaengig)", min, size, () -> seed -> {
            // A transmitted seed hash is cheaper and independent of server salts.
            if (hash != null && SeedHash.of(seed) != hash) return false;
            return NativeBiomes.matches(seed, samples, preset);
        }, x -> x, true);
    }
    private static void crackFeature(CommandContext<FabricClientCommandSource> c) throws Exception {
        Path directory = EgoXConfig.DIRECTORY.resolve("traces").toAbsolutePath().normalize();
        Path file = directory.resolve(getString(c, "file")).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file) || Files.size(file) > 1_000_000)
            throw new IllegalArgumentException("Trace-Datei muss in config/egoxseedcracker/traces liegen (max. 1 MB)");
        var trace = EgoXConfig.JSON.fromJson(Files.readString(file), FeatureTrace.class);
        trace.validate();
        long min = getLong(c, "min"), max = getLong(c, "max");
        launch("Feature " + trace.feature() + " " + trace.mode(), min, count(min, max), trace::predicate, x -> x, false);
    }
    private static void export() throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("context", EgoXSession.context()); payload.put("structures", EgoXSession.structures());
        payload.put("biomes", EgoXSession.biomes()); payload.put("salts", EgoXSession.salts());
        payload.put("finderDiagnostics", NativeTemplateFinder.failures()); payload.put("pillarSeeds", EndPillarFinder.candidates());
        payload.put("biomeAnalysis", AutomaticBiomes.report());
        payload.put("libraryIdentity", SessionLibrary.identity()); payload.put("oreObservations", BlockEvidence.ores()); payload.put("bedrockObservations", BlockEvidence.bedrock());
        payload.put("dungeonObservations", DungeonEvidence.snapshot());
        payload.put("featureAnalysis", FeatureEvidence.report());
        payload.put("featureObservations", BlockEvidence.features());
        payload.put("visualObservations", FinderMarkers.observations());
        if (EgoXSession.job != null) payload.put("lastSearch", EgoXSession.job.snapshot());
        Path file = EgoXConfig.DIRECTORY.resolve("observations-" + System.currentTimeMillis() + ".json");
        Files.writeString(file, EgoXConfig.JSON.toJson(payload)); say("Beobachtungen exportiert: " + file.getFileName());
    }
}
