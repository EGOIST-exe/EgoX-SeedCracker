package de.egox.seedcracker.core;

public record Placement(String id, int spacing, int separation, int vanillaSalt, Spread spread) {
    public enum Spread { LINEAR, TRIANGULAR }
    public Placement {
        if (id == null || id.isBlank() || spread == null || spacing <= 0 || separation < 0 || separation >= spacing)
            throw new IllegalArgumentException("Invalid random-spread placement");
    }

    public boolean matches(long seed, long salt, int chunkX, int chunkZ, LegacyRandom random) {
        int regionX = Math.floorDiv(chunkX, spacing);
        int regionZ = Math.floorDiv(chunkZ, spacing);
        random.setSeed(seed + salt + regionX * 341873128712L + regionZ * 132897987541L);
        int bound = spacing - separation;
        int x = offset(random, bound);
        if (x != Math.floorMod(chunkX, spacing)) return false;
        return offset(random, bound) == Math.floorMod(chunkZ, spacing);
    }

    public int offset(LegacyRandom random, int bound) {
        return spread == Spread.LINEAR ? random.nextInt(bound) : (random.nextInt(bound) + random.nextInt(bound)) / 2;
    }
}
