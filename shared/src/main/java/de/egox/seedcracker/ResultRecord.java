package de.egox.seedcracker;

import java.util.Map;

/** Seeds and hashes are decimal strings: JSON/Sheets numbers cannot preserve all 64 bits. */
public record ResultRecord(String id, String createdAt, String server, String dimension,
    String minecraftVersion, String worldSeed, String structureSeed, String seedHash,
    String verification, int biomeSamples, int structureSamples, Map<String, String> salts,
    String source) { }
