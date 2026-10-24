package de.egox.seedcracker.core;

public record StructureObservation(Placement placement, int chunkX, int chunkZ, String dimension) {
    public boolean matches(long seed, long salt, LegacyRandom random) {
        return placement.matches(seed, salt, chunkX, chunkZ, random);
    }
}
