package de.egox.seedcracker;

import de.egox.seedcracker.core.*;
import java.util.*;

public record LibrarySnapshot(int schema, Identity identity, String savedAt, List<StructureObservation> structures,
    List<BiomeObservation> biomes, Map<String, Long> salts, String worldSeed, List<Marker> markers,
    List<BlockObservation> ores, List<BlockObservation> bedrock, int[] pillarHeights, AutomaticBiomes.Report biomeAnalysis, long minedOres,
    List<BlockObservation> features, FeatureEvidence.Report featureAnalysis, List<DungeonObservation> dungeons) {
    public LibrarySnapshot(int schema, Identity identity, String savedAt, List<StructureObservation> structures,
        List<BiomeObservation> biomes, Map<String, Long> salts, String worldSeed, List<Marker> markers,
        List<BlockObservation> ores, List<BlockObservation> bedrock, int[] pillarHeights, AutomaticBiomes.Report biomeAnalysis, long minedOres,
        List<BlockObservation> features, FeatureEvidence.Report featureAnalysis) {
        this(schema, identity, savedAt, structures, biomes, salts, worldSeed, markers, ores, bedrock, pillarHeights, biomeAnalysis, minedOres, features, featureAnalysis, List.of());
    }
    public record Identity(String player, String server, String seedHash, String minecraft) { }
    public record Marker(String type, int chunkX, int chunkZ, String dimension, double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ, String source, Integer blockX, Integer blockY, Integer blockZ) { }
    public static boolean singleplayer(String server) { return server.equals("singleplayer") || server.startsWith("singleplayer:"); }
    public static String address(String server) {
        if(singleplayer(server))return server;
        String value=server.substring(server.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
        if(!value.contains(":"))value+=":25565";
        return value;
    }
}
