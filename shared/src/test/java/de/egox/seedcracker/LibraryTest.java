package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LibraryTest {
    @TempDir Path root;
    private final LibrarySnapshot.Identity identity = new LibrarySnapshot.Identity("player-a", "example.org:25565", "-9223372036854775808", "26.3");
    private BlockObservation ore(int x, String source) { return new BlockObservation("minecraft:overworld", "minecraft:deepslate_emerald_ore", x, -12, -1, source, "2026-10-24T00:00:00Z"); }
    private LibrarySnapshot snapshot(long count) {
        var p = new Placement("minecraft:shipwrecks", 24, 4, 1, Placement.Spread.LINEAR);
        return new LibrarySnapshot(1, identity, "2026-10-24T00:00:00Z", List.of(new StructureObservation(p,-24,48,"minecraft:overworld")),
            List.of(new BiomeObservation(-1,16,-1,"minecraft:plains")), Map.of(p.id(), -12345L), Long.toString(Long.MIN_VALUE), List.of(),
            count == 0 ? List.of() : List.of(ore(-1,"CLIENT_BREAK_PREDICTED")), List.of(), new int[]{76,79,82,85,88,91,94,97,100,103}, null, count, List.of(), null);
    }
    @Test void roundTripPreservesExactSeedsCoordinatesSaltsAndPillars() throws Exception {
        var store = new ObservationStore(root); store.save(snapshot(1)); var loaded = store.load(identity).orElseThrow();
        assertEquals(Long.toString(Long.MIN_VALUE), loaded.worldSeed()); assertEquals(-12345L,loaded.salts().get("minecraft:shipwrecks"));
        assertEquals(-1,loaded.ores().getFirst().chunkX()); assertEquals(-1,loaded.ores().getFirst().chunkZ());
        assertEquals(snapshot(1).structures(),loaded.structures()); assertArrayEquals(snapshot(1).pillarHeights(),loaded.pillarHeights());
    }
    @Test void identitiesSeparatePlayersServersHashesAndMinecraftVersions() throws Exception {
        var store = new ObservationStore(root); store.save(snapshot(0));
        assertEquals("example.org:25565",LibrarySnapshot.address("EXAMPLE.ORG"));assertEquals("45.93.248.170:25622",LibrarySnapshot.address("45.93.248.170/45.93.248.170:25622"));
        for (var other : List.of(new LibrarySnapshot.Identity("player-b",identity.server(),identity.seedHash(),identity.minecraft()),
            new LibrarySnapshot.Identity(identity.player(),"another.org",identity.seedHash(),identity.minecraft()),
            new LibrarySnapshot.Identity(identity.player(),identity.server(),"1",identity.minecraft()),
            new LibrarySnapshot.Identity(identity.player(),identity.server(),identity.seedHash(),"1.21.11"))) {
            assertNotEquals(ObservationStore.key(identity),ObservationStore.key(other)); assertTrue(store.load(other).isEmpty());
        }
        assertEquals(1,store.list(identity.player()).size()); assertTrue(store.list("another-player").isEmpty());
    }
    @Test void journalAfterCheckpointIsReplayedWithoutCountingOldOreTwice() throws Exception {
        var store = new ObservationStore(root); store.append(identity,"ORE_MINED",ore(-1,"CLIENT_BREAK_PREDICTED")); store.save(snapshot(1));
        store.append(identity,"ORE_MINED",ore(-17,"CLIENT_BREAK_PREDICTED")); store.append(identity,"ORE_SERVER_RESULT",ore(-17,"SERVER_BLOCK_CHANGE"));
        var loaded = store.load(identity).orElseThrow(); assertEquals(2,loaded.minedOres()); assertEquals(2,loaded.ores().size());
        var raw=com.google.gson.JsonParser.parseString(Files.readAllLines(store.directory(identity).resolve("observations.jsonl")).get(0)).getAsJsonObject().getAsJsonObject("data");
        assertEquals(-1,raw.get("chunkX").getAsInt());assertEquals(-1,raw.get("chunkZ").getAsInt());
        assertEquals("SERVER_BLOCK_CHANGE",loaded.ores().getLast().source());
        store.save(loaded); assertEquals(2,store.load(identity).orElseThrow().minedOres());
    }
    @Test void rawJournalRestoresBeforeFirstCheckpointAndAfterTornLastLine() throws Exception {
        var store = new ObservationStore(root); store.append(identity,"ORE_MINED",ore(-1,"CLIENT_BREAK_PREDICTED"));
        Path log=store.directory(identity).resolve("observations.jsonl"); Files.writeString(log,"{\"kind\":",StandardOpenOption.APPEND);
        assertEquals(1,store.load(identity).orElseThrow().minedOres());
        store.append(identity,"ORE_MINED",ore(-17,"CLIENT_BREAK_PREDICTED")); assertEquals(2,store.load(identity).orElseThrow().minedOres());
    }
    @Test void corruptedNewestSnapshotFallsBackToPreviousAndReplaysNewEvents() throws Exception {
        var store = new ObservationStore(root); store.append(identity,"ORE_MINED",ore(-1,"CLIENT_BREAK_PREDICTED")); store.save(snapshot(1));
        store.append(identity,"ORE_MINED",ore(-17,"CLIENT_BREAK_PREDICTED")); store.save(store.load(identity).orElseThrow());
        Files.writeString(store.directory(identity).resolve("session.json"),"{broken");
        var loaded=store.load(identity).orElseThrow(); assertEquals(2,loaded.minedOres()); assertEquals(2,loaded.ores().size());
    }
    @Test void repeatedMiningOfSamePositionRetainsEveryRawEvent() throws Exception {
        var store = new ObservationStore(root); store.append(identity,"ORE_MINED",ore(-1,"CLIENT_BREAK_PREDICTED")); store.append(identity,"ORE_MINED",ore(-1,"CLIENT_BREAK_PREDICTED"));
        var loaded=store.load(identity).orElseThrow(); assertEquals(2,loaded.minedOres()); assertEquals(1,loaded.ores().size());
        assertEquals(2,Files.readAllLines(store.directory(identity).resolve("observations.jsonl")).size());
    }
    @Test void hostileServerNamesNeverBecomeFilesystemPaths() throws Exception {
        var store = new ObservationStore(root);
        var odd = new LibrarySnapshot.Identity("../player", "../../outside\\test", "1", "26.3"); store.append(odd,"ORE_MINED",ore(-1,"CLIENT_BREAK_PREDICTED"));
        assertEquals(root.toAbsolutePath().normalize(),store.directory(odd).getParent()); assertTrue(store.directory(odd).getFileName().toString().matches("[a-f0-9]{64}"));
    }
    @Test void singleplayerWorldNamesCannotEnterThePublicOutbox() {
        assertTrue(LibrarySnapshot.singleplayer("singleplayer")); assertTrue(LibrarySnapshot.singleplayer("singleplayer:My World")); assertFalse(LibrarySnapshot.singleplayer("example.org:25565"));
    }
    @Test void checkerDefaultsMigrateOnceAndPreserveSubsequentChoices() {
        var json=com.google.gson.JsonParser.parseString("{\"displayRevision\":2,\"autoBiomes\":true,\"hud\":true,\"outlineRange\":128}").getAsJsonObject();
        assertTrue(ConfigMigration.update(json)); assertFalse(json.get("autoBiomes").getAsBoolean()); assertFalse(json.get("autoOres").getAsBoolean());
        assertTrue(json.get("hud").getAsBoolean()); assertEquals(128,json.get("outlineRange").getAsInt());
        json.addProperty("autoBiomes",true);json.addProperty("autoOres",true);assertFalse(ConfigMigration.update(json)); assertTrue(json.get("autoBiomes").getAsBoolean());assertTrue(json.get("autoOres").getAsBoolean());
    }
    @Test void markersInDifferentGeodeHeightCellsSurviveJournalRecovery() throws Exception {
        var store=new ObservationStore(root);
        for(int y:new int[]{0,64})store.append(identity,"MARKER",new LibrarySnapshot.Marker("egox:geode",0,0,"minecraft:overworld",1,y+1,1,5,y+5,5,"Geode",null,null,null));
        var loaded=store.load(identity).orElseThrow();assertEquals(2,loaded.markers().size());
    }
    @Test void dimensionChangesKeepRestoreValidButDisconnectInvalidatesPendingLoads() throws Exception {
        var id=SessionLibrary.class.getDeclaredField("identity");var loading=SessionLibrary.class.getDeclaredField("loading");id.setAccessible(true);loading.setAccessible(true);
        var generation=SessionLibrary.class.getDeclaredField("generation");generation.setAccessible(true);
        var context=EgoXSession.class.getDeclaredField("context");context.setAccessible(true);var previous=EgoXSession.context();
        try {
            id.set(null,identity);loading.setBoolean(null,true);long token=generation.getLong(null);
            context.set(null,new EgoXSession.Context(100,identity.server(),"minecraft:overworld",1L));assertTrue(SessionLibrary.isCurrentRestore(identity,token));
            context.set(null,new EgoXSession.Context(101,identity.server(),"minecraft:the_nether",1L));assertTrue(SessionLibrary.isCurrentRestore(identity,token));
            SessionLibrary.close();assertNull(SessionLibrary.identity());assertFalse(SessionLibrary.loading());
            id.set(null,identity);assertFalse(SessionLibrary.isCurrentRestore(identity,token));
        } finally {id.set(null,null);loading.setBoolean(null,false);context.set(null,previous);}
    }
    @Test void featureBlocksAndFinishedAnalysisReplayForLaterChecks() throws Exception {
        var store=new ObservationStore(root);var block=new BlockObservation("minecraft:overworld","minecraft:budding_amethyst",4,-30,4,"OBSERVED_FEATURE_BLOCK","");
        store.append(identity,"FEATURE_BLOCK",block);
        var report=new FeatureEvidence.Report("COMPLETE",65536,65536,List.of("-9223372036854775808 | HASH_WIDERSPRUCH"),"unconfirmed");store.append(identity,"FEATURE_ANALYSIS",report);
        var loaded=store.load(identity).orElseThrow();assertEquals(List.of(block),loaded.features());assertEquals(report,loaded.featureAnalysis());
    }
}
